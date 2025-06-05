package ExcelUtil;

import java.sql.Time;
import java.time.LocalTime;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ExcelUpdater {
    private static final String EXCEL_PATH = "data/Fuel_Inventory_Report.xlsx";
    private static final String PDF_DIR = "data/pdfToAdd/";
    private static final Object FILE_LOCK = new Object();

    public static void main(String[] args) throws IOException {
        // 1. Load existing workbook
        File excelFile = new File(EXCEL_PATH);
        XSSFWorkbook workbook;

        try (FileInputStream fis = new FileInputStream(excelFile)) {
            workbook = new XSSFWorkbook(fis);
        }

        // 2. Get all existing dates
        Set<String> existingDates = getExistingDates(workbook);

        // 3. Process only new PDFs
        File pdfDir = new File(PDF_DIR);
        File[] newPdfs = pdfDir.listFiles((dir, name) -> {
            try {
                return name.toLowerCase().endsWith(".pdf") &&
                        isNewPdf(name, existingDates);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        });
        System.out.println(newPdfs.toString());
        if (newPdfs != null) {
            for (File pdf : newPdfs) {
                updateWorkbook(workbook, "", "");
            }
        }

        // 4. Save with changes
        try (FileOutputStream fos = new FileOutputStream(EXCEL_PATH)) {
            workbook.write(fos);
        }
    }

    private static Set<String> getExistingDates(XSSFWorkbook workbook) {
        Set<String> existingDates = new HashSet<>();

        for (String grade : PDFToExcel.PRODUCT_GRADES) {
            XSSFSheet sheet = workbook.getSheet(grade);
            if (sheet == null) continue;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                Cell dateCell = row.getCell(0);
                if (dateCell != null) {
                    existingDates.add(dateCell.getStringCellValue());
                }
            }
        }

        return existingDates;
    }

    private static boolean isNewPdf(String filename, Set<String> existingDates) throws IOException {
        try (PDDocument doc = PDDocument.load(new File(PDF_DIR + filename))) {
            String text = new PDFTextStripper().getText(doc);
            String date = extractDate(text);
            return date != null && !existingDates.contains(date);
        }
    }


    public static void processSinglePdf(File pdfFile) throws IOException {
        synchronized(FILE_LOCK) {
            System.out.println("Processing PDF: " + pdfFile.getName());

            // Load or create workbook
            XSSFWorkbook workbook;
            File excelFile = new File(EXCEL_PATH);

            if (excelFile.exists()) {
                System.out.println("Loading existing workbook");
                try (FileInputStream fis = new FileInputStream(excelFile)) {
                    workbook = new XSSFWorkbook(fis);
                }
            } else {
                System.out.println("Creating new workbook");
                workbook = new XSSFWorkbook();
                // Initialize sheets if needed
                for (String grade : PDFToExcel.PRODUCT_GRADES) {
                    workbook.createSheet(grade);
                }
            }

            // Process PDF content
            try (PDDocument document = PDDocument.load(pdfFile)) {
                String text = new PDFTextStripper().getText(document);
                String dateStr = extractDate(text);

                if (dateStr == null) {
                    throw new IOException("No date found in PDF");
                }

                updateWorkbook(workbook, text, dateStr);
            }

            // Save changes
            System.out.println("Saving workbook to: " + excelFile.getAbsolutePath());
            try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                workbook.write(fos);
            }
            workbook.close();
        }
    }

    private static void updateWorkbook(XSSFWorkbook workbook, String pdfText, String dateStr) throws IOException {
        LocalDate newDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        boolean insertedAny = false;

        for (String grade : PDFToExcel.PRODUCT_GRADES) {
            XSSFSheet sheet = workbook.getSheet(grade);
            if (sheet == null) {
                System.out.println("Creating new sheet for grade: " + grade);
                sheet = workbook.createSheet(grade);
            }

            String[] values = extractProductData(pdfText, grade);
            if (values == null || values.length < 6) {
                System.err.println("No data found for grade: " + grade);
                continue;
            }

            int insertRowNum = findInsertionPoint(sheet, newDate);
            System.out.println("Inserting data at row: " + insertRowNum);

            if (insertRowNum <= sheet.getLastRowNum()) {
                sheet.shiftRows(insertRowNum, sheet.getLastRowNum(), 1);
            }

            Row newRow = sheet.createRow(insertRowNum);
            newRow.createCell(0).setCellValue(dateStr);

            for (int i = 0; i < values.length; i++) {
                Cell cell = newRow.createCell(i + 1);
                cell.setCellValue(Double.parseDouble(values[i].replace(",", "")));
            }

            insertedAny = true;
            System.out.println("Updated " + grade + " for date " + dateStr);
        }

        if (!insertedAny) {
            throw new IOException("No valid data found in PDF for any product grade");
        }
    }



    private static int findInsertionPoint(XSSFSheet sheet, LocalDate newDate) {
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Cell dateCell = row.getCell(0);
            if (dateCell == null) continue;

            LocalDate rowDate = LocalDate.parse(
                    dateCell.getStringCellValue(),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy")
            );

            if (newDate.isBefore(rowDate)) {
                return i; // Found where to insert
            }
        }
        int result = sheet.getLastRowNum() + 1;
        System.out.println(result);
        return sheet.getLastRowNum() + 1; // Append at end if newest
    }

    // Optional: Full sheet sort (call this after all updates)
    private static void sortSheetByDate(XSSFSheet sheet) {
        List<Row> rows = new ArrayList<>();

        // Skip header
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            rows.add(sheet.getRow(i));
        }

        // Sort by date
        rows.sort(Comparator.comparing(row ->
                LocalDate.parse(
                        row.getCell(0).getStringCellValue(),
                        DateTimeFormatter.ofPattern("MM/dd/yyyy")
                )
        ));

        // Rewrite sheet (preserving formatting)
        for (int i = 0; i < rows.size(); i++) {
            Row oldRow = rows.get(i);
            Row newRow = sheet.createRow(i + 1);

            // Copy all cells
            for (int j = 0; j < oldRow.getLastCellNum(); j++) {
                Cell oldCell = oldRow.getCell(j);
                if (oldCell != null) {
                    Cell newCell = newRow.createCell(j);
                    copyCell(oldCell, newCell);
                }
            }
        }
    }

    private static void copyCell(Cell source, Cell target) {
        target.setCellStyle(source.getCellStyle());
        switch (source.getCellType()) {
            case STRING -> target.setCellValue(source.getStringCellValue());
            case NUMERIC -> target.setCellValue(source.getNumericCellValue());
            case BOOLEAN -> target.setCellValue(source.getBooleanCellValue());
            case FORMULA -> target.setCellFormula(source.getCellFormula());
        }
    }

    private static Row findOrCreateRow(XSSFSheet sheet, String date) {
        // Check if row exists
        for (Row row : sheet) {
            if (row.getCell(0).getStringCellValue().equals(date)) {
                return row;
            }
        }

        // Create new row at end
        Row newRow = sheet.createRow(sheet.getLastRowNum() + 1);
        newRow.createCell(0).setCellValue(date);
        return newRow;
    }

    private static String extractDate(String text) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("AS OF:\\s*(\\d{2}/\\d{2}/\\d{4})");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String[] extractProductData(String text, String productGrade) {
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            if (line.startsWith(productGrade)) {
                String[] parts = line.substring(productGrade.length()).trim().split("\\s+");
                if (parts.length >= 6) {
                    String[] values = new String[6];
                    for (int i = 0; i < 6; i++) {
                        values[i] = parts[i].replace(",", "");
                    }
                    return values;
                }
                break;
            }
        }
        return null;
    }
}