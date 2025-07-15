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
      LocalDate today = LocalDate.now();

      System.out.println("Searching for emails from " + lastDataDate + " to " + today);

      // Get all pricing data since the last data date (inclusive)
      Map<LocalDate, Map<String, Double>> allPricingData = getAllPricingData(lastDataDate, today);

      if (!allPricingData.isEmpty()) {
        System.out.println("Updating CSVs with all missing data...");
        for (Map.Entry<LocalDate, Map<String, Double>> entry : allPricingData.entrySet()) {
          GCcsvupdater.updateSpreadCSVs(entry.getValue(), entry.getKey());
        }
      } else {
        System.out.println("No new pricing data found.");
      }

    } catch (Exception e) {
      System.err.println("Error occurred:");
      e.printStackTrace();
    }
  }

  public static Map<LocalDate, Map<String, Double>> getAllPricingData(LocalDate startDate, LocalDate endDate) throws Exception {
    Map<LocalDate, Map<String, Double>> result = new TreeMap<>();

    // Authenticate once
    String accessToken = getAccessToken();
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<?> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    // Process each day
    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      OffsetDateTime since = date.atStartOfDay().atOffset(ZoneOffset.UTC);
      OffsetDateTime until = date.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

      try {
        Map<String, Double> dailyData = getDailyPricingData(graphClient, since, until);
        if (!dailyData.isEmpty()) {
          result.put(date, dailyData);
          System.out.println("Found data for " + date + ": " + dailyData);
        }
      } catch (Exception e) {
        System.err.println("Error processing date " + date + ": " + e.getMessage());
      }
    }

    return result;
  }

  private static Map<String, Double> getDailyPricingData(GraphServiceClient<?> graphClient,
      OffsetDateTime since,
      OffsetDateTime until) throws Exception {
    MessageCollectionPage messages = graphClient.users("automatedreports@rioenergy.com").messages()
        .buildRequest()
        .filter("receivedDateTime ge " + since.toString() + " and receivedDateTime lt " + until.toString())
        .select("id,subject,receivedDateTime")
        .get();

    Message targetMessage = null;
    for (Message message : messages.getCurrentPage()) {
      if (message.subject != null && message.subject.toLowerCase().contains("pricing quote")) {
        targetMessage = message;
        break;
      }
    }

    if (targetMessage == null) {
      return Collections.emptyMap();
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

  public static LocalDate getLastUpdatedDateFromCSV(String filePath) throws IOException {

    int currentYear = Year.now().getValue();
    String currentyear = Integer.toString(currentYear);
    List<String> lines = Files.readAllLines(Paths.get(filePath));
    int yearIndex = Arrays.asList(lines.get(0).split(",")).indexOf(currentyear);

    for (int i = lines.size() - 1; i > 0; i--) {
      String[] parts = lines.get(i).split(",", -1);
      if (parts.length > yearIndex && !parts[yearIndex].isBlank()) {
        return LocalDate.parse(currentyear + "/" + parts[0], DateTimeFormatter.ofPattern("yyyy/M/d"));
      }
    }
    return LocalDate.of(2025, 1, 1); // fallback
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
          "Platts Gasoline Unl 93 USGC Prompt Pipeline", "GC93",
          "Platts Naphtha Cargo FOB US Gulf Coast","Nap"
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
