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

  private static final String ORIGIN_CSV_PATH = "data/Colonial/Origin/HTNOrigin.csv";
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
    requestOptions.add(new QueryOption("$top", "50"));

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

  public static void processOriginStartsEmail(String htmlContent, LocalDate bulletinDate) throws IOException {
    Path csvPath = Paths.get(ORIGIN_CSV_PATH);

    // 1. Read existing CSV or initialize if empty
    List<String> lines = Files.exists(csvPath) ?
        Files.readAllLines(csvPath, StandardCharsets.UTF_8) :
        List.of("Fuel," + String.join(",", Collections.nCopies(72, "")));

    // 2. Parse CSV structure with bulletin date tracking
    String[] headers = lines.get(0).split(",");
    Map<String, Map<String, FuelDateEntry>> csvData = new LinkedHashMap<>();


    for (int i = 1; i < lines.size(); i++) {
      String[] parts = lines.get(i).split(",", -1);
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
      for (Element row : table.select("tr")) {
        Elements cells = row.select("td");
        if (cells.size() >= 8 && cells.get(0).text().equals("HTN/PDA")) {
          String cycle = cells.get(1).text().trim();
          LocalDate currentLatest = latestBulletinDates.get(cycle);
          if (currentLatest == null || bulletinDate.isAfter(currentLatest)) {
            latestBulletinDates.put(cycle, bulletinDate);
          }
        }
      }
    }

    // Second pass: Process updates only from the most recent bulletins
    for (Element table : tables) {
      for (Element row : table.select("tr")) {
        Elements cells = row.select("td");
        if (cells.size() >= 8 && cells.get(0).text().equals("HTN/PDA")) {
          String cycle = cells.get(1).text().trim();

          // Only process if this email has the most recent bulletin for this cycle
          if (bulletinDate.equals(latestBulletinDates.get(cycle))) {
            updateFuelDate(csvData, "A", cycle, cleanDate(cells.get(2).text()), bulletinDate);
            updateFuelDate(csvData, "D", cycle, cleanDate(cells.get(4).text()), bulletinDate);
            updateFuelDate(csvData, "F", cycle, cleanDate(cells.get(5).text()), bulletinDate);
            updateFuelDate(csvData, "62", cycle, cleanDate(cells.get(7).text()), bulletinDate);
          }
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
    Files.createDirectories(csvPath.getParent());
    Files.write(csvPath, updatedLines, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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

    // Special logging for A cycle 10
    if (fuelCode.equals("A") && cycle.equals("10")) {
      System.out.println("\n=== DEBUG: A Cycle 10 Comparison ===");
      System.out.println("Existing date: " + (existingEntry != null ? existingEntry.date : "<empty>"));
      System.out.println("Existing bulletin date: " +
          (existingEntry != null && existingEntry.bulletinDate != null ?
              existingEntry.bulletinDate : "null/unknown"));
      System.out.println("New date: " + newDate);
      System.out.println("New bulletin date: " + bulletinDate);
    }

    boolean shouldUpdate;
    if (existingEntry == null) {
      shouldUpdate = true;
    } else if (existingEntry.bulletinDate == null) {
      shouldUpdate = true;
    } else {
      // Only compare dates if both are non-null
      shouldUpdate = bulletinDate.isAfter(existingEntry.bulletinDate);
    }

    if (shouldUpdate) {
      fuelData.put(cycle, new FuelDateEntry(newDate, bulletinDate));

      if (fuelCode.equals("A") && cycle.equals("10")) {
        System.out.println("UPDATE DECISION:");
        System.out.println(" - No existing entry: " + (existingEntry == null));
        System.out.println(" - Existing bulletin null: " + (existingEntry != null && existingEntry.bulletinDate == null));
        if (existingEntry != null && existingEntry.bulletinDate != null) {
          System.out.println(" - New bulletin is newer: " + bulletinDate.isAfter(existingEntry.bulletinDate));
        }
        System.out.println("UPDATE APPLIED to A cycle 10: " +
            (existingEntry != null ? existingEntry.date : "<empty>") +
            " -> " + newDate + " (Bulletin: " + bulletinDate + ")");
      }

      System.out.printf("%s cycle %s: %s -> %s [Bulletin: %s]%n",
          fuelCode, cycle,
          existingEntry != null ? existingEntry.date : "<empty>",
          newDate,
          bulletinDate);
    } else if (fuelCode.equals("A") && cycle.equals("10")) {
      System.out.println("NO UPDATE to A cycle 10 - Reason:");
      if (existingEntry != null) {
        System.out.println(" - Existing bulletin date: " + existingEntry.bulletinDate);
        System.out.println(" - New bulletin date: " + bulletinDate);
        if (existingEntry.bulletinDate != null) {
          System.out.println(" - New bulletin is older or equal: " +
              !bulletinDate.isAfter(existingEntry.bulletinDate));
        }
      }
    }
  }


  private static String cleanDate(String dateStr) {
    return dateStr.replace("*", "").trim();
  }
}