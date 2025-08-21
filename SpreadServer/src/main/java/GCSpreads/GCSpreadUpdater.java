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

  public static final Map<String, Integer> MONTH_MAP = Map.ofEntries(
      Map.entry("Jan", 1),
      Map.entry("Feb", 2),
      Map.entry("Mar", 3),
      Map.entry("Apr", 4),
      Map.entry("May", 5),
      Map.entry("Jun", 6),
      Map.entry("Jul", 7),
      Map.entry("Aug", 8),
      Map.entry("Sep", 9),
      Map.entry("Oct", 10),
      Map.entry("Nov", 11),
      Map.entry("Dec", 12)
  );

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

//      System.out.println("entered handler for manual refresh");
//      String date = "7-31-2025";
//      LocalDate inputDate = getLocalDate(date);
//      System.out.println("Looking for data for: " + date);
//
//      String accessToken = getAccessToken();
//      IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
//      GraphServiceClient<?> graphClient = GraphServiceClient.builder()
//          .authenticationProvider(authProvider)
//          .buildClient();
//
//      Map<String, Double> data = getPricingDataForDate(graphClient, inputDate.plusDays(1));
//      System.out.println(data);
//      System.out.println("Found data, writing to csv");
//      GCcsvupdater.updateSpreadCSVs(data, inputDate.plusDays(1));
//      System.out.println("code finished successfully");
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
      System.out.println("checking email: " + message.subject);
      if (message.subject != null && message.subject.toLowerCase().contains("pricing quote")) {
        System.out.println("Found pricing quote email");
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
      System.out.println("found excel sheet");
      byte[] content = ((FileAttachment) attachment).contentBytes;
      LocalDate attachmentDate = getAttachmentDate(attachment.name);
      System.out.println("entering parseExcelData for sheet from " + attachmentDate);
      return parseExcelData(new ByteArrayInputStream(content), attachmentDate);
    }

    return Collections.emptyMap();
  }

  public static Map<String, Double> getPricingDataForDate(GraphServiceClient<?> graphClient,
      LocalDate targetDate) throws Exception {

    // Calculate date range (past 10 days)
    OffsetDateTime endDate = OffsetDateTime.now();
    OffsetDateTime startDate = endDate.minusDays(10);
    System.out.println("searching for messages from " + startDate + " to " + endDate);

    // Search messages from the past 10 days
    MessageCollectionPage messages = graphClient.users("automatedreports@rioenergy.com").messages()
        .buildRequest()
        .filter("receivedDateTime ge " + startDate.toString() +
            " and receivedDateTime le " + endDate.toString())
        .select("id,subject,receivedDateTime")
        .top(100) // Increase limit to ensure we get enough messages
        .get();

    // Process all pages of results
    while (messages != null) {
      for (Message message : messages.getCurrentPage()) {
        if (message.subject != null && message.subject.toLowerCase().contains("pricing quote")) {
          System.out.println("Found pricing quote email, processing");
          // Check attachments for matching date
          Map<String, Double> result = processMessageForDate(
              graphClient, message, targetDate);

          if (!result.isEmpty()) {
            return result;
          }
        }
      }

      // Get next page if available
      MessageCollectionRequestBuilder nextPage = messages.getNextPage();
      if (nextPage == null) {
        break;
      }
      messages = nextPage.buildRequest().get();
    }

    return Collections.emptyMap();
  }

  public static Map<String, Double> processMessageForDate(
      GraphServiceClient<?> graphClient, Message message, LocalDate targetDate) throws Exception {

    // Get all attachments
    System.out.println("Getting all attachments from email");
    AttachmentCollectionPage attachments = graphClient.users("automatedreports@rioenergy.com")
        .messages(message.id)
        .attachments()
        .buildRequest()
        .get();


    // Look for Excel attachments
    System.out.println("Checking if one of the attachments is for correct day");
    for (Attachment attachment : attachments.getCurrentPage()) {
      System.out.println("Checking if attachment date equal to " + targetDate);
      System.out.println("attachment date" + getAttachmentDate(attachment.name));
      if (attachment instanceof FileAttachment &&
          attachment.name != null &&
          attachment.name.toLowerCase().endsWith(".xlsx") &&
          getAttachmentDate(attachment.name).equals(targetDate)) {
        LocalDate attachmentDate = getAttachmentDate(attachment.name);

        System.out.println("Found correct excel attachment, downloading attachment");

        // Download the attachment
        FileAttachment excelAttachment = (FileAttachment) graphClient
            .users("automatedreports@rioenergy.com")
            .messages(message.id)
            .attachments(attachment.id)
            .buildRequest()
            .get();

        byte[] content = excelAttachment.contentBytes;

        System.out.println("Parsing data");

        Map<String, Double> data = parseExcelData(new ByteArrayInputStream(content), attachmentDate);
        return data;

      }
    }

    return Collections.emptyMap();
  }

  public static LocalDate getLocalDate(String title) {
    String[] parts = title.split("-");
    System.out.println(parts);
    Integer day = Integer.parseInt(parts[1]);
    System.out.println("day: " + day);
    Integer month = Integer.parseInt(parts[0]);
    System.out.println("month: " + month);
    Integer year = Integer.parseInt(parts[2]);
    System.out.println("year: " + year);
    return LocalDate.of(year, month, day);
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

  private static Map<String, Double> parseExcelData(InputStream excelStream, LocalDate attachmentDate) throws Exception {
    final String METHOD_NAME = "parseExcelData";
    System.out.printf("[%s] Starting with date: %s%n", METHOD_NAME, attachmentDate);

    Map<String, Double> result = new HashMap<>();
    try (XSSFWorkbook workbook = new XSSFWorkbook(excelStream)) {
      Sheet sheet = workbook.getSheetAt(0);
      System.out.printf("[%s] Loaded workbook with sheet: %s%n", METHOD_NAME, sheet.getSheetName());

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
          "Platts Naphtha Cargo FOB US Gulf Coast", "Nap"
      );

      Row headerRow = sheet.getRow(0);
      if (headerRow == null) {
        throw new RuntimeException("Header row (row 0) is null");
      }
      System.out.printf("[%s] Header row found with %d cells%n", METHOD_NAME, headerRow.getLastCellNum());

      // Log header values for debugging
      System.out.printf("[%s] Header values:%n", METHOD_NAME);
      for (int i = 0; i < headerRow.getLastCellNum(); i++) {
        Cell headerCell = headerRow.getCell(i);
        if (headerCell != null) {
          System.out.printf("  Column %d: '%s'%n", i, headerCell.getStringCellValue().trim());
        }
      }

      // Find the row with the matching date
      System.out.printf("[%s] Looking for data row with date: %s%n", METHOD_NAME, attachmentDate);
      Row dataRow = findRowByDate(sheet, attachmentDate.minusDays(1));
      System.out.printf("[%s] Data row found: %s%n", METHOD_NAME, dataRow);

      if (dataRow == null) {
        throw new RuntimeException("No data found for date: " + attachmentDate);
      }

      System.out.printf("[%s] Processing data row with %d cells%n", METHOD_NAME, dataRow.getLastCellNum());

      for (int i = 0; i < headerRow.getLastCellNum(); i++) {
        Cell headerCell = headerRow.getCell(i);
        if (headerCell != null) {
          String header = headerCell.getStringCellValue().trim();
          if (headerMapping.containsKey(header)) {
            Cell valueCell = dataRow.getCell(i);
            if (valueCell != null) {
              System.out.printf("[%s] Processing column %d: %s -> %s (cell type: %s)%n",
                  METHOD_NAME, i, header, headerMapping.get(header), valueCell.getCellType());

              if (valueCell.getCellType() == CellType.NUMERIC) {
                double value = valueCell.getNumericCellValue();
                System.out.printf("[%s] Found numeric value: %.4f -> %.2f (after *100)%n",
                    METHOD_NAME, value, value * 100);
                result.put(headerMapping.get(header), value * 100);
              } else if (valueCell.getCellType() == CellType.STRING) {
                System.out.printf("[%s] Found string value: '%s' (skipping)%n",
                    METHOD_NAME, valueCell.getStringCellValue());
              } else if (valueCell.getCellType() == CellType.BLANK) {
                System.out.printf("[%s] Cell is blank (skipping)%n", METHOD_NAME);
              }
            } else {
              System.out.printf("[%s] Value cell is null for header: %s%n", METHOD_NAME, header);
            }
          }
        }
      }

      System.out.printf("[%s] Successfully extracted %d values%n", METHOD_NAME, result.size());
      System.out.printf("[%s] Result map: %s%n", METHOD_NAME, result);

    } catch (Exception e) {
      System.err.printf("[%s ERROR] Failed to parse Excel data: %s%n", METHOD_NAME, e.getMessage());
      throw e;
    }

    return result;
  }

  private static Row findRowByDate(Sheet sheet, LocalDate targetDate) {
    final String METHOD_NAME = "findRowByDate";
    System.out.printf("[%s] Starting search for target date: %s%n", METHOD_NAME, targetDate);

    // The date column is column A (index 0)
    for (int rowNum = 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
      Row row = sheet.getRow(rowNum);
      if (row == null) {
        System.out.printf("[%s] Row %d is null, skipping%n", METHOD_NAME, rowNum);
        continue;
      }

      Cell dateCell = row.getCell(0);
      if (dateCell == null) {
        System.out.printf("[%s] Row %d - Date cell is null, skipping%n", METHOD_NAME, rowNum);
        continue;
      }

      System.out.printf("[%s] Row %d - Cell type: %s%n", METHOD_NAME, rowNum, dateCell.getCellType());

      // Handle different date formats in Excel
      LocalDate rowDate = null;
      try {
        if (dateCell.getCellType() == CellType.NUMERIC) {
          // Excel stores dates as numeric values
          Date javaDate = dateCell.getDateCellValue();
          System.out.printf("[%s] Row %d - Numeric date value: %s%n", METHOD_NAME, rowNum, javaDate);

          rowDate = javaDate.toInstant()
              .atZone(ZoneId.systemDefault())
              .toLocalDate();

          System.out.printf("[%s] Row %d - Parsed LocalDate: %s%n", METHOD_NAME, rowNum, rowDate);

        } else if (dateCell.getCellType() == CellType.STRING) {
          // Parse string dates like "09/Aug/2025"
          String dateString = dateCell.getStringCellValue().trim();
          System.out.printf("[%s] Row %d - String date value: '%s'%n", METHOD_NAME, rowNum, dateString);

          DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MMM/yyyy", Locale.ENGLISH);
          rowDate = LocalDate.parse(dateString, formatter);

          System.out.printf("[%s] Row %d - Parsed LocalDate: %s%n", METHOD_NAME, rowNum, rowDate);
        } else {
          System.out.printf("[%s] Row %d - Unsupported cell type: %s%n", METHOD_NAME, rowNum, dateCell.getCellType());
          continue;
        }

        if (rowDate != null) {
          System.out.printf("[%s] Row %d - Comparing row date %s with target date %s%n",
              METHOD_NAME, rowNum, rowDate, targetDate);

          if (rowDate.equals(targetDate)) {
            System.out.printf("[%s] SUCCESS: Found matching date at row %d%n", METHOD_NAME, rowNum);
            return row;
          } else {
            System.out.printf("[%s] Row %d - Dates don't match%n", METHOD_NAME, rowNum);
          }
        } else {
          System.out.printf("[%s] Row %d - Failed to parse date%n", METHOD_NAME, rowNum);
        }

      } catch (Exception e) {
        System.out.printf("[%s] Row %d - ERROR parsing date: %s%n", METHOD_NAME, rowNum, e.getMessage());
        // Skip rows with invalid date formats
        continue;
      }
    }

    System.out.printf("[%s] WARNING: No row found for target date %s%n", METHOD_NAME, targetDate);
    return null;
  }

  public static LocalDate getAttachmentDate(String title) {
    String[] parts = title.split("_");
    Integer day = Integer.parseInt(parts[1]);
    Integer month = MONTH_MAP.get(parts[2]);
    Integer year = Integer.parseInt(parts[3]);
    return LocalDate.of(year, month, day);
  }
}
