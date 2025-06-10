package ExcelUtil;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class PDFToExcel {

    public static final String[] PRODUCT_GRADES = {
            "A PREMIUM UNLEADED GASOLINE",
            "E DENATURED FUEL ETHANOL",
            "Q COMMERCIAL JET FUEL",
            "V SUB OCTANE UNL GASOLINE",
            "X #2 ULSD",
            "Y #1 ULSD FUEL OIL"
    };

    private static final String[] CSV_HEADERS = {
            "Date",
            "System Inventory",
            "MPL Racks Only",
            "Offlines and MPL Racks 7-Day Average",
            "Offlines and MPL Racks 28-Day Average",
            "Receipts 7-Day Average",
            "Receipts 28-Day Average"
    };

    public static void main(String[] args) throws IOException {
        String pdfDirectory = "src/data/pdf/";
        String outputExcelFile = "src/data/Fuel_Inventory_Report.xlsx";

        // Map to hold all data: {ProductGrade → {Date → Values}}
        Map<String, Map<String, String[]>> productData = new LinkedHashMap<>();
        for (String grade : PRODUCT_GRADES) {
            productData.put(grade, new LinkedHashMap<>());
        }

        File dir = new File(pdfDirectory);
        File[] pdfFiles = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));

        if (pdfFiles != null) {
            for (File pdfFile : pdfFiles) {
                try {
                    System.out.println("Processing: " + pdfFile.getName());
                    processPDF(pdfFile, productData);
                } catch (IOException e) {
                    System.err.println("Error processing: " + pdfFile.getName());
                    e.printStackTrace();
                }
            }

            // Write all data to a single Excel file with multiple sheets
            writeToExcel(outputExcelFile, productData);
            System.out.println("Excel file generated: " + outputExcelFile);
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Runtime.getRuntime().exec("cmd /c start \"\" \"" + outputExcelFile + "\"");
            }
        } else {
            System.out.println("No PDFs found in: " + pdfDirectory);
        }
    }

    private static void processPDF(File pdfFile, Map<String, Map<String, String[]>> productData) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            String date = extractDate(text);

            if (date == null) {
                System.err.println("Date not found in: " + pdfFile.getName());
                return;
            }

            for (String grade : PRODUCT_GRADES) {
                String[] values = extractProductData(text, grade);
                if (values != null) {
                    productData.get(grade).put(date, values);
                }
            }
        }
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

    private static void writeToExcel(String outputFile, Map<String, Map<String, String[]>> productData) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.setDataFormat(workbook.createDataFormat().getFormat("0"));
            CellStyle syntheticStyle = workbook.createCellStyle();
            syntheticStyle.cloneStyleFrom(numberStyle);
            syntheticStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            syntheticStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (String grade : PRODUCT_GRADES) {
                Sheet sheet = workbook.createSheet(grade.substring(0, Math.min(grade.length(), 31)));

                // Create header row
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < CSV_HEADERS.length; i++) {
                    headerRow.createCell(i).setCellValue(CSV_HEADERS[i]);
                }

                // Sort all existing dates (newest first)
                List<LocalDate> existingDates = productData.get(grade).keySet().stream()
                        .map(dateStr -> LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yyyy")))
                        .sorted(Comparator.reverseOrder())
                        .collect(Collectors.toList());

                // Generate complete date range (min date to max date), excluding leap days
                LocalDate startDate = existingDates.get(existingDates.size() - 1);
                LocalDate endDate = existingDates.get(0);
                List<LocalDate> allDates = new ArrayList<>();
                for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                    // Skip February 29th
                    if (!(date.getMonth() == Month.FEBRUARY && date.getDayOfMonth() == 29)) {
                        allDates.add(date);
                    }
                }

                // Process each date in reverse order (newest first)
                int rowNum = 1;
                String[] lastThursdayData = null;
                for (LocalDate date : allDates) {
                    String dateKey = date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                    DayOfWeek dayOfWeek = date.getDayOfWeek();

                    // Always use Thursday's data for Friday/Saturday
                    boolean isSynthetic = false;
                    String[] dataToWrite;

                    if (dayOfWeek == DayOfWeek.FRIDAY || dayOfWeek == DayOfWeek.SATURDAY) {
                        if (lastThursdayData != null) {
                            dataToWrite = lastThursdayData;
                            isSynthetic = true;
                        } else {
                            continue; // Skip if no Thursday data available
                        }
                    } else {
                        dataToWrite = productData.get(grade).get(dateKey);
                        if (dayOfWeek == DayOfWeek.THURSDAY) {
                            lastThursdayData = dataToWrite; // Update Thursday reference
                        }
                    }

                    // Write row (only if we have data)
                    if (dataToWrite != null) {
                        Row row = sheet.createRow(rowNum++);
                        row.createCell(0).setCellValue(dateKey);
                        for (int i = 0; i < dataToWrite.length; i++) {
                            Cell cell = row.createCell(i + 1);
                            String value = dataToWrite[i];
                            try {
                                cell.setCellValue(Double.parseDouble(value.replace(",", "")));
                                cell.setCellStyle(isSynthetic ? syntheticStyle : numberStyle);
                            } catch (NumberFormatException e) {
                                cell.setCellValue(value);
                            }
                        }
                    }
                }

                // Auto-size columns
                for (int i = 0; i < CSV_HEADERS.length; i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            try (FileOutputStream fileOut = new FileOutputStream(outputFile)) {
                workbook.write(fileOut);
            }
        } catch (IOException e) {
            System.err.println("Error writing Excel file: " + e.getMessage());
        }
    }

    private static LocalDate parseDate(String dateStr) {
        return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
    }


}



