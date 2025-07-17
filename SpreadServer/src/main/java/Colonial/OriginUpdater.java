package Colonial;

import static Outlook.ExplorerParser.getAccessToken;

import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.*;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class OriginUpdater {
  private static final String ORIGIN_CSV_PATH = "data/Colonial/Origin/HTNOrigin.csv";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yy");

  public static void main(String[] args) {
    try {
      System.out.println("=== Colonial Origin Updater ===");
      String accessToken = getAccessToken();

      System.out.println("Updating origin data from most recent email...");
      LocalDate bulletindate = updateFromMostRecentOriginEmail(accessToken);
      System.out.println(bulletindate);

      System.out.println("=== Update completed ===");
    } catch (Exception e) {
      System.err.println("Error in OriginUpdater: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public static LocalDate updateFromMostRecentOriginEmail(String accessToken) throws IOException {
    Message message = fetchMostRecentOriginEmail(accessToken, "automatedreports@rioenergy.com");
    if (message == null) {
      System.out.println("No origin starts email found in the last 30 days");
      return null;
    }

    System.out.println("Processing origin starts email received on: " + message.receivedDateTime);

    // Use received date as bulletin date (formatted as MM/dd/yy)
    LocalDate bulletinDate = ColonialOrigin.extractBulletinDate(message.body.content);

    // Parse email content and update CSV
    processOriginStartsEmail(message.body.content);
    return bulletinDate;
  }

  public static void processOriginStartsEmail(String htmlContent) throws IOException {
    Path csvPath = Paths.get(ORIGIN_CSV_PATH);

    // 1. Read existing CSV or initialize if empty
    List<String> lines = Files.exists(csvPath) ?
        Files.readAllLines(csvPath, StandardCharsets.UTF_8) :
        List.of("Type,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69,70,71,72");

    // 2. Parse CSV into memory
    Map<String, Map<String, String>> csvData = new LinkedHashMap<>();
    String[] headers = lines.get(0).split(",");

    // Initialize all fuel types with empty maps
    csvData.put("A", new HashMap<>());
    csvData.put("D", new HashMap<>());
    csvData.put("F", new HashMap<>());
    csvData.put("62", new HashMap<>());

    // Populate with existing data
    for (int i = 1; i < lines.size(); i++) {
      String[] parts = lines.get(i).split(",", -1);
      if (parts.length > 0 && csvData.containsKey(parts[0])) {
        for (int j = 1; j < parts.length && j < headers.length; j++) {
          if (!parts[j].trim().isEmpty()) {
            csvData.get(parts[0]).put(headers[j], parts[j].trim());
          }
        }
      }
    }

    // 3. Parse email content with enhanced table detection
    Document doc = Jsoup.parse(htmlContent);
    boolean updatesFound = false;

    System.out.println("\n=== EMAIL PARSING DEBUG ===");
    System.out.println("Looking for data tables in email...");

    // Find all tables that might contain the data
    for (Element table : doc.select("table")) {
      // Skip small tables that can't be our data table
      if (table.select("tr").size() < 5) {
        continue;
      }

      System.out.println("\nProcessing potential data table...");

      // Look for header row that contains "Cycle" and "Location"
      Elements headerRows = table.select("tr:has(td:contains(Location)), tr:has(th:contains(Location))");
      if (headerRows.isEmpty()) {
        System.out.println("No header row with 'Location' found, skipping table");
        continue;
      }

      // Get column indices from header row
      Elements headerCells = headerRows.first().select("td, th");
      int locationCol = -1;
      int cycleCol = -1;
      int aCol = -1;  // Regular CBOB / L Grade
      int dCol = -1;  // Ultra Low Sulfur Diesel
      int fCol = -1;  // Conventional Regular
      int col62 = -1; // Regular RBOB

      for (int i = 0; i < headerCells.size(); i++) {
        String headerText = headerCells.get(i).text().trim();
        if (headerText.equalsIgnoreCase("Location")) {
          locationCol = i;
        } else if (headerText.equalsIgnoreCase("Cycle")) {
          cycleCol = i;
        } else if (headerText.contains("Regular CBOB") || headerText.contains("L Grade")) {
          aCol = i;
        } else if (headerText.contains("Ultra Low Sulfur Diesel")) {
          dCol = i;
        } else if (headerText.contains("Conventional Regular")) {
          fCol = i;
        } else if (headerText.contains("Regular RBOB")) {
          col62 = i;
        }
      }

      // Verify we found all required columns
      if (locationCol == -1 || cycleCol == -1 || aCol == -1 || dCol == -1 || fCol == -1 || col62 == -1) {
        System.out.println("Required columns not found in this table:");
        System.out.println("Location: " + locationCol + ", Cycle: " + cycleCol +
            ", A: " + aCol + ", D: " + dCol + ", F: " + fCol + ", 62: " + col62);
        continue;
      }

      System.out.println("Found valid data table structure:");
      System.out.println("Location column: " + locationCol);
      System.out.println("Cycle column: " + cycleCol);
      System.out.println("A (Regular CBOB) column: " + aCol);
      System.out.println("D (Diesel) column: " + dCol);
      System.out.println("F (Conventional) column: " + fCol);
      System.out.println("62 (RBOB) column: " + col62);

      // Process data rows
      for (Element row : table.select("tr")) {
        Elements cells = row.select("td");
        if (cells.size() <= Math.max(Math.max(cycleCol, aCol), Math.max(dCol, Math.max(fCol, col62)))) {
          continue; // Skip rows without enough cells
        }

        String location = cells.get(locationCol).text().trim();
        if ("HTN/PDA".equalsIgnoreCase(location)) {
          String cycle = cells.get(cycleCol).text().trim();
          System.out.println("\nProcessing HTN/PDA row for cycle " + cycle);

          // Get dates for each fuel type
          String aDate = cleanDate(cells.get(aCol).text());
          String dDate = cleanDate(cells.get(dCol).text());
          String fDate = cleanDate(cells.get(fCol).text());
          String date62 = cleanDate(cells.get(col62).text());

          System.out.println("Raw dates - A: '" + cells.get(aCol).text() + "' D: '" + cells.get(dCol).text() +
              "' F: '" + cells.get(fCol).text() + "' 62: '" + cells.get(col62).text() + "'");
          System.out.println("Cleaned dates - A: '" + aDate + "' D: '" + dDate + "' F: '" + fDate + "' 62: '" + date62 + "'");

          // Update each fuel type if date is valid
          if (aDate.matches("\\d{2}/\\d{2}")) {
            csvData.get("A").put(cycle, aDate);
            updatesFound = true;
            System.out.println("Updated A for cycle " + cycle + " to " + aDate);
          }
          if (dDate.matches("\\d{2}/\\d{2}")) {
            csvData.get("D").put(cycle, dDate);
            updatesFound = true;
            System.out.println("Updated D for cycle " + cycle + " to " + dDate);
          }
          if (fDate.matches("\\d{2}/\\d{2}")) {
            csvData.get("F").put(cycle, fDate);
            updatesFound = true;
            System.out.println("Updated F for cycle " + cycle + " to " + fDate);
          }
          if (date62.matches("\\d{2}/\\d{2}")) {
            csvData.get("62").put(cycle, date62);
            updatesFound = true;
            System.out.println("Updated 62 for cycle " + cycle + " to " + date62);
          }
        }
      }
    }

    if (!updatesFound) {
      System.out.println("No valid date updates found in email after processing all tables");
      return;
    }

    // 4. Write updated CSV
    List<String> updatedLines = new ArrayList<>();
    updatedLines.add(String.join(",", headers));

    // Write data in consistent order (A, D, F, 62)
    for (String fuelType : new String[]{"A", "D", "F", "62"}) {
      StringBuilder line = new StringBuilder(fuelType);
      for (int i = 1; i < headers.length; i++) {
        String date = csvData.get(fuelType).get(headers[i]);
        line.append(",").append(date != null ? date : "");
      }
      updatedLines.add(line.toString());
    }

    // Write to file
    Files.createDirectories(csvPath.getParent());
    Files.write(csvPath, updatedLines, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

    System.out.println("\nSuccessfully updated origin CSV with new dates");
  }


  private static String cleanDate(String dateStr) {
    return dateStr.replace("*", "").trim();
  }

  private static Message fetchMostRecentOriginEmail(String accessToken, String userPrincipalName) {
    try {
      IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
      GraphServiceClient<?> graphClient = GraphServiceClient.builder()
          .authenticationProvider(authProvider)
          .buildClient();

      // Look back 30 days for origin emails with specific subject
      OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
      String dateFilter = "receivedDateTime ge " + thirtyDaysAgo.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
      String subjectFilter = "contains(subject, 'Colonial - Origin')";
      String combinedFilter = dateFilter + " and " + subjectFilter;

      MessageCollectionPage messagesPage = graphClient.users(userPrincipalName)
          .messages()
          .buildRequest()
          .filter(combinedFilter)
          .select("subject,receivedDateTime,body")  // Only get needed fields
          .orderBy("receivedDateTime desc")
          .top(1)
          .get();

      if (!messagesPage.getCurrentPage().isEmpty()) {
        Message message = messagesPage.getCurrentPage().get(0);
        System.out.println("Found origin email with subject: " + message.subject +
            " received at: " + message.receivedDateTime);

        // Ensure we have the message body
        if (message.body == null || message.body.content == null) {
          message = graphClient.users(userPrincipalName)
              .messages(message.id)
              .buildRequest()
              .select("body")
              .get();
        }
        return message;
      } else {
        System.out.println("No origin emails found in the last 30 days with subject containing 'Colonial - Origin'");
      }
    } catch (Exception e) {
      System.err.println("Error fetching email: " + e.getMessage());
      e.printStackTrace();
    }
    return null;
  }
}