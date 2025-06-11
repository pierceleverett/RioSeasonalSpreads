package Handlers;

import com.google.gson.Gson;
import org.apache.poi.ss.usermodel.*;
import spark.Request;
import spark.Response;
import spark.Route;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.LinkedHashMap;
import java.util.Map;

public class MagellanGraphHandler implements Route {

  private static final Map<String, String> FUEL_SHEET_MAP = Map.of(
      "A", "A PREMIUM UNLEADED GASOLINE",
      "E", "E DENATURED FUEL ETHANOL",
      "Q", "Q COMMERCIAL JET FUEL",
      "V", "V SUB OCTANE UNL GASOLINE",
      "X", "X #2 ULSD",
      "Y", "Y #1 ULSD FUEL OIL"
  );

  @Override
  public Object handle(Request request, Response response) {
    System.out.println("Received request to /getMagellanData");

    String fuelCode = request.queryParams("fuel");
    if (fuelCode == null || !FUEL_SHEET_MAP.containsKey(fuelCode)) {
      response.status(400);
      return "Invalid fuel type. Supported types: " + FUEL_SHEET_MAP.keySet();
    }

    String filePath = System.getenv().getOrDefault("DATA_FILE_PATH", "data/Fuel_Inventory_Report.xlsx");
    File fileCheck = new File(filePath);
    System.out.println("Checking file: " + fileCheck.getAbsolutePath());

    if (!fileCheck.exists() || !fileCheck.isFile() || fileCheck.length() == 0) {
      response.status(404);
      return "Excel file not found or is empty at: " + fileCheck.getAbsolutePath();
    }

    try (FileInputStream file = new FileInputStream(fileCheck);
        Workbook workbook = WorkbookFactory.create(file)) {

      String sheetName = FUEL_SHEET_MAP.get(fuelCode);
      Sheet sheet = workbook.getSheet(sheetName);
      if (sheet == null) {
        response.status(404);
        return "Sheet not found for fuel type: " + fuelCode;
      }

      int HEADER_ROW = 2;
      int FIRST_DATA_ROW = 3;
      int DATE_COL = 8;
      int FIRST_DATA_COL = 9;

      Row headerRow = sheet.getRow(HEADER_ROW);
      if (headerRow == null) {
        response.status(500);
        return "Header row not found in sheet";
      }

      Map<Integer, String> headers = new LinkedHashMap<>();
      for (int j = FIRST_DATA_COL; j <= headerRow.getLastCellNum(); j++) {
        Cell cell = headerRow.getCell(j);
        if (cell != null) {
          String header = getCellValueAsString(cell);
          if (header != null && !header.isEmpty()) {
            headers.put(j, header.trim());
          }
        }
      }

      Map<String, Map<String, Object>> result = new LinkedHashMap<>();
      for (int i = FIRST_DATA_ROW; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null) continue;

        String date = extractDate(row.getCell(DATE_COL));
        if (date == null || date.isEmpty()) continue;

        Map<String, Object> rowData = new LinkedHashMap<>();
        boolean hasData = false;

        for (Map.Entry<Integer, String> entry : headers.entrySet()) {
          int colIndex = entry.getKey();
          String header = entry.getValue();
          Cell dataCell = row.getCell(colIndex);
          Object value = getCellValue(dataCell);
          rowData.put(header, value);
          if (value != null) hasData = true;
        }

        if (hasData) {
          result.put(date, rowData);
        }
      }

      response.type("application/json");
      return new Gson().toJson(result);

    } catch (IOException e) {
      response.status(500);
      e.printStackTrace();
      return "Error reading Excel file: " + e.getMessage();
    } catch (Exception e) {
      response.status(500);
      e.printStackTrace();
      return "Internal server error: " + e.getMessage();
    }
  }

  private String extractDate(Cell dateCell) {
    if (dateCell == null) return null;
    switch (dateCell.getCellType()) {
      case STRING:
        return dateCell.getStringCellValue().trim();
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(dateCell)) {
          return new SimpleDateFormat("MM/dd").format(dateCell.getDateCellValue());
        } else {
          return String.valueOf(dateCell.getNumericCellValue());
        }
      case FORMULA:
        switch (dateCell.getCachedFormulaResultType()) {
          case STRING:
            return dateCell.getStringCellValue().trim();
          case NUMERIC:
            if (DateUtil.isCellDateFormatted(dateCell)) {
              return new SimpleDateFormat("MM/dd").format(dateCell.getDateCellValue());
            } else {
              return String.valueOf(dateCell.getNumericCellValue());
            }
        }
      default:
        return null;
    }
  }

  private Object getCellValue(Cell cell) {
    if (cell == null) return null;
    switch (cell.getCellType()) {
      case STRING:
        return cell.getStringCellValue().trim();
      case NUMERIC:
        return DateUtil.isCellDateFormatted(cell) ? cell.getDateCellValue() : cell.getNumericCellValue();
      case BOOLEAN:
        return cell.getBooleanCellValue();
      case FORMULA:
        switch (cell.getCachedFormulaResultType()) {
          case STRING:
            return cell.getStringCellValue().trim();
          case NUMERIC:
            return DateUtil.isCellDateFormatted(cell) ? cell.getDateCellValue() : cell.getNumericCellValue();
          case BOOLEAN:
            return cell.getBooleanCellValue();
        }
      default:
        return null;
    }
  }

  private String getCellValueAsString(Cell cell) {
    if (cell == null) return null;
    switch (cell.getCellType()) {
      case STRING:
        return cell.getStringCellValue();
      case NUMERIC:
        return DateUtil.isCellDateFormatted(cell)
            ? new SimpleDateFormat("MM/dd").format(cell.getDateCellValue())
            : String.valueOf(cell.getNumericCellValue());
      case FORMULA:
        switch (cell.getCachedFormulaResultType()) {
          case STRING:
            return cell.getStringCellValue();
          case NUMERIC:
            return DateUtil.isCellDateFormatted(cell)
                ? new SimpleDateFormat("MM/dd").format(cell.getDateCellValue())
                : String.valueOf(cell.getNumericCellValue());
        }
      default:
        return null;
    }
  }
}
