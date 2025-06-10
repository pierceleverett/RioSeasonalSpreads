package Handlers;

import com.google.gson.Gson;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;
import spark.Request;
import spark.Response;
import spark.Route;

import java.io.FileInputStream;
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
  public Object handle(Request request, Response response) throws Exception {
    String fuelCode = request.queryParams("fuel");

    if (fuelCode == null || !FUEL_SHEET_MAP.containsKey(fuelCode)) {
      response.status(400);
      return "Invalid fuel type. Supported types: " + FUEL_SHEET_MAP.keySet();
    }

    String filePath = "data/Fuel_Inventory_Report.xlsx";
    try (FileInputStream file = new FileInputStream(filePath);
        Workbook workbook = WorkbookFactory.create(file)) {

      // Get the sheet for the fuel type
      String sheetName = FUEL_SHEET_MAP.get(fuelCode);
      Sheet sheet = workbook.getSheet(sheetName);
      if (sheet == null) {
        response.status(404);
        return "Sheet not found for fuel type: " + fuelCode;
      }

      // Define key positions (0-based)
      int HEADER_ROW = 2;     // Row 3 in Excel (0-based index 2)
      int FIRST_DATA_ROW = 3;  // Row 4 in Excel (0-based index 3)
      int DATE_COL = 8;       // Column I (0-based index 8)
      int FIRST_DATA_COL = 9; // Column J (0-based index 9)

      // Extract headers (from Row 3, columns J to end)
      Row headerRow = sheet.getRow(HEADER_ROW);
      if (headerRow == null) {
        response.status(500);
        return "Header row not found in sheet";
      }

      // Read headers from columns J onward
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

      // Parse data rows (starting from row 4)
      Map<String, Map<String, Object>> result = new LinkedHashMap<>();
      for (int i = FIRST_DATA_ROW; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null) continue;

        // Extract date (from Column I)
        String date = extractDate(row.getCell(DATE_COL));
        if (date == null || date.isEmpty()) {
          continue; // Skip if no valid date
        }

        // Map data values to headers
        Map<String, Object> rowData = new LinkedHashMap<>();
        boolean hasData = false;

        for (Map.Entry<Integer, String> entry : headers.entrySet()) {
          int colIndex = entry.getKey();
          String header = entry.getValue();
          Cell dataCell = row.getCell(colIndex);

          Object value = getCellValue(dataCell);
          if (value != null) {
            rowData.put(header, value);
            hasData = true;
          } else {
            rowData.put(header, null);
          }
        }

        if (hasData) {
          result.put(date, rowData);
        }
      }

      // Set response type and return JSON
      response.type("application/json");
      return new Gson().toJson(result);
    }
  }

  private String extractDate(Cell dateCell) {
    if (dateCell == null) {
      return null;
    }

    switch (dateCell.getCellType()) {
      case STRING:
        return dateCell.getStringCellValue().trim();
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(dateCell)) {
          SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd");
          return dateFormat.format(dateCell.getDateCellValue());
        } else {
          return String.valueOf(dateCell.getNumericCellValue());
        }
      case FORMULA:
        switch (dateCell.getCachedFormulaResultType()) {
          case STRING:
            return dateCell.getStringCellValue().trim();
          case NUMERIC:
            if (DateUtil.isCellDateFormatted(dateCell)) {
              SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd");
              return dateFormat.format(dateCell.getDateCellValue());
            } else {
              return String.valueOf(dateCell.getNumericCellValue());
            }
          default:
            return null;
        }
      default:
        return null;
    }
  }

  private Object getCellValue(Cell cell) {
    if (cell == null) {
      return null;
    }

    switch (cell.getCellType()) {
      case STRING:
        return cell.getStringCellValue().trim();
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
          return cell.getDateCellValue();
        } else {
          return cell.getNumericCellValue();
        }
      case BOOLEAN:
        return cell.getBooleanCellValue();
      case FORMULA:
        switch (cell.getCachedFormulaResultType()) {
          case STRING:
            return cell.getStringCellValue().trim();
          case NUMERIC:
            if (DateUtil.isCellDateFormatted(cell)) {
              return cell.getDateCellValue();
            } else {
              return cell.getNumericCellValue();
            }
          case BOOLEAN:
            return cell.getBooleanCellValue();
          default:
            return null;
        }
      default:
        return null;
    }
  }

  private String getCellValueAsString(Cell cell) {
    if (cell == null) {
      return null;
    }

    switch (cell.getCellType()) {
      case STRING:
        return cell.getStringCellValue();
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
          SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd");
          return dateFormat.format(cell.getDateCellValue());
        } else {
          return String.valueOf(cell.getNumericCellValue());
        }
      case FORMULA:
        switch (cell.getCachedFormulaResultType()) {
          case STRING:
            return cell.getStringCellValue();
          case NUMERIC:
            if (DateUtil.isCellDateFormatted(cell)) {
              SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd");
              return dateFormat.format(cell.getDateCellValue());
            } else {
              return String.valueOf(cell.getNumericCellValue());
            }
          default:
            return null;
        }
      default:
        return null;
    }
  }
}