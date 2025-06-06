package Handlers;

import java.io.File;
import java.io.FileInputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import spark.Request;
import spark.Response;
import spark.Route;
import com.google.gson.JsonObject;

public class LastestUploadHandler implements Route {

  private static final String EXCEL_PATH = "data/Fuel_Inventory_Report.xlsx";

  public Object handle(Request request, Response response) throws Exception {
    response.type("application/json");
    JsonObject jsonResponse = new JsonObject();

    try {
      File excelFile = new File(EXCEL_PATH);

      if (!excelFile.exists()) {
        response.status(404);
        jsonResponse.addProperty("error", "Inventory file not found");
        return jsonResponse.toString();
      }

      String lastDate = "";
      try (FileInputStream fis = new FileInputStream(excelFile);
          XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

        XSSFSheet sheet = workbook.getSheet("A PREMIUM UNLEADED GASOLINE");
        if (sheet == null) {
          response.status(404);
          jsonResponse.addProperty("error", "Sheet not found");
          return jsonResponse.toString();
        }

        // Start from the last row and move upwards to find the first non-empty date
        for (int i = sheet.getLastRowNum(); i >= 0; i--) {
          Row row = sheet.getRow(i);
          if (row != null) {
            Cell dateCell = row.getCell(0);
            if (dateCell != null && !dateCell.getStringCellValue().trim().isEmpty()) {
              lastDate = dateCell.getStringCellValue();
              break;
            }
          }
        }
      }

      if (lastDate.isEmpty()) {
        response.status(404);
        jsonResponse.addProperty("error", "No valid dates found");
        return jsonResponse.toString();
      }

      jsonResponse.addProperty("lastUpdated", lastDate);
      return jsonResponse.toString();

    } catch (Exception e) {
      response.status(500);
      jsonResponse.addProperty("error", "Error processing file: " + e.getMessage());
      return jsonResponse.toString();
    }
  }
}