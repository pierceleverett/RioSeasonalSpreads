package Colonial;

import static Outlook.ExplorerParser.getAccessToken;

import com.microsoft.graph.models.*;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.*;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

public class ColonialOrigin {

  private static final String ORIGIN_CSV_BASE = "data/Colonial/Origin/HTNOrigin";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd");
  private static final DateTimeFormatter BULLETIN_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yy");
  private static final Map<String, LocalDate> latestBulletinDates = new HashMap<>();

  public static void main(String[] args) throws IOException {
    String accessToken = getAccessToken();
    String userPrincipalName = "automatedreports@rioenergy.com";

    try {
      System.out.println("Starting Colonial Origin processing...");
      fetchAndProcessOriginStartsEmails(accessToken, userPrincipalName);
      System.out.println("Processing completed successfully.");
    } catch (Exception e) {
      System.err.println("Error processing emails: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public static void fetchAndProcessOriginStartsEmails(String accessToken, String userPrincipalName) throws IOException {
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<?> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    List<Message> relevantMessages = new ArrayList<>();
    LinkedList<QueryOption> requestOptions = new LinkedList<>();
    requestOptions.add(new QueryOption("$select", "subject,receivedDateTime,body"));
    requestOptions.add(new QueryOption("$top", "500"));

    MessageCollectionPage messagesPage;
    MessageCollectionRequestBuilder nextPage = null;

    do {
      messagesPage = (nextPage == null)
          ? graphClient.users(userPrincipalName).messages()
          .buildRequest(requestOptions)
          .orderBy("receivedDateTime desc")
          .get()
          : nextPage.buildRequest().get();

      for (Message message : messagesPage.getCurrentPage()) {
        if (message.subject != null && message.subject.toLowerCase().contains("colonial - origin")) {
          if (message.body == null || message.body.content == null) {
            Message fullMessage = graphClient.users(userPrincipalName)
                .messages(message.id)
                .buildRequest()
                .select("body")
                .get();
            message.body = fullMessage.body;
          }

          LocalDate bulletinDate = extractBulletinDate(message.body.content);
          if (bulletinDate != null) {
            System.out.println("Processing email from: " + bulletinDate);
            processOriginStartsEmail(message.body.content, bulletinDate);
          }
        }
      }

      nextPage = messagesPage.getNextPage();
    } while (nextPage != null);
  }

  public static LocalDate extractBulletinDate(String htmlContent) {
    Document doc = Jsoup.parse(htmlContent);
    String text = doc.text();
    Pattern pattern = Pattern.compile("Date:\\s*(\\d{2}/\\d{2}/\\d{2,4})");
    Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      try {
        String dateStr = matcher.group(1);
        // Handle both 2-digit and 4-digit years
        if (dateStr.length() == 8) { // MM/dd/yy
          return LocalDate.parse(dateStr, BULLETIN_DATE_FORMAT);
        } else { // MM/dd/yyyy
          return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        }
      } catch (Exception e) {
        System.err.println("Failed to parse bulletin date: " + e.getMessage());
      }
    }
    return null;
  }


  public static void processOriginStartsForYear(String htmlContent, LocalDate bulletinDate,
      String csvPath, int targetYear) throws IOException {
    Path path = Paths.get(csvPath);

    // 1. Read existing CSV or initialize with header if empty
    List<String> lines;
    if (Files.exists(path)) {
      lines = Files.readAllLines(path, StandardCharsets.UTF_8);
      if (lines.isEmpty()) {
        lines = List.of("Fuel," + String.join(",", Collections.nCopies(72, "")));
      }
    } else {
      lines = List.of("Fuel," + String.join(",", Collections.nCopies(72, "")));
    }

    // 2. Parse CSV structure with bulletin date tracking
    String[] headers = lines.get(0).split(",");
    Map<String, Map<String, FuelDateEntry>> csvData = new LinkedHashMap<>();

    for (int i = 1; i < lines.size(); i++) {
      String[] parts = lines.get(i).split(",", -1);
      if (parts.length == 0) continue;

      String fuelCode = parts[0];
      Map<String, FuelDateEntry> fuelData = new HashMap<>();

      for (int j = 1; j < parts.length && j < headers.length; j++) {
        if (!parts[j].isEmpty()) {
          fuelData.put(headers[j], new FuelDateEntry(parts[j], null));
        }
      }
      csvData.put(fuelCode, fuelData);
    }

    // 3. Parse email content
    Document doc = Jsoup.parse(htmlContent);
    Elements tables = doc.select("table");

    // First pass: Determine the most recent bulletin date for each cycle
    for (Element table : tables) {
      Elements rows = table.select("tr");
      if (rows.isEmpty()) continue;

      for (Element row : rows) {
        Elements cells = row.select("td");
        if (cells.size() < 8) continue;

        try {
          if (cells.get(0).text().equals("HTN/PDA")) {
            String cycle = cells.get(1).text().trim();
            LocalDate currentLatest = latestBulletinDates.get(cycle);
            if (currentLatest == null || bulletinDate.isAfter(currentLatest)) {
              latestBulletinDates.put(cycle, bulletinDate);
            }
          }
        } catch (Exception e) {
          System.err.println("Error processing row for latest bulletin date: " + e.getMessage());
          continue;
        }
      }
    }

    // Second pass: Process updates only from the most recent bulletins
    for (Element table : tables) {
      Elements rows = table.select("tr");
      if (rows.isEmpty()) continue;

      for (Element row : rows) {
        Elements cells = row.select("td");
        if (cells.size() < 8) continue;

        try {
          if (cells.get(0).text().equals("HTN/PDA")) {
            String cycle = cells.get(1).text().trim();
            int cycleNum = Integer.parseInt(cycle);

            // Only process cycles that belong to this target year
            if (shouldProcessCycleForYear(bulletinDate, cycleNum, targetYear) &&
                bulletinDate.equals(latestBulletinDates.get(cycle))) {

              System.out.printf("Updating %d data for cycle %s%n", targetYear, cycle);

              // Safely update each fuel type with bounds checking
              updateFuelDateSafely(csvData, cells, "A", 2, cycle, bulletinDate);
              updateFuelDateSafely(csvData, cells, "D", 4, cycle, bulletinDate);
              updateFuelDateSafely(csvData, cells, "F", 5, cycle, bulletinDate);
              updateFuelDateSafely(csvData, cells, "62", 7, cycle, bulletinDate);
            }
          }
        } catch (Exception e) {
          System.err.println("Error processing row: " + e.getMessage());
          continue;
        }
      }
    }

    // 5. Prepare updated CSV content
    List<String> updatedLines = new ArrayList<>();
    updatedLines.add(String.join(",", headers));

    // Ensure all required fuel types exist
    String[] requiredFuels = {"A", "D", "F", "62"};
    for (String fuel : requiredFuels) {
      csvData.putIfAbsent(fuel, new HashMap<>());
    }

    // 6. Generate CSV rows
    for (Map.Entry<String, Map<String, FuelDateEntry>> fuelEntry : csvData.entrySet()) {
      String[] row = new String[headers.length];
      row[0] = fuelEntry.getKey();

      for (int i = 1; i < headers.length; i++) {
        FuelDateEntry entry = fuelEntry.getValue().get(headers[i]);
        row[i] = (entry != null) ? entry.date : "";
      }
      updatedLines.add(String.join(",", row));
    }

    // 7. Write to file
    Files.createDirectories(path.getParent());
    Files.write(path, updatedLines, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static boolean isTransitionPeriod(LocalDate date) {
    int month = date.getMonthValue();
    return month >= 11 || month <= 2;
  }

  private static boolean shouldProcessCycleForYear(LocalDate bulletinDate, int cycleNum, int targetYear) {
    int currentYear = bulletinDate.getYear();
    int month = bulletinDate.getMonthValue();

    // February through October - only current year data
    if (month >= 2 && month <= 10) {
      return targetYear == currentYear;
    }

    // January handling
    if (month == 1) {
      if (targetYear == currentYear - 1) {
        // Previous year gets only cycles 71-72
        return cycleNum >= 71;
      } else if (targetYear == currentYear) {
        // Current year gets cycles 1-70
        return cycleNum <= 70;
      }
    }

    // November-December handling
    if (month >= 11) {
      if (targetYear == currentYear) {
        // Current year gets cycles 11-72
        return cycleNum >= 11;
      } else if (targetYear == currentYear + 1) {
        // Next year gets cycles 1-10
        return cycleNum <= 10;
      }
    }

    return false;
  }

  public static void processOriginStartsEmail(String htmlContent, LocalDate bulletinDate) throws IOException {
    int currentYear = bulletinDate.getYear();
    int month = bulletinDate.getMonthValue();

    // Always process current year
    processOriginStartsForYear(htmlContent, bulletinDate,
        ORIGIN_CSV_BASE + currentYear + ".csv", currentYear);

    // January - also process previous year for cycles 71-72
    if (month == 1) {
      processOriginStartsForYear(htmlContent, bulletinDate,
          ORIGIN_CSV_BASE + (currentYear - 1) + ".csv", currentYear - 1);
    }
    // November-December - also process next year for cycles 1-10
    else if (month >= 11) {
      processOriginStartsForYear(htmlContent, bulletinDate,
          ORIGIN_CSV_BASE + (currentYear + 1) + ".csv", currentYear + 1);
    }
  }

  private static class FuelDateEntry {
    String date;
    LocalDate bulletinDate;

    FuelDateEntry(String date, LocalDate bulletinDate) {
      this.date = date;
      this.bulletinDate = bulletinDate;
    }
  }

  private static void updateFuelDate(Map<String, Map<String, FuelDateEntry>> csvData,
      String fuelCode,
      String cycle,
      String newDate,
      LocalDate bulletinDate) {
    if (!newDate.matches("\\d{2}/\\d{2}")) return;

    Map<String, FuelDateEntry> fuelData = csvData.computeIfAbsent(fuelCode, k -> new HashMap<>());
    FuelDateEntry existingEntry = fuelData.get(cycle);

    boolean shouldUpdate;
    if (existingEntry == null) {
      shouldUpdate = true;
    } else if (existingEntry.bulletinDate == null) {
      shouldUpdate = true;
    } else {
      shouldUpdate = bulletinDate.isAfter(existingEntry.bulletinDate);
    }

    if (shouldUpdate) {
      fuelData.put(cycle, new FuelDateEntry(newDate, bulletinDate));
      System.out.printf("%s cycle %s: %s -> %s [Bulletin: %s]%n",
          fuelCode, cycle,
          existingEntry != null ? existingEntry.date : "<empty>",
          newDate,
          bulletinDate);
    }
  }

  private static String cleanDate(String dateStr) {
    return dateStr.replace("*", "").trim();
  }

  private static void updateFuelDateSafely(Map<String, Map<String, FuelDateEntry>> csvData,
      Elements cells, String fuelCode, int cellIndex,
      String cycle, LocalDate bulletinDate) {
    try {
      if (cellIndex < cells.size()) {
        String dateStr = cleanDate(cells.get(cellIndex).text());
        updateFuelDate(csvData, fuelCode, cycle, dateStr, bulletinDate);
      }
    } catch (Exception e) {
      System.err.printf("Error updating %s cycle %s: %s%n",
          fuelCode, cycle, e.getMessage());
    }
  }
}