package Colonial;

import static Outlook.ExplorerParser.getAccessToken;
import com.microsoft.graph.models.*;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.*;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class MostRecentFungible {
  private static final String USER_PRINCIPAL_NAME = "automatedreports@rioenergy.com";
  private static final String EMAIL_SUBJECT_FILTER = "colonial - fungible";
  private static final Set<String> VALID_FUELS = Set.of(
      "52", "54", "56", "62", "78", "96",
      "A", "D", "F", "M", "H", "V"
  );

  public static FungibleData extractLatestFungibleData() throws IOException {
    try {
      System.out.println("[DEBUG] Starting to fetch access token...");
      String accessToken = getAccessToken();
      System.out.println("[DEBUG] Access token retrieved successfully");

      System.out.println("[DEBUG] Fetching most recent fungible email...");
      Message message = fetchMostRecentFungibleEmail(accessToken, USER_PRINCIPAL_NAME);

      if (message == null) {
        String errorMsg = "No fungible email found with subject containing: " + EMAIL_SUBJECT_FILTER;
        System.err.println("[ERROR] " + errorMsg);
        throw new IOException(errorMsg);
      }

      System.out.println("[DEBUG] Found email with subject: " + message.subject);
      System.out.println("[DEBUG] Received date: " + message.receivedDateTime);

      return parseFungibleEmail(message);
    } catch (Exception e) {
      System.err.println("[ERROR] Failed to extract fungible data: " + e.getMessage());
      throw new IOException("Failed to extract fungible data", e);
    }
  }

  private static Message fetchMostRecentFungibleEmail(String accessToken, String userPrincipalName) throws IOException {
    Objects.requireNonNull(accessToken, "Access token cannot be null");
    Objects.requireNonNull(userPrincipalName, "User principal name cannot be null");

    try {
      IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
      GraphServiceClient<?> graphClient = GraphServiceClient.builder()
          .authenticationProvider(authProvider)
          .buildClient();

      // Format date filter correctly (ISO 8601 format)
      String dateFilter = "receivedDateTime ge " +
          OffsetDateTime.now(ZoneOffset.UTC).minusDays(2)
              .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

      System.out.println("[DEBUG] Using date filter: " + dateFilter);

      MessageCollectionPage messagesPage = graphClient.users(userPrincipalName)
          .messages()
          .buildRequest()
          .filter(dateFilter)
          .top(100)
          .orderBy("receivedDateTime desc")
          .get();

      if (messagesPage.getCurrentPage().isEmpty()) {
        System.out.println("[DEBUG] No recent emails found in last 2 days");
        return null;
      }

      // Search through messages locally
      for (Message message : messagesPage.getCurrentPage()) {
        if (message.subject != null &&
            message.subject.toLowerCase().contains(EMAIL_SUBJECT_FILTER)) {

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
      }

      System.out.println("[DEBUG] No matching emails found in recent messages");
      return null;

    } catch (com.microsoft.graph.http.GraphServiceException e) {
      System.err.println("[ERROR] Graph API Error: " + e.getMessage());
      throw new IOException("Failed to fetch fungible email from Graph API: " + e.getMessage(), e);
    } catch (Exception e) {
      System.err.println("[ERROR] Unexpected error: " + e.getMessage());
      throw new IOException("Failed to fetch fungible email", e);
    }
  }

  private static FungibleData parseFungibleEmail(Message message) {
    FungibleData result = new FungibleData();

    try {
      if (message.body == null || message.body.content == null) {
        throw new IOException("Email body is empty");
      }

      Document doc = Jsoup.parse(message.body.content);
      String plainText = doc.text().replaceAll("\\s+", " ").trim();

      // Extract report date
      result.reportDate = extractDateFromPlainText(plainText);
      System.out.println("[DEBUG] Extracted report date: " + result.reportDate);

      // Find the data table
      Element table = findMainDataTable(doc);
      if (table == null) {
        throw new IOException("No data table found in email");
      }

      // Get all location headers
      List<String> locations = getLocationHeaders(table);
      if (locations.isEmpty()) {
        throw new IOException("No location headers found in table");
      }
      System.out.println("[DEBUG] Found locations: " + locations);

      // Process table rows for all locations and fuels
      processAllTableRows(table, result.reportDate, locations, result.data);

    } catch (Exception e) {
      throw new RuntimeException("Failed to parse fungible email: " + e.getMessage(), e);
    }

    return result;
  }

  private static Element findMainDataTable(Document doc) {
    // First try to find table with Product and Cycle headers
    for (Element table : doc.select("table")) {
      Elements headers = table.select("tr:first-child th, tr:first-child td");
      boolean hasProduct = false, hasCycle = false;

      for (Element header : headers) {
        String text = header.text().toLowerCase();
        if (text.contains("product")) hasProduct = true;
        if (text.contains("cycle")) hasCycle = true;
      }

      if (hasProduct && hasCycle) {
        return table;
      }
    }

    // Fallback: Look for any table with location names
    for (Element table : doc.select("table")) {
      Elements headers = table.select("tr:first-child th, tr:first-child td");
      for (int i = 2; i < headers.size(); i++) { // Skip first two columns
        String text = headers.get(i).text().toLowerCase();
        if (!text.isEmpty()) {
          return table;
        }
      }
    }

    return null;
  }

  private static List<String> getLocationHeaders(Element table) {
    List<String> locations = new ArrayList<>();
    Elements headers = table.select("tr:first-child th, tr:first-child td");

    // Skip first two columns (Product and Cycle)
    for (int i = 2; i < headers.size(); i++) {
      String location = headers.get(i).text().trim();
      if (!location.isEmpty()) {
        locations.add(location);
      }
    }

    return locations;
  }

  private static void processAllTableRows(Element table, LocalDate reportDate,
      List<String> locations, Map<String, Map<String, Map<String, String>>> data) {

    String formattedDate = reportDate.format(DateTimeFormatter.ofPattern("MM/dd"));
    Elements rows = table.select("tr:gt(0)"); // Skip header row

    for (Element row : rows) {
      Elements cells = row.select("td");
      if (cells.size() < 2) continue; // Need at least Product and Cycle columns

      String product = cells.get(0).text().trim();
      String rawCycle = cells.get(1).text().trim();

      if (product.isEmpty() || rawCycle.isEmpty() || rawCycle.length() < 2) {
        continue;
      }

      String cycle = rawCycle.substring(0, 2); // Use first two digits of cycle

      // Initialize data structure if needed
      data.computeIfAbsent(cycle, k -> new TreeMap<>())
          .computeIfAbsent(product, k -> new TreeMap<>());

      // Process each location column
      for (int i = 0; i < locations.size(); i++) {
        int colIndex = i + 2; // Skip Product and Cycle columns

        if (colIndex < cells.size()) {
          String dateStr = cells.get(colIndex).text().trim();
          if (!dateStr.isEmpty()) {
            String location = locations.get(i);
            System.out.printf("[DEBUG] Found delivery: %s | %s | %s | %s%n",
                location, product, cycle, dateStr);

            data.get(cycle).get(product).put(location, dateStr);
          }
        }
      }
    }
  }

  private static LocalDate extractDateFromPlainText(String plainText) {
    Pattern[] patterns = {
        Pattern.compile("Date:\\s*(\\d{2}/\\d{2}/\\d{2})"),
        Pattern.compile("(\\d{2}/\\d{2}/\\d{2})\\s+\\d{2}:\\d{2}"),
        Pattern.compile("Date:\\s*(\\w+\\s+\\d{1,2},\\s+\\d{4})")
    };

    for (Pattern p : patterns) {
      Matcher m = p.matcher(plainText);
      if (m.find()) {
        String dateStr = m.group(1);
        try {
          return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yy"));
        } catch (DateTimeParseException e1) {
          try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMMM d, yyyy"));
          } catch (DateTimeParseException e2) {
            continue;
          }
        }
      }
    }
    throw new RuntimeException("No valid date pattern found");
  }

  public static class FungibleData {
    public LocalDate reportDate;
    // Data structure: Cycle -> Product -> Location -> DeliveryDate
    public Map<String, Map<String, Map<String, String>>> data = new TreeMap<>();

    @Override
    public String toString() {
      return "FungibleData{" +
          "reportDate=" + reportDate +
          ", data=" + data +
          '}';
    }
  }
}