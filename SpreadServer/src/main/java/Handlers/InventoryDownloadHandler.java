package Handlers;

import spark.Request;
import spark.Response;
import spark.Route;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

public class InventoryDownloadHandler implements Route {

  private static final String EXCEL_PATH = "data/Fuel_Inventory_Report.xlsx";

  public Object handle(Request request, Response response) throws Exception {
    try {
      File excelFile = new File(EXCEL_PATH);

      // Check if file exists
      if (!excelFile.exists()) {
        response.status(404);
        return "{\"error\":\"Inventory data not found. Please upload data first.\"}";
      }

      // Set response headers
      response.type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      response.header("Content-Disposition", "attachment; filename=inventory_data.xlsx");

      // Stream the file
      return Files.readAllBytes(excelFile.toPath());

    } catch (IOException e) {
      response.status(500);
      return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
    }
  }
}
