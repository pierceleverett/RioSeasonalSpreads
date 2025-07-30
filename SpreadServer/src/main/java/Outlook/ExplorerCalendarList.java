package Outlook;

import static Outlook.ExplorerParser.getAccessToken;

import Outlook.FusionCurveParser.SimpleAuthProvider;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import com.squareup.moshi.Json;
import com.squareup.moshi.JsonClass;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class ExplorerCalendarList {

  private static final String EMAIL_SUBJECT_FILTER = "explorer - calendar list";
  private static final String[] LOCATIONS = {"PTN", "PTA", "PAS", "GRV", "GLN", "WDR", "HMD"};

  @JsonClass(generateAdapter = true)
  public static class CalendarList {
    @Json(name = "bulletinDate")
    private final String bulletinDate;

    @Json(name = "data")
    private final Map<String, Map<String, Map<String, String>>> data;

    public CalendarList(String bulletinDate, Map<String, Map<String, Map<String, String>>> data) {
      this.bulletinDate = bulletinDate;
      this.data = data;
    }

    public String getBulletinDate() {
      return bulletinDate;
    }

    public Map<String, Map<String, Map<String, String>>> getData() {
      return data;
    }
  }


  public static void main(String[] args) {
    try {
      System.out.println("[DEBUG] Starting to get access token...");
      String accessToken = getAccessToken();
      System.out.println("[DEBUG] Access token retrieved");
      Message message = fetchMostRecentCalendarEmail(accessToken, "automatedreports@rioenergy.com");
      String bulletinDate = extractBulletinDate(message).toString();
      Map<String, Map<String, Map<String, String>>> output = parseCalendarList(message);
      CalendarList bulletin = new CalendarList(bulletinDate, output);
      System.out.println(bulletin);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Message fetchMostRecentCalendarEmail(String accessToken, String userPrincipalName) throws IOException {
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

  public static Map<String, Map<String, Map<String, String>>> parseCalendarList(Message message) {
    Map<String, Map<String, Map<String, String>>> result = new HashMap<>();
    System.out.println("[DEBUG] Starting parseCalendarList");

    if (message == null || message.body == null || message.body.content == null) {
      System.out.println("[DEBUG] Message or body content is null");
      return result;
    }

    String emailContent = message.body.content;
    Document doc = Jsoup.parse(emailContent);
    String plainText = doc.body().wholeText();
    System.out.println("[DEBUG] Plain text length: " + plainText.length());

    String[] lines = plainText.split("\\r?\\n");
    System.out.println("[DEBUG] Found " + lines.length + " lines in plain text");

    // Find the line with the data (line 4 in your example)
    String dataLine = lines[4].trim();
    System.out.println("[DEBUG] Data line: " + dataLine);

    // Extract just the table data part (after the note)
    int tableStart = dataLine.indexOf("CycleProductPTN ClosePTA ClosePAS CloseGRV CloseGLN CloseWDR CloseHMD Close");
    if (tableStart == -1) {
      System.out.println("[DEBUG] Could not find table header in data line");
      return result;
    }

    String tableData = dataLine.substring(tableStart + "CycleProductPTN ClosePTA ClosePAS CloseGRV CloseGLN CloseWDR CloseHMD Close".length());
    System.out.println("[DEBUG] Table data: " + tableData);

    // This pattern matches:
    // 1. The cycle number (1+ digits)
    // 2. The product type (letters)
    // 3. Seven dates in MM/dd/yyyy format
    Pattern recordPattern = Pattern.compile("(\\d+)(Jet|Gas|Oil)((?:\\d{1,2}/\\d{1,2}/\\d{4}){7})");
    Matcher matcher = recordPattern.matcher(tableData);

    while (matcher.find()) {
      String cycle = matcher.group(1);
      String product = matcher.group(2);
      String dates = matcher.group(3);

      // Split the dates string into individual dates
      String[] dateArray = dates.split("(?<=\\d{4})(?=\\d)");
      if (dateArray.length != LOCATIONS.length) {
        System.out.println("[DEBUG] Unexpected number of dates for " + cycle + product);
        continue;
      }

      Map<String, String> locationDates = new HashMap<>();
      for (int i = 0; i < LOCATIONS.length; i++) {
        locationDates.put(LOCATIONS[i], dateArray[i]);
      }

      result.computeIfAbsent(cycle, k -> new HashMap<>())
          .put(product, locationDates);
    }

    System.out.println("[DEBUG] Result map size: " + result.size());
    System.out.println("[DEBUG] Sample data: " + (result.isEmpty() ? "empty" : result.entrySet().iterator().next()));
    return result;
  }



  // Add this method to your ExplorerScheduling class
  public static LocalDate extractBulletinDate(Message message) {
    if (message == null || message.body == null || message.body.content == null) {
      System.out.println("[DEBUG] Message or body content is null");
      return null;
    }

    String emailContent = message.body.content;
    Document doc = Jsoup.parse(emailContent);
    String plainText = doc.body().wholeText();
    String[] lines = plainText.split("\\r?\\n");

    if (lines.length == 0) {
      System.out.println("[DEBUG] No lines found in email");
      return null;
    }

    // The first line contains the date (from your example: "Calendar List July 2025Publisher:Explorer ... Date:07/28/25 15:17")
    String firstLine = lines[0].trim();
    System.out.println("[DEBUG] First line: " + firstLine);

    // Look for the date pattern in the first line
    // First try to find "Date:MM/dd/yy" pattern
    int dateIndex = firstLine.indexOf("Date:");
    if (dateIndex != -1) {
      String datePart = firstLine.substring(dateIndex + 5).trim();
      // Take the first part before space (to get just the date without time)
      String[] dateTimeParts = datePart.split("\\s+");
      if (dateTimeParts.length > 0) {
        try {
          // Handle both 2-digit and 4-digit year formats
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/[yy][yyyy]");
          return LocalDate.parse(dateTimeParts[0], formatter);
        } catch (DateTimeParseException e) {
          System.out.println("[DEBUG] Failed to parse date from Date: pattern: " + dateTimeParts[0]);
        }
      }
    }

    // If "Date:" pattern not found, try to find a date in the line
    // This regex looks for MM/dd/yy or MM/dd/yyyy patterns
    java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile("\\b(\\d{1,2}/\\d{1,2}/(?:\\d{4}|\\d{2}))\\b");
    java.util.regex.Matcher matcher = datePattern.matcher(firstLine);
    if (matcher.find()) {
      try {
        String foundDate = matcher.group(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/[yy][yyyy]");
        return LocalDate.parse(foundDate, formatter);
      } catch (DateTimeParseException e) {
        System.out.println("[DEBUG] Failed to parse found date: " + e.getMessage());
      }
    }

    System.out.println("[DEBUG] Could not find a valid date in the first line");
    return null;
  }


}
