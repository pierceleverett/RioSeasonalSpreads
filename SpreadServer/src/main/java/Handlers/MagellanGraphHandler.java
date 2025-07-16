package Handlers;

import com.google.gson.Gson;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import spark.Request;
import spark.Response;
import spark.Route;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class MagellanGraphHandler implements Route {
  int currYearint = LocalDate.now().getYear();
  String currYear = Integer.toString(currYearint);
  String year1 = Integer.toString(currYearint - 10);
  String year2 = Integer.toString(currYearint - 9);
  String year3 = Integer.toString(currYearint - 8);
  String year4 = Integer.toString(currYearint - 7);
  String year5 = Integer.toString(currYearint - 6);
  String year6 = Integer.toString(currYearint - 5);
  String year7 = Integer.toString(currYearint - 4);
  String year8 = Integer.toString(currYearint - 3);
  String year9 = Integer.toString(currYearint - 2);
  String year10 = Integer.toString(currYearint - 1);
  List<String> YEARS = Arrays.asList(currYear, year1, year2, year3, year4, year5, year6, year7, year8, year9, year10);


  @Override
  public Object handle(Request request, Response response) {
    String fuel = request.queryParams("fuel");
    String dataHeader = request.queryParams("data");
    int currYearint = LocalDate.now().getYear();
    String currYear = Integer.toString(currYearint);
    String year1 = Integer.toString(currYearint - 10);
    String year2 = Integer.toString(currYearint - 9);
    String year3 = Integer.toString(currYearint - 8);
    String year4 = Integer.toString(currYearint - 7);
    String year5 = Integer.toString(currYearint - 6);
    String year6 = Integer.toString(currYearint - 5);
    String year7 = Integer.toString(currYearint - 4);
    String year8 = Integer.toString(currYearint - 3);
    String year9 = Integer.toString(currYearint - 2);
    String year10 = Integer.toString(currYearint - 1);


    if (fuel == null || dataHeader == null) {
      response.status(400);
      return "Missing required query parameters: 'fuel' and 'data'";
    }

    String filePath = "data/new" + fuel + ".xlsx";
    File file = new File(filePath);
    if (!file.exists()) {
      response.status(404);
      return "Excel file not found: " + filePath;
    }

    try (FileInputStream fis = new FileInputStream(file);
        Workbook workbook = new XSSFWorkbook(fis)) {

      Sheet sheet = workbook.getSheetAt(0);
      Row headerRow = sheet.getRow(0);
      if (headerRow == null) {
        response.status(500);
        return "Header row not found.";
      }

      int dataColIndex = -1;
      for (Cell cell : headerRow) {
        if (cell.getStringCellValue().trim().equalsIgnoreCase(dataHeader.trim())) {
          dataColIndex = cell.getColumnIndex();
          break;
        }
      }

      if (dataColIndex == -1) {
        response.status(400);
        return "Data column '" + dataHeader + "' not found.";
      }

      Map<String, Map<String, Double>> result = initializeDateMap();

      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        Row row = sheet.getRow(i);
        if (row == null) continue;

        String mmdd = extractDate(row.getCell(0));
        if (mmdd == null || mmdd.equals("02/29")) continue;

        Map<String, Double> dateMap = result.get(mmdd);
        if (dateMap == null) continue;

        Cell cell = row.getCell(dataColIndex);
        Double val = getNumericValue(cell);
        if (val != null) {
          String year = extractYear(row.getCell(0));
          if (YEARS.contains(year)) {
            dateMap.put(year, val);
          }
        }
      }

      for (Map<String, Double> values : result.values()) {
        values.put("5YEARAVG", average(values, Arrays.asList(year6, year7, year8, year9, year10)));
        values.put("10YEARAVG", average(values, YEARS.subList(0, 11)));
      }

      response.type("application/json");
      return new Gson().toJson(result);

    } catch (IOException e) {
      response.status(500);
      return "Error reading Excel file: " + e.getMessage();
    } catch (Exception e) {
      response.status(500);
      return "Internal server error: " + e.getMessage();
    }
  }

  private Map<String, Map<String, Double>> initializeDateMap() {
    Map<String, Map<String, Double>> map = new LinkedHashMap<>();
    Calendar cal = Calendar.getInstance();
    cal.set(2000, Calendar.JANUARY, 1);
    SimpleDateFormat sdf = new SimpleDateFormat("MM/dd");

    for (int i = 0; i < 366; i++) {
      String mmdd = sdf.format(cal.getTime());
      if (!mmdd.equals("02/29")) {
        Map<String, Double> yearMap = new HashMap<>();
        for (String year : YEARS) yearMap.put(year, null);
        yearMap.put("5YEARAVG", null);
        yearMap.put("10YEARAVG", null);
        map.put(mmdd, yearMap);
      }
      cal.add(Calendar.DAY_OF_YEAR, 1);
    }
    return map;
  }

  private String extractDate(Cell cell) {
    if (cell == null) return null;
    try {
      if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
        return new SimpleDateFormat("MM/dd").format(cell.getDateCellValue());
      } else if (cell.getCellType() == CellType.STRING) {
        String[] parts = cell.getStringCellValue().split("/");
        if (parts.length >= 2) {
          return String.format("%02d/%02d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
      }
    } catch (Exception ignored) {}
    return null;
  }

  private String extractYear(Cell cell) {
    if (cell == null) return null;
    try {
      if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
        return new SimpleDateFormat("yyyy").format(cell.getDateCellValue());
      } else if (cell.getCellType() == CellType.STRING) {
        String[] parts = cell.getStringCellValue().split("/");
        if (parts.length == 3) return parts[2];
      }
    } catch (Exception ignored) {}
    return null;
  }

  private Double getNumericValue(Cell cell) {
    if (cell == null) return null;
    try {
      if (cell.getCellType() == CellType.NUMERIC) {
        return cell.getNumericCellValue();
      } else if (cell.getCellType() == CellType.FORMULA) {
        if (cell.getCachedFormulaResultType() == CellType.NUMERIC) {
          return cell.getNumericCellValue();
        }
      }
    } catch (Exception ignored) {}
    return null;
  }

  private Double average(Map<String, Double> values, List<String> keys) {
    double sum = 0;
    int count = 0;
    for (String key : keys) {
      Double val = values.get(key);
      if (val != null) {
        sum += val;
        count++;
      }
    }
    return count > 0 ? sum / count : null;
  }
}
