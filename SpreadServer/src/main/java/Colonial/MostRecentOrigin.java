package Colonial;

import static Outlook.ExplorerParser.getAccessToken;
import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.*;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import com.squareup.moshi.Json;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class MostRecentOrigin {
  private static final String USER_PRINCIPAL_NAME = "automatedreports@rioenergy.com";
  private static final String EMAIL_SUBJECT_FILTER = "Colonial - Origin";
  private static final int MAX_DAYS_TO_SEARCH = 30;

  public static class OriginComparisonResult {
    @Json(name = "currentReportDate")
    public LocalDate currentReportDate;

    @Json(name = "previousReportDate")
    public LocalDate previousReportDate;

    @Json(name = "currentData")
    public OriginData currentData;

    @Json(name = "previousData")
    public OriginData previousData;

    @Json(name = "isNewerData")
    public boolean isNewerData;

    @Override
    public String toString() {
      return "OriginComparisonResult{" +
          "currentReportDate=" + currentReportDate +
          ", previousReportDate=" + previousReportDate +
          ", isNewerData=" + isNewerData +
          '}';
    }
  }

  public static class OriginData {
    @Json(name = "reportDate")
    public LocalDate reportDate;

    @Json(name = "data")
    // Cycle -> FuelType -> Date
    public Map<String, Map<String, String>> data = new TreeMap<>();

    @Override
    public String toString() {
      return "OriginData{" +
          "reportDate=" + reportDate +
          ", data=" + data +
          '}';
    }
  }

  public static void main(String[] args) {
    System.out.println("=== Starting Origin Data Comparison ===");
    try {
      System.out.println("[MAIN] Fetching and comparing origin data...");
      OriginComparisonResult result = extractAndCompareOriginData();

      System.out.println("\n=== COMPARISON RESULTS ===");
      System.out.println("Current Report Date: " + result.currentReportDate);
      System.out.println("Previous Report Date: " + result.previousReportDate);
      System.out.println("Is Newer Data: " + result.isNewerData);

      System.out.println("\n=== CURRENT DATA ===");
      printOriginData(result.currentData);

      if (result.previousData != null) {
        System.out.println("\n=== PREVIOUS DATA ===");
        printOriginData(result.previousData);

        System.out.println("\n=== DATE CHANGES ===");
        printDateChanges(result.currentData, result.previousData);
      }

      System.out.println("\n=== PROCESS COMPLETED SUCCESSFULLY ===");
    } catch (Exception e) {
      System.err.println("[MAIN ERROR] Failed to compare origin data: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static void printOriginData(OriginData data) {
    if (data == null) {
      System.out.println("No data available");
      return;
    }
    System.out.println("Report Date: " + data.reportDate);
    for (Map.Entry<String, Map<String, String>> cycleEntry : data.data.entrySet()) {
      System.out.println("Cycle " + cycleEntry.getKey() + ":");
      for (Map.Entry<String, String> fuelEntry : cycleEntry.getValue().entrySet()) {
        System.out.println("  " + fuelEntry.getKey() + ": " + fuelEntry.getValue());
      }
    }
  }

  private static void printDateChanges(OriginData current, OriginData previous) {
    for (Map.Entry<String, Map<String, String>> cycleEntry : current.data.entrySet()) {
      String cycle = cycleEntry.getKey();
      if (previous.data.containsKey(cycle)) {
        for (Map.Entry<String, String> fuelEntry : cycleEntry.getValue().entrySet()) {
          String fuelType = fuelEntry.getKey();
          String currentDate = fuelEntry.getValue();
          String previousDate = previous.data.get(cycle).get(fuelType);

          if (previousDate != null && !currentDate.equals(previousDate)) {
            System.out.printf("Cycle %s %s changed from %s to %s%n",
                cycle, fuelType, previousDate, currentDate);
          }
        }
      }
    }
  }

  public static OriginComparisonResult extractAndCompareOriginData() throws IOException {
    try {
      String accessToken = getAccessToken();
      System.out.println("[DEBUG] Access token retrieved successfully");

      // Fetch the most recent origin email
      Message currentMessage = fetchMostRecentOriginEmail(accessToken, USER_PRINCIPAL_NAME);
      if (currentMessage == null) {
        throw new IOException("No origin email found with subject containing: " + EMAIL_SUBJECT_FILTER);
      }

      OriginData currentData = parseOriginEmail(currentMessage);
      LocalDate currentDate = currentData.reportDate;

      // Try to find a previous email from a different date
      OriginData previousData = null;
      LocalDate previousDate = null;

        Message previousMessage = findSecondMostRecentOriginEmail(accessToken, currentMessage.receivedDateTime);

      if (previousMessage != null) {
        previousData = parseOriginEmail(previousMessage);
        previousDate = previousData.reportDate;
      }

      OriginComparisonResult result = new OriginComparisonResult();
      result.currentReportDate = currentDate;
      result.currentData = currentData;
      result.previousReportDate = previousDate;
      result.previousData = previousData;
      result.isNewerData = previousDate == null || currentDate.isAfter(previousDate);

      return result;

    } catch (Exception e) {
      System.err.println("[ERROR] Failed to extract and compare origin data: " + e.getMessage());
      throw new IOException("Failed to extract and compare origin data", e);
    }
  }

  private static Message findSecondMostRecentOriginEmail(String accessToken, OffsetDateTime excludeAfterDate) throws IOException {
    System.out.println("\n[DEBUG] Starting findSecondMostRecentOriginEmail");
    System.out.println("[DEBUG] Excluding emails after: " + excludeAfterDate);
    System.out.println("[DEBUG] Looking back " + MAX_DAYS_TO_SEARCH + " days");

    try {
      IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
      GraphServiceClient<?> graphClient = GraphServiceClient.builder()
          .authenticationProvider(authProvider)
          .buildClient();

      // Calculate date range
      OffsetDateTime searchStart = excludeAfterDate.minusDays(MAX_DAYS_TO_SEARCH);
      System.out.println("[DEBUG] Searching from: " + searchStart + " to " + excludeAfterDate);

      String dateFilter = "receivedDateTime lt " + excludeAfterDate
          .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) +
          " and receivedDateTime ge " + searchStart
          .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

      System.out.println("[DEBUG] Using filter: " + dateFilter);

      MessageCollectionPage messagesPage = graphClient.users(USER_PRINCIPAL_NAME)
          .messages()
          .buildRequest()
          .filter(dateFilter + " and contains(subject, '" + EMAIL_SUBJECT_FILTER + "')")
          .top(100)
          .orderBy("receivedDateTime desc")
          .get();

      System.out.println("[DEBUG] Found " + messagesPage.getCurrentPage().size() + " potential messages");

      // Skip the first message if it has the same receivedDateTime as our exclusion point
      List<Message> filteredMessages = new ArrayList<>();
      for (Message message : messagesPage.getCurrentPage()) {
        if (message.receivedDateTime.isBefore(excludeAfterDate)) {
          filteredMessages.add(message);
        }
      }

      System.out.println("[DEBUG] After filtering, " + filteredMessages.size() + " messages remain");

      if (!filteredMessages.isEmpty()) {
        Message message = filteredMessages.get(0);
        System.out.println("[DEBUG] Found second most recent email:");
        System.out.println("  Subject: " + message.subject);
        System.out.println("  Received: " + message.receivedDateTime);
        System.out.println("  ID: " + message.id);

        // Ensure we have the message body
        if (message.body == null || message.body.content == null) {
          System.out.println("[DEBUG] Fetching full message content...");
          message = graphClient.users(USER_PRINCIPAL_NAME)
              .messages(message.id)
              .buildRequest()
              .select("body,subject,receivedDateTime")
              .get();
        }
        return message;
      }

      System.out.println("[DEBUG] No suitable second message found");
      return null;

    } catch (Exception e) {
      System.err.println("[ERROR] in findSecondMostRecentOriginEmail: " + e.getMessage());
      e.printStackTrace();
      throw new IOException("Failed to find second most recent origin email", e);
    }
  }

  private static Message fetchMostRecentOriginEmail(String accessToken, String userPrincipalName) throws IOException {
    try {
      IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
      GraphServiceClient<?> graphClient = GraphServiceClient.builder()
          .authenticationProvider(authProvider)
          .buildClient();

      String dateFilter = "receivedDateTime ge " +
          OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
              .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

      MessageCollectionPage messagesPage = graphClient.users(userPrincipalName)
          .messages()
          .buildRequest()
          .filter(dateFilter + " and contains(subject, '" + EMAIL_SUBJECT_FILTER + "')")
          .top(1)
          .orderBy("receivedDateTime desc")
          .get();

      if (!messagesPage.getCurrentPage().isEmpty()) {
        Message message = messagesPage.getCurrentPage().get(0);
        System.out.println("[DEBUG] Found matching message: " + message.subject);

        if (message.body == null || message.body.content == null) {
          message = graphClient.users(userPrincipalName)
              .messages(message.id)
              .buildRequest()
              .select("body,subject,receivedDateTime")
              .get();
        }
        return message;
      }
      return null;
    } catch (Exception e) {
      throw new IOException("Failed to fetch origin email", e);
    }
  }

  private static OriginData parseOriginEmail(Message message) {
    OriginData result = new OriginData();

    try {
      if (message.body == null || message.body.content == null) {
        throw new IOException("Email body is empty");
      }

      Document doc = Jsoup.parse(message.body.content);
      result.reportDate = extractDateFromEmail(doc);

      // Find the data table
      Element table = findMainDataTable(doc);
      if (table == null) {
        throw new IOException("No data table found in email");
      }

      // Process table rows
      processTableRows(table, result.data);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse origin email: " + e.getMessage(), e);
    }

    return result;
  }

  private static LocalDate extractDateFromEmail(Document doc) {
    // Try to extract date from email body text
    String bodyText = doc.text();
    String[] datePatterns = {
        "Date:\\s*(\\d{2}/\\d{2}/\\d{2})",
        "(\\d{2}/\\d{2}/\\d{2})\\s+\\d{2}:\\d{2}",
        "as of (\\w+ \\d{1,2}, \\d{4})"
    };

    for (String pattern : datePatterns) {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(bodyText);
      if (m.find()) {
        String dateStr = m.group(1);
        try {
          if (dateStr.contains("/")) {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yy"));
          } else {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMMM d, yyyy"));
          }
        } catch (Exception e) {
          continue;
        }
      }
    }
    throw new RuntimeException("No valid date pattern found in email");
  }

  private static Element findMainDataTable(Document doc) {
    // Look for table with Cycle and Location headers
    for (Element table : doc.select("table")) {
      Elements headers = table.select("tr:first-child th, tr:first-child td");
      boolean hasCycle = false, hasLocation = false;

      for (Element header : headers) {
        String text = header.text().toLowerCase();
        if (text.contains("cycle")) hasCycle = true;
        if (text.contains("location")) hasLocation = true;
      }

      if (hasCycle && hasLocation && headers.size() >= 6) {
        return table;
      }
    }
    return null;
  }

  private static void processTableRows(Element table, Map<String, Map<String, String>> data) {
    // Get column indices
    Elements headerCells = table.select("tr:first-child th, tr:first-child td");
    int locationCol = -1;
    int cycleCol = -1;
    int aCol = -1;  // Regular CBOB
    int dCol = -1;  // Diesel
    int fCol = -1;  // Conventional
    int col62 = -1; // RBOB

    for (int i = 0; i < headerCells.size(); i++) {
      String headerText = headerCells.get(i).text().trim();
      if (headerText.equalsIgnoreCase("Location")) {
        locationCol = i;
      } else if (headerText.equalsIgnoreCase("Cycle")) {
        cycleCol = i;
      } else if (headerText.contains("Regular CBOB") || headerText.contains("L Grade")) {
        aCol = i;
      } else if (headerText.contains("Premium")) {
        dCol = i;
      } else if (headerText.contains("Conventional Regular")) {
        fCol = i;
      } else if (headerText.contains("Ultra Low Sulfur Diesel")) {
        col62 = i;
      }
    }

    // Verify we found all required columns
    if (locationCol == -1 || cycleCol == -1 || aCol == -1 || dCol == -1 || fCol == -1 || col62 == -1) {
      throw new RuntimeException("Required columns not found in table");
    }

    // Process data rows
    for (Element row : table.select("tr:gt(0)")) {
      Elements cells = row.select("td");
      if (cells.size() <= Math.max(Math.max(cycleCol, aCol), Math.max(dCol, Math.max(fCol, col62)))) {
        continue;
      }

      String location = cells.get(locationCol).text().trim();
      if ("HTN/PDA".equalsIgnoreCase(location)) {
        String cycle = cells.get(cycleCol).text().trim();
        data.computeIfAbsent(cycle, k -> new HashMap<>());

        // Get dates for each fuel type
        String aDate = cleanDate(cells.get(aCol).text());
        String dDate = cleanDate(cells.get(dCol).text());
        String fDate = cleanDate(cells.get(fCol).text());
        String date62 = cleanDate(cells.get(col62).text());

        // Only store valid dates
        if (aDate.matches("\\d{2}/\\d{2}")) data.get(cycle).put("A", aDate);
        if (dDate.matches("\\d{2}/\\d{2}")) data.get(cycle).put("D", dDate);
        if (fDate.matches("\\d{2}/\\d{2}")) data.get(cycle).put("F", fDate);
        if (date62.matches("\\d{2}/\\d{2}")) data.get(cycle).put("62", date62);
      }
    }
  }

  private static String cleanDate(String dateStr) {
    return dateStr.replace("*", "").trim();
  }

  private static final Moshi moshi = new Moshi.Builder()
      .add(LocalDate.class, new LocalDateAdapter())
      .build();

  public static String toJson(OriginComparisonResult result) {
    JsonAdapter<OriginComparisonResult> jsonAdapter = moshi.adapter(OriginComparisonResult.class);
    return jsonAdapter.indent("  ").toJson(result);
  }

  // Adapter for LocalDate serialization
  private static class LocalDateAdapter extends JsonAdapter<LocalDate> {
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public LocalDate fromJson(JsonReader reader) throws IOException {
      return LocalDate.parse(reader.nextString(), formatter);
    }

    @Override
    public void toJson(JsonWriter writer, LocalDate value) throws IOException {
      if (value != null) {
        writer.value(formatter.format(value));
      } else {
        writer.nullValue();
      }
    }
  }
}