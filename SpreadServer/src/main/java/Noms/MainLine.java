package Noms;

import static Colonial.MostRecentOrigin.fetchMostRecentOriginEmail;
import static Colonial.MostRecentOrigin.parseOriginEmail;
import static Colonial.OriginUpdater.updateFromMostRecentOriginEmail;
import static Outlook.ExplorerParser.getAccessToken;

import Colonial.MostRecentOrigin.OriginData;
import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.*;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.time.DayOfWeek;

public class MainLine {
  private static final String USER_PRINCIPAL_NAME = "automatedreports@rioenergy.com";
  private static final String EMAIL_SUBJECT_FILTER = "Colonial - DATEINFO";
  private static final int MAX_DAYS_TO_SEARCH = 14;
  private static final String ORIGIN_CSV_PATH = "data/Colonial/Origin/HTNOrigin2025.csv";
  public static Set<LocalDate> HOLIDAYS;

  public static class MainLineData {
    public LocalDate reportDate;
    // Data structure: Grade -> Destination -> Date
    public Map<String, Map<String, String>> data = new TreeMap<>();

    @Override
    public String toString() {
      return "MainLineData{" +
          "reportDate=" + reportDate +
          ", data=" + data +
          '}';
    }
  }

  public static class ClerkHolidayService {
    private static final String CLERK_API_BASE = "https://api.clerk.dev/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public ClerkHolidayService(String apiKey) {
      this.apiKey = apiKey;
      this.httpClient = HttpClient.newHttpClient();
    }

    public Set<LocalDate> getUserHolidays(String userId) throws Exception {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(CLERK_API_BASE + "/users/" + userId))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException("Failed to fetch user data: " + response.body());
      }

      JSONObject userData = new JSONObject(response.body());
      JSONObject unsafeMetadata = userData.optJSONObject("unsafe_metadata");

      if (unsafeMetadata == null || !unsafeMetadata.has("holidays")) {
        return new HashSet<>();
      }

      JSONArray holidaysArray = unsafeMetadata.getJSONArray("holidays");
      Set<LocalDate> holidays = new HashSet<>();

      for (int i = 0; i < holidaysArray.length(); i++) {
        JSONObject holiday = holidaysArray.getJSONObject(i);
        holidays.add(LocalDate.parse(holiday.getString("date")));
      }

      return holidays;
    }
  }

  public static void main(String[] args) {
    try {
      ClerkHolidayService service = new ClerkHolidayService("sk_test_1qlrksRhhWCq5JoqgdF5oCOMdl3paX4vn6D4EAGhkf");
      Set<LocalDate> holidays = service.getUserHolidays("user_2yN2W6lvSdZjV746FQ7NEexyhVu");
      HOLIDAYS = holidays;
      System.out.println(HOLIDAYS);
      MainLine.MainLineData mainLineData = MainLine.extractLatestMainLineData();
      Map<String, Map<String, String>> processedDates = processMainLineDates(mainLineData);

      System.out.println("Processed Dates:");
      processedDates.forEach((cycle, dates) -> {
        System.out.println("Cycle " + cycle + ":");
        dates.forEach((key, date) -> System.out.println("  " + key + ": " + date));
      });
    } catch (IOException e) {
      e.printStackTrace();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static MainLineData extractLatestMainLineData() throws IOException {
    System.out.println("[MainLine] Starting extractLatestMainLineData()");
    try {
      System.out.println("[MainLine] Getting access token...");
      String accessToken = getAccessToken();
      System.out.println("[MainLine] Successfully obtained access token");

      System.out.println("[MainLine] Fetching most recent main line email (last " + MAX_DAYS_TO_SEARCH + " days)...");
      Message message = fetchMostRecentMainLineEmail(accessToken, USER_PRINCIPAL_NAME);

      if (message == null) {
        String errorMsg = "No DATEINFO email found in last " + MAX_DAYS_TO_SEARCH + " days with subject containing: " + EMAIL_SUBJECT_FILTER;
        System.err.println("[MainLine ERROR] " + errorMsg);
        throw new IOException(errorMsg);
      }

      System.out.println("[MainLine] Found email with subject: " + message.subject);
      System.out.println("[MainLine] Received date: " + message.receivedDateTime);

      System.out.println("[MainLine] Parsing email content...");
      return parseMainLineEmail(message);
    } catch (Exception e) {
      System.err.println("[MainLine ERROR] Failed to extract main line data: " + e.getMessage());
      throw new IOException("Failed to extract main line data", e);
    }
  }

  public static Message fetchMostRecentMainLineEmail(String accessToken, String userPrincipalName) throws IOException {
    System.out.println("[MainLine] Starting fetchMostRecentMainLineEmail()");
    try {
      IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
      GraphServiceClient<?> graphClient = GraphServiceClient.builder()
          .authenticationProvider(authProvider)
          .buildClient();

      OffsetDateTime twoWeeksAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(MAX_DAYS_TO_SEARCH);
      String dateFilter = "receivedDateTime ge " + twoWeeksAgo.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

      System.out.println("[MainLine] Using date filter (last " + MAX_DAYS_TO_SEARCH + " days): " + dateFilter);
      System.out.println("[MainLine] Querying messages...");

      MessageCollectionPage messagesPage = graphClient.users(userPrincipalName)
          .messages()
          .buildRequest()
          .filter(dateFilter)
          .top(100)
          .orderBy("receivedDateTime desc")
          .get();

      System.out.println("[MainLine] Found " + messagesPage.getCurrentPage().size() + " messages in date range");

      for (Message message : messagesPage.getCurrentPage()) {
        System.out.println("[MainLine] Checking message with subject: " + message.subject);
        if (message.subject != null && message.subject.contains(EMAIL_SUBJECT_FILTER)) {
          System.out.println("[MainLine] Found matching email: " + message.subject);
          System.out.println("[MainLine] Received at: " + message.receivedDateTime);

          if (message.body == null || message.body.content == null) {
            System.out.println("[MainLine] Fetching full message content...");
            message = graphClient.users(userPrincipalName)
                .messages(message.id)
                .buildRequest()
                .select("body,subject,receivedDateTime")
                .get();
          }
          return message;
        }
      }

      System.out.println("[MainLine] No matching emails found in last " + MAX_DAYS_TO_SEARCH + " days");
      return null;

    } catch (Exception e) {
      System.err.println("[MainLine ERROR] Failed to fetch main line email: " + e.getMessage());
      throw new IOException("Failed to fetch main line email", e);
    }
  }

  public static MainLineData parseMainLineEmail(Message message) {
    System.out.println("[MainLine] Starting parseMainLineEmail()");
    MainLineData result = new MainLineData();

    try {
      if (message.body == null || message.body.content == null) {
        throw new IOException("Email body is empty");
      }

      System.out.println("[MainLine] Parsing HTML content...");
      Document doc = Jsoup.parse(message.body.content);
      String plainText = doc.text().replaceAll("\\s+", " ").trim();

      System.out.println("[MainLine] Extracting report date...");
      result.reportDate = extractDateFromPlainText(plainText);
      System.out.println("[MainLine] Extracted report date: " + result.reportDate);

      System.out.println("[MainLine] Finding main data table...");
      Element table = findMainLineTable(doc);
      if (table == null) {
        throw new IOException("No data table found in email");
      }
      System.out.println("[MainLine] Found data table");

      System.out.println("[MainLine] Extracting destination headers...");
      List<String> destinations = getDestinationHeaders(table);
      if (destinations.isEmpty()) {
        throw new IOException("No destination headers found in table");
      }
      System.out.println("[MainLine] Found destinations: " + destinations);

      System.out.println("[MainLine] Processing table rows...");
      processMainLineTableRows(table, destinations, result.data);
      System.out.println("[MainLine] Processed " + result.data.size() + " grades");

    } catch (Exception e) {
      System.err.println("[MainLine ERROR] Failed to parse main line email: " + e.getMessage());
      throw new RuntimeException("Failed to parse main line email", e);
    }

    return result;
  }

  private static Element findMainLineTable(Document doc) {
    System.out.println("[MainLine] Searching for main line table...");
    int tableCount = 0;

    for (Element table : doc.select("table")) {
      tableCount++;
      Elements headers = table.select("tr:first-child th, tr:first-child td");
      boolean hasGrade = false;

      System.out.println("[MainLine] Checking table #" + tableCount + " with " + headers.size() + " headers");

      for (Element header : headers) {
        String text = header.text().toLowerCase();
        System.out.println("[MainLine] Header text: " + text);
        if (text.contains("grade")) {
          hasGrade = true;
          break;
        }
      }

      if (hasGrade && headers.size() > 1) {
        System.out.println("[MainLine] Found valid main line table");
        return table;
      }
    }

    System.out.println("[MainLine] No valid main line table found");
    return null;
  }

  private static List<String> getDestinationHeaders(Element table) {
    System.out.println("[MainLine] Extracting destination headers...");
    List<String> destinations = new ArrayList<>();
    Elements headers = table.select("tr:first-child th, tr:first-child td");

    System.out.println("[MainLine] Found " + headers.size() + " headers");

    // Skip first column (GRADE)
    for (int i = 1; i < headers.size(); i++) {
      String dest = headers.get(i).text().trim();
      System.out.println("[MainLine] Header " + i + ": " + dest);
      if (!dest.isEmpty()) {
        destinations.add(dest);
      }
    }

    System.out.println("[MainLine] Extracted " + destinations.size() + " destinations");
    return destinations;
  }

  private static void processMainLineTableRows(Element table, List<String> destinations,
      Map<String, Map<String, String>> data) {

    System.out.println("[MainLine] Processing table rows...");
    Elements rows = table.select("tr:gt(0)"); // Skip header row
    System.out.println("[MainLine] Found " + rows.size() + " data rows");

    for (Element row : rows) {
      Elements cells = row.select("td");
      System.out.println("[MainLine] Row has " + cells.size() + " cells");

      if (cells.size() < 2) {
        System.out.println("[MainLine] Skipping row - insufficient cells");
        continue; // Need at least GRADE and one destination
      }

      String grade = cells.get(0).text().trim();
      System.out.println("[MainLine] Processing grade: " + grade);

      if (grade.isEmpty()) {
        System.out.println("[MainLine] Skipping row - empty grade");
        continue;
      }

      // Initialize grade entry if needed
      data.computeIfAbsent(grade, k -> new TreeMap<>());

      // Process each destination column
      for (int i = 0; i < destinations.size(); i++) {
        int colIndex = i + 1; // Skip GRADE column

        if (colIndex < cells.size()) {
          String dateStr = cells.get(colIndex).text().trim();
          // Remove asterisk if present
          if (dateStr.endsWith("*")) {
            dateStr = dateStr.substring(0, dateStr.length() - 1);
            System.out.println("[MainLine] Removed asterisk from date: " + dateStr);
          }

          String destination = destinations.get(i);

          if (!dateStr.isEmpty()) {
            System.out.println("[MainLine] Found date for " + destination + ": " + dateStr);
            data.get(grade).put(destination, dateStr);
          } else {
            System.out.println("[MainLine] Empty date for " + destination);
          }
        } else {
          System.out.println("[MainLine] Missing column for " + destinations.get(i));
        }
      }
    }
  }

  private static LocalDate extractDateFromPlainText(String plainText) {
    System.out.println("[MainLine] Extracting date from plain text...");
    System.out.println("[MainLine] Plain text sample: " + plainText.substring(0, Math.min(200, plainText.length())) + "...");

    // First try to find the bulletin date in the email headers
    Pattern datePattern = Pattern.compile(
        "Date:\\s*(\\d{2}/\\d{2}/\\d{2})|" +
            "(\\d{2}/\\d{2}/\\d{2}\\s+\\d{2}:\\d{2})|" +
            "Printed on (\\d{2}/\\d{2}/\\d{4})");

    Matcher m = datePattern.matcher(plainText);
    if (m.find()) {
      System.out.println("[MainLine] Found date pattern match");
      for (int i = 1; i <= m.groupCount(); i++) {
        if (m.group(i) != null) {
          String dateStr = m.group(i);
          System.out.println("[MainLine] Trying to parse date: " + dateStr);
          try {
            if (dateStr.length() > 8) { // Contains time
              dateStr = dateStr.substring(0, 8);
            }
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yy"));
          } catch (DateTimeParseException e) {
            try {
              return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            } catch (DateTimeParseException e2) {
              System.out.println("[MainLine] Failed to parse date format for group " + i);
              continue;
            }
          }
        }
      }
    }
    System.err.println("[MainLine ERROR] No valid date pattern found in email");
    throw new RuntimeException("No valid date pattern found in email");
  }


  private static boolean isBusinessDay(LocalDate date) throws Exception {
    DayOfWeek dayOfWeek = date.getDayOfWeek();
    //System.out.println("Holiday contains date " + date + ": " + HOLIDAYS.contains(date));
    return dayOfWeek != DayOfWeek.SATURDAY
        && dayOfWeek != DayOfWeek.SUNDAY
        && !HOLIDAYS.contains(date);
  }

  static LocalDate subtractBusinessDays(LocalDate date, int daysToSubtract)
      throws Exception {
    if (daysToSubtract <= 0) return date;

    LocalDate result = date;
    int subtractedDays = 0;

    while (subtractedDays < daysToSubtract) {
      result = result.minusDays(1);
      if (isBusinessDay(result)) {
        subtractedDays++;
      }
    }

    return result;
  }

  static Optional<String> adjustSchedulingDate(String dateStr, int daysToSubtract) {
    if (dateStr == null || dateStr.isEmpty()) return Optional.empty();

    try {
      // Parse date (assuming format MM/DD)
      LocalDate date = LocalDate.parse(dateStr + "/2025",
          DateTimeFormatter.ofPattern("MM/dd/yyyy"));

      LocalDate adjustedDate = subtractBusinessDays(date, daysToSubtract);

      return Optional.of(adjustedDate.format(DateTimeFormatter.ofPattern("MM/dd"))
          .replace("/2025", "")); // Remove year for consistency
    } catch (Exception e) {
      System.err.println("Error adjusting date: " + dateStr);
      return Optional.empty();
    }
  }

  public static Map<String, Map<String, String>> processMainLineDates(MainLineData mainLineData) throws IOException {
    Map<String, Map<String, String>> result = new TreeMap<>();

    // First update the origin starts from emails
    String accessToken = getAccessToken();
    System.out.println("Fetching most recent origin email");
    Message mostRecentOrigin = fetchMostRecentOriginEmail(accessToken, "automatedreports@rioenergy.com");
    OriginData parsedOrigin = parseOriginEmail(mostRecentOrigin);
    System.out.println(parsedOrigin);
    String originBulletinDate = parsedOrigin.reportDate.toString();
    Map<String, Map<String, String>> originData = parsedOrigin.data;

    // Group grades by cycle (last 2 digits of grade)
    Map<String, List<String>> gradesByCycle = new TreeMap<>();

    for (String grade : mainLineData.data.keySet()) {
      if (grade.length() < 3) continue;

      String cycle = grade.substring(grade.length() - 2);
      gradesByCycle.computeIfAbsent(cycle, k -> new ArrayList<>()).add(grade);
    }

    for (Map.Entry<String, List<String>> entry : gradesByCycle.entrySet()) {
      String cycle = entry.getKey();
      Map<String, String> cycleData = new HashMap<>();

      // Find distillate grades (51, 54, 62)
      List<String> distillateGrades = entry.getValue().stream()
          .filter(g -> g.startsWith("51-") || g.startsWith("54-") || g.startsWith("62-"))
          .collect(Collectors.toList());

      // Check if we have all three required grades (51, 54, and 62)
      boolean hasAllGrades = distillateGrades.stream()
          .map(g -> g.split("-")[0])
          .collect(Collectors.toSet())
          .containsAll(Set.of("51", "54", "62"));

      // Only include Distillate_Nomination if we have all three grades
      if (hasAllGrades) {
        Optional<String> earliestDistillateDate = distillateGrades.stream()
            .flatMap(g -> mainLineData.data.get(g).values().stream())
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder());

        earliestDistillateDate.flatMap(d -> adjustSchedulingDate(d, 5))
            .ifPresent(date -> cycleData.put("Distillate_Nomination", date));
      }

      // Process main line fields
      Optional<String> aSchedulingDate = entry.getValue().stream()
          .filter(g -> g.startsWith("A"))
          .findFirst()
          .flatMap(g -> mainLineData.data.get(g).values().stream().findFirst());

      Optional<String> distillate62SchedulingDate = entry.getValue().stream()
          .filter(g -> g.startsWith("62-"))
          .findFirst()
          .flatMap(g -> mainLineData.data.get(g).values().stream().findFirst());

      Optional<String> gasNomination = aSchedulingDate.flatMap(d -> adjustSchedulingDate(d, 5));

      // Convert cycle from two-digit string to number for CSV lookup
      int cycleNumber;
      try {
        cycleNumber = Integer.parseInt(cycle);
      } catch (NumberFormatException e) {
        System.err.println("Invalid cycle format: " + cycle);
        continue;
      }

      // Add origin dates from CSV
      Optional<String> aOriginDate = getOriginDateForCycle(originData, "A", String.valueOf(cycleNumber));
      Optional<String> distillate62OriginDate = getOriginDateForCycle(originData, "62", String.valueOf(cycleNumber));

      // Process stub line nominations using helper methods

      // Put all fields in cycle data
      gasNomination.ifPresent(date -> cycleData.put("Gas_Nomination", date));
      aSchedulingDate.ifPresent(date -> cycleData.put("A_Scheduling_Date", date));
      distillate62SchedulingDate.ifPresent(date -> cycleData.put("62_Scheduling_Date", date));
      aOriginDate.ifPresent(date -> cycleData.put("A_Origin_Date", date));
      distillate62OriginDate.ifPresent(date -> cycleData.put("62_Origin_Date", date));

      if (mainLineData.reportDate != null) {
        cycleData.put("DateInfo_Bulletin_Date", mainLineData.reportDate.toString());
      }
      if (originBulletinDate != null) {
        cycleData.put("Origin_Bulletin_Date", originBulletinDate.toString());
      }

      if (!cycleData.isEmpty()) {
        result.put(cycle, cycleData);
      }
    }

    return result;
  }

  private static Optional<String> getOriginDateForCycle(Map<String, Map<String, String>> originData, String type, String cycle) {
    if (!originData.containsKey(cycle)) {
      return Optional.empty();
    }

    Map<String, String> typeDates = originData.get(cycle);
    // Check if the cycle exists in the type's dates
    if (typeDates.containsKey(type)) {
      String date = typeDates.get(type);
      if (date != null && !date.trim().isEmpty()) {
        return Optional.of(date);
      }
    }
    return Optional.empty();
  }


}