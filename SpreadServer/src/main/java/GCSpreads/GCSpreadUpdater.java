package GCSpreads;

import static Outlook.ExplorerParser.getAccessToken;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.*;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class GCSpreadUpdater {

  public static void main(String[] args) {
    try {
      LocalDate lastDataDate = getLastUpdatedDateFromCSV("data/spreads/GulfCoast/A.csv");
      LocalDate searchStartDate = lastDataDate.plusDays(2); // Email received + 1 day

      System.out.println("Searching for emails starting from: " + searchStartDate);

      OffsetDateTime since = searchStartDate.atStartOfDay().atOffset(ZoneOffset.UTC);
      Map<String, Double> pricingData = getLatestPricingData(since);

      if (!pricingData.isEmpty()) {
        System.out.println("Pricing data retrieved. Updating CSVs...");
        GCcsvupdater.updateSpreadCSVs(pricingData, searchStartDate);
      } else {
        System.out.println("No new pricing data found.");
      }

    } catch (Exception e) {
      System.err.println("Error occurred:");
      e.printStackTrace();
    }
  }

  public static LocalDate getLastUpdatedDateFromCSV(String filePath) throws IOException {
    List<String> lines = Files.readAllLines(Paths.get(filePath));
    int yearIndex = Arrays.asList(lines.get(0).split(",")).indexOf("2025");

    for (int i = lines.size() - 1; i > 0; i--) {
      String[] parts = lines.get(i).split(",", -1);
      if (parts.length > yearIndex && !parts[yearIndex].isBlank()) {
        return LocalDate.parse("2025/" + parts[0], DateTimeFormatter.ofPattern("yyyy/M/d"));
      }
    }
    return LocalDate.of(2025, 1, 1); // fallback
  }

  public static Map<String, Double> getLatestPricingData(OffsetDateTime since) throws Exception {
    String accessToken = getAccessToken();
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<?> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    MessageCollectionPage messages = graphClient.users("automatedreports@rioenergy.com").messages()
        .buildRequest()
        .filter("receivedDateTime ge " + since.toString())
        .select("id,subject")
        .get();

    Message targetMessage = null;
    for (Message message : messages.getCurrentPage()) {
      if (message.subject != null && message.subject.toLowerCase().contains("pricing quote")) {
        targetMessage = message;
        break;
      }
    }

    if (targetMessage == null) {
      throw new Exception("No pricing quote email found since " + since);
    }

    AttachmentCollectionPage attachments = graphClient.users("automatedreports@rioenergy.com")
        .messages(targetMessage.id)
        .attachments()
        .buildRequest()
        .get();

    FileAttachment excelAttachment = null;
    for (Attachment attachment : attachments.getCurrentPage()) {
      if (attachment instanceof FileAttachment && attachment.name != null && attachment.name.toLowerCase().endsWith(".xlsx")) {
        excelAttachment = (FileAttachment) attachment;
        break;
      }
    }

    if (excelAttachment == null) {
      throw new Exception("No Excel attachment found in the email.");
    }

    Attachment attachment = graphClient.users("automatedreports@rioenergy.com")
        .messages(targetMessage.id)
        .attachments(excelAttachment.id)
        .buildRequest()
        .get();

    if (attachment instanceof FileAttachment) {
      byte[] content = ((FileAttachment) attachment).contentBytes;
      return parseExcelData(new ByteArrayInputStream(content));
    }

    return Collections.emptyMap();
  }

  private static Map<String, Double> parseExcelData(InputStream excelStream) throws Exception {
    Map<String, Double> result = new HashMap<>();
    try (XSSFWorkbook workbook = new XSSFWorkbook(excelStream)) {
      Sheet sheet = workbook.getSheetAt(0);
      Map<String, String> headerMapping = Map.of(
          "Platts Gasoline CBOB 87 USGC Houston Prompt Pipeline", "A",
          "Platts Gasoline CBOB 93 USGC Houston Prompt Pipeline", "D",
          "Platts Gasoline CBOB Chicago Buckeye Complex", "ChiCBOB",
          "Platts Gasoline Prem Unleaded 91 Chicago Pipe", "Chi91",
          "Platts Gasoline RBOB 83.7 USGC Houston prompt pipeline", "F",
          "Platts Gasoline RBOB 91.4 USGC Houston Prompt Pipeline", "H",
          "Platts Gasoline RBOB Chicago Buckeye Complex", "ChiRBOB",
          "Platts Gasoline Unl 87 USGC Prompt Pipeline", "M",
          "Platts Gasoline Unl 93 USGC Prompt Pipeline", "GC93"
      );

      Row headerRow = sheet.getRow(0);
      Row dataRow = sheet.getRow(2);

      for (int i = 0; i < headerRow.getLastCellNum(); i++) {
        Cell headerCell = headerRow.getCell(i);
        if (headerCell != null) {
          String header = headerCell.getStringCellValue().trim();
          if (headerMapping.containsKey(header)) {
            Cell valueCell = dataRow.getCell(i);
            if (valueCell != null && valueCell.getCellType() == CellType.NUMERIC) {
              result.put(headerMapping.get(header), valueCell.getNumericCellValue() * 100);
            }
          }
        }
      }
    }
    return result;
  }
}
