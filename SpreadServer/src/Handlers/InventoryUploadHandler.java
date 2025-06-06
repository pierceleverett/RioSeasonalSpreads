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
    private static final Object PROCESSING_LOCK = new Object();

    public Object handle(Request request, Response response) throws Exception {
      response.type("application/json");

      try {
        // 1. Validate upload directory
        Path uploadPath = Paths.get(UPLOAD_DIR);
        Files.createDirectories(uploadPath);

        if (!Files.isWritable(uploadPath)) {
          throw new IOException("Upload directory not writable");
        }

        // 2. Process single file (Spark processes multipart sequentially)
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

        // 4. Process with thread-safe lock
        synchronized (PROCESSING_LOCK) {
          String tempFilename = UUID.randomUUID() + ".pdf";
          Path tempFilePath = uploadPath.resolve(tempFilename);

          try (InputStream fileContent = filePart.getInputStream()) {
            Files.copy(fileContent, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
          }

          System.out.println("Processing PDF: " + tempFilePath);
          ExcelUpdater.processSinglePdf(tempFilePath.toFile());
          Files.deleteIfExists(tempFilePath);
        }

        return new Moshi.Builder()
            .build()
            .adapter(Map.class)
            .toJson(Map.of(
                "status", "success",
                "message", "File processed successfully"
            ));

      } catch (Exception e) {
        response.status(500);
        return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
      }
    }
  }
