package Handlers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import spark.Request;
import spark.Response;
import spark.Route;
import com.google.gson.JsonObject;

public class LatestUploadHandler implements Route {

  private static final String EXCEL_PATH = "data/Fuel_Inventory_Report.xlsx";
  private static final Logger LOGGER = Logger.getLogger(LatestUploadHandler.class.getName());

  public Object handle(Request request, Response response) {
    response.type("application/json");
    JsonObject jsonResponse = new JsonObject();

    try {
      File excelFile = new File(EXCEL_PATH);
      LOGGER.info("Checking file at: " + excelFile.getAbsolutePath());

      if (!excelFile.exists() || !excelFile.isFile() || excelFile.length() == 0) {
        String errorMsg = "Excel file not found or is empty at: " + excelFile.getAbsolutePath();
        LOGGER.warning(errorMsg);
        response.status(404);
        jsonResponse.addProperty("error", errorMsg);
        return jsonResponse.toString();
      }

      String lastDate = "";
      try (FileInputStream fis = new FileInputStream(excelFile);
          Workbook workbook = new XSSFWorkbook(fis)) {

        Sheet sheet = workbook.getSheet("A PREMIUM UNLEADED GASOLINE");
        if (sheet == null) {
          String errorMsg = "Sheet 'A PREMIUM UNLEADED GASOLINE' not found.";
          LOGGER.warning(errorMsg);
          response.status(404);
          jsonResponse.addProperty("error", errorMsg);
          return jsonResponse.toString();
        }

        for (int i = sheet.getLastRowNum(); i >= 0; i--) {
          Row row = sheet.getRow(i);
          if (row != null) {
            Cell dateCell = row.getCell(0);
            if (dateCell != null && !dateCell.toString().trim().isEmpty()) {
              lastDate = dateCell.toString();
              break;
            }
          }
        }
      }

      if (lastDate.isEmpty()) {
        String errorMsg = "No valid dates found in the sheet.";
        LOGGER.warning(errorMsg);
        response.status(404);
        jsonResponse.addProperty("error", errorMsg);
        return jsonResponse.toString();
      }

      jsonResponse.addProperty("lastUpdated", lastDate);
      return jsonResponse.toString();

    } catch (IOException e) {
      String errorMsg = "Error reading Excel file: " + e.getMessage();
      LOGGER.log(Level.SEVERE, errorMsg, e);
      response.status(500);
      jsonResponse.addProperty("error", errorMsg);
      return jsonResponse.toString();
    } catch (Exception e) {
      String errorMsg = "Unhandled exception: " + e.getMessage();
      LOGGER.log(Level.SEVERE, errorMsg, e);
      response.status(500);
      jsonResponse.addProperty("error", errorMsg);
      return jsonResponse.toString();
    }
  }
}
