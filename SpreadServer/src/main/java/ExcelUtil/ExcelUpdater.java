package ExcelUtil;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class ExcelUpdater {
    private static final String EXCEL_DIR = "data/";
    private static final String PDF_DIR = "data/pdfToAdd/";
    private static final Object FILE_LOCK = new Object();

    public static void main(String[] args) throws IOException {
        File pdfDir = new File(PDF_DIR);
        File[] newPdfs = pdfDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        if (newPdfs != null) {
            for (File pdf : newPdfs) {
                processSinglePdf(pdf);
            }
        }
    }

    public static void processSinglePdf(File pdfFile) throws IOException {
        synchronized (FILE_LOCK) {
            try (PDDocument document = PDDocument.load(pdfFile)) {
                String text = new PDFTextStripper().getText(document);
                String dateStr = extractDate(text);
                if (dateStr == null) throw new IOException("No date found in PDF");

                for (String grade : PDFToExcel.PRODUCT_GRADES) {
                    String filePrefix = grade.substring(0, 1); // Take the first letter
                    File excelFile = new File(EXCEL_DIR + filePrefix + ".xlsx");
                    System.out.println(excelFile);
                    XSSFWorkbook workbook;
                    XSSFSheet sheet;

                    if (excelFile.exists()) {
                        try (FileInputStream fis = new FileInputStream(excelFile)) {
                            workbook = new XSSFWorkbook(fis);
                        }
                    } else {
                        workbook = new XSSFWorkbook();
                        sheet = workbook.createSheet("Sheet1");
                    }

                    sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : workbook.createSheet("Sheet1");

                    String[] values = extractProductData(text, grade);
                    if (values == null || values.length < 6) continue;

                    LocalDate newDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                    System.out.println("adding: " + newDate);

                    boolean dateExists = false;
                    for (Row row : sheet) {
                        Cell cell = row.getCell(0);
                        if (cell != null && cell.getCellType() == CellType.STRING) {
                            try {
                                LocalDate existingDate = LocalDate.parse(cell.getStringCellValue(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                                if (existingDate.equals(newDate)) {
                                    dateExists = true;
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    if (dateExists) {
                        System.out.println("Skipping duplicate date: " + newDate + " from file: " + pdfFile.getName());
                        return;
                    }

                    LocalDate lastDate = getLastDate(sheet);
                    String[] lastThursdayData = getLastThursdayData(sheet, lastDate);

                    for (LocalDate date = lastDate.plusDays(1); date.isBefore(newDate); date = date.plusDays(1)) {
                        DayOfWeek dow = date.getDayOfWeek();
                        if (dow == DayOfWeek.THURSDAY) {
                            lastThursdayData = getRowData(sheet, date);
                        } else if ((dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY) && lastThursdayData != null) {
                            insertRow(sheet, date, lastThursdayData, true);
                        }
                    }

                    insertRow(sheet, newDate, values, false);
                    System.out.println("added new date");

                    try (FileOutputStream fos = new FileOutputStream(excelFile)) {
                        workbook.setForceFormulaRecalculation(true);
                        workbook.write(fos);
                    }
                    workbook.close();
                    System.out.println("closed workbook");
                }
            }
        }
    }

    private static LocalDate getLastDate(XSSFSheet sheet) {
        LocalDate latest = LocalDate.MIN;
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(0);
                if (cell != null && cell.getCellType() == CellType.STRING) {
                    try {
                        LocalDate date = LocalDate.parse(cell.getStringCellValue(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                        if (date.isAfter(latest)) latest = date;
                    } catch (Exception ignored) {}
                }
            }
        }
        return latest.equals(LocalDate.MIN) ? LocalDate.now().minusDays(7) : latest;
    }

    private static void insertRow(XSSFSheet sheet, LocalDate date, String[] values, boolean isSynthetic) {
        int rowNum = sheet.getLastRowNum() + 1;
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));

        CellStyle style = sheet.getWorkbook().createCellStyle();
        if (isSynthetic) {
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i + 1);
            try {
                cell.setCellValue(Double.parseDouble(values[i].replace(",", "")));
            } catch (NumberFormatException e) {
                cell.setCellValue(values[i]);
            }
            if (isSynthetic) cell.setCellStyle(style);
        }
    }

    private static String[] getLastThursdayData(XSSFSheet sheet, LocalDate lastDate) {
        LocalDate date = lastDate;
        while (date.isAfter(LocalDate.MIN)) {
            if (date.getDayOfWeek() == DayOfWeek.THURSDAY) {
                return getRowData(sheet, date);
            }
            date = date.minusDays(1);
        }
        return null;
    }

    private static String[] getRowData(XSSFSheet sheet, LocalDate date) {
        String dateStr = date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        for (Row row : sheet) {
            if (row.getCell(0) != null && row.getCell(0).getStringCellValue().equals(dateStr)) {
                String[] data = new String[6];
                for (int i = 0; i < 6; i++) {
                    Cell cell = row.getCell(i + 1);
                    data[i] = cell != null ? cell.toString() : "";
                }
                return data;
            }
        }
        return null;
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
