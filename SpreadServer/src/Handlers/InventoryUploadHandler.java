package Handlers;

import ExcelUtil.ExcelUpdater;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import javax.servlet.http.Part;
import spark.Request;
import spark.Response;
import spark.Route;
import com.squareup.moshi.Moshi;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

public class InventoryUploadHandler implements Route {
  private static final String UPLOAD_DIR = "data/pdfToAdd/";

  public Object handle(Request request, Response response) throws Exception {
    response.type("application/json");

    try {
      // 1. Validate upload directory
      Path uploadPath = Paths.get(UPLOAD_DIR);
      Files.createDirectories(uploadPath);

      if (!Files.isWritable(uploadPath)) {
        throw new IOException("Upload directory not writable: " + uploadPath);
      }

      // 2. Process uploaded file
      Part filePart = request.raw().getPart("file");
      if (filePart == null) {
        response.status(400);
        return "{\"error\":\"No file uploaded\"}";
      }

      // 3. Validate PDF
      if (!"application/pdf".equals(filePart.getContentType())) {
        response.status(400);
        return "{\"error\":\"Only PDF files are accepted\"}";
      }

      // 4. Save to temp file
      String tempFilename = UUID.randomUUID() + ".pdf";
      Path tempFilePath = uploadPath.resolve(tempFilename);

      try (InputStream fileContent = filePart.getInputStream()) {
        Files.copy(fileContent, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
      }

      // 5. Process with ExcelUpdater
      System.out.println("Processing PDF: " + tempFilePath);
      ExcelUpdater.processSinglePdf(tempFilePath.toFile());

      // 6. Clean up
      Files.deleteIfExists(tempFilePath);

      // 7. Return success
      return new Moshi.Builder().build()
          .adapter(Map.class)
          .toJson(Map.of(
              "status", "success",
              "message", "Inventory updated successfully"
          ));

    } catch (Exception e) {
      response.status(500);
      return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
    }
  }
}