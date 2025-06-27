package Colonial;

import static Outlook.ExplorerParser.getAccessToken;

import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.*;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import Outlook.FusionCurveParser.SimpleAuthProvider;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ColonialTransitTime {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  public static class TransitEntry {
    public String origin, destination;
    public int cycle;
    public double gasTime, distillateTime;

    public LocalDate date;
  }

  public static void processTransitTimes() {
    try {
      String accessToken = getAccessToken();
      String userPrincipalName = "automatedreports@rioenergy.com";

      List<Message> messages = fetchTransitTimeEmails(accessToken, userPrincipalName);
      System.out.println("found " + messages.size() + " messages, going to process");
      processMessages(messages);

      System.out.println("Transit time data successfully processed and written to CSV files.");
    } catch (Exception e) {
      System.err.println("Error processing transit time data: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static List<Message> fetchTransitTimeEmails(String accessToken, String userPrincipalName) throws IOException {
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<?> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    List<Message> relevantMessages = new ArrayList<>();
    MessageCollectionPage messagesPage;
    MessageCollectionRequestBuilder nextPage = null;

    do {
      messagesPage = (nextPage == null)
          ? graphClient.users(userPrincipalName).messages()
          .buildRequest()
          .select("subject,receivedDateTime,body")
          .orderBy("receivedDateTime desc")
          .top(50)
          .get()
          : nextPage.buildRequest().get();

      for (Message message : messagesPage.getCurrentPage()) {
        if (message.subject != null && message.subject.toLowerCase().contains("colonial - transit times")) {
          relevantMessages.add(message);
        }
      }

      nextPage = messagesPage.getNextPage();
    } while (nextPage != null);

    return relevantMessages;
  }

  private static void processMessages(List<Message> messages) throws IOException {
    // Sort messages by date (oldest first)
    messages.sort(Comparator.comparing(m -> m.receivedDateTime));

    for (Message message : messages) {
      try {
        String body = message.body.content;
        List<TransitEntry> entries = parseTransitTimeBody(body);
        processEntries(entries);
      } catch (Exception e) {
        System.err.println("Error processing message: " + e.getMessage());
      }
    }
  }

  private static List<TransitEntry> parseTransitTimeBody(String body) {
    System.out.println("========== Starting to parse email body ==========");
    List<TransitEntry> entries = new ArrayList<>();

    // Log initial body (first 500 chars to avoid flooding console)
    System.out.println("Initial body preview:\n" + body.substring(0, Math.min(500, body.length())) + "...");

    // Convert HTML to plain text
    String plainText = body.replaceAll("<[^>]+>", " ")
        .replaceAll("&nbsp;", " ")
        .replaceAll("\\s+", " ")
        .trim();
    System.out.println("---------- Plain text version ----------");
    System.out.println(plainText.substring(0, Math.min(500, plainText.length())) + "...");

    // Extract date
    LocalDate entryDate;
    try {
      System.out.println("Attempting to extract date...");
      entryDate = extractDateFromPlainText(plainText);
      System.out.println("Successfully parsed date: " + entryDate);
    } catch (Exception e) {
      System.err.println("FAILED to parse date: " + e.getMessage());
      return entries;
    }

    // Find table section
    System.out.println("Looking for transit times table...");
    String tableStartMarker = "Average scheduled transit times between selected locations:";
    int tableStart = plainText.indexOf(tableStartMarker);

    if (tableStart == -1) {
      System.err.println("ERROR: Could not find table start marker: " + tableStartMarker);
      return entries;
    }
    System.out.println("Found table at position: " + tableStart);

    // Extract table data
    String tableData = plainText.substring(tableStart + tableStartMarker.length());
    System.out.println("---------- Table data ----------");
    System.out.println(tableData.substring(0, Math.min(500, tableData.length())) + "...");

    // Split into lines and process
    String[] lines = tableData.split("From To Cycle Gas Distillates Days Hours Days Hours")[1]
        .split("The information contained")[0]
        .trim()
        .split("\\s+");

    System.out.println("Found " + lines.length + " data elements in table");
    System.out.println("First 20 elements: " + Arrays.toString(Arrays.copyOfRange(lines, 0, 20)));

    // Process entries (7 fields per row)
    System.out.println("Processing entries...");
    for (int i = 0; i + 6 < lines.length; i += 7) {
      try {
        System.out.println("Processing entry starting at index " + i);
        String[] currentRow = Arrays.copyOfRange(lines, i, i+7);
        System.out.println("Row data: " + Arrays.toString(currentRow));

        TransitEntry entry = new TransitEntry();
        entry.origin = currentRow[0];
        entry.destination = currentRow[1];
        entry.cycle = Integer.parseInt(currentRow[2]);

        int gasDays = Integer.parseInt(currentRow[3]);
        int gasHours = Integer.parseInt(currentRow[4]);
        entry.gasTime = gasDays + gasHours / 24.0;

        int distDays = Integer.parseInt(currentRow[5]);
        int distHours = Integer.parseInt(currentRow[6]);
        entry.distillateTime = distDays + distHours / 24.0;

        entry.date = entryDate;
        entries.add(entry);

        System.out.println("Successfully added entry: " +
            entry.origin + "-" + entry.destination +
            " cycle " + entry.cycle +
            " gas:" + entry.gasTime +
            " distillate:" + entry.distillateTime);

      } catch (NumberFormatException e) {
        System.err.println("Skipping malformed row at index " + i + ": " + e.getMessage());
      } catch (ArrayIndexOutOfBoundsException e) {
        System.err.println("Incomplete row at index " + i);
        break;
      }
    }

    System.out.println("========== Finished parsing ==========");
    System.out.println("Total entries parsed: " + entries.size());
    return entries;
  }

  private static LocalDate extractDateFromPlainText(String plainText) {
    System.out.println("Extracting date from text: " + plainText.substring(0, 100) + "...");

    // Look for multiple possible date patterns
    Pattern[] patterns = {
        Pattern.compile("Date:\\s*(\\d{2}/\\d{2}/\\d{2})"),  // Date: MM/dd/yy
        Pattern.compile("(\\d{2}/\\d{2}/\\d{2})\\s+\\d{2}:\\d{2}"),  // MM/dd/yy HH:mm
        Pattern.compile("Date:\\s*(\\w+\\s+\\d{1,2},\\s+\\d{4})")  // Date: Month dd, yyyy
    };

    for (Pattern p : patterns) {
      Matcher m = p.matcher(plainText);
      if (m.find()) {
        String dateStr = m.group(1);
        System.out.println("Found date string: " + dateStr);

        try {
          // Try MM/dd/yy first
          LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MM/dd/yy"));
          System.out.println("Parsed as MM/dd/yy: " + date);
          return date;
        } catch (DateTimeParseException e1) {
          try {
            // Try MMMM d, yyyy (Month day, year)
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("MMMM d, yyyy"));
            System.out.println("Parsed as MMMM d, yyyy: " + date);
            return date;
          } catch (DateTimeParseException e2) {
            continue;
          }
        }
      }
    }
    throw new RuntimeException("No valid date pattern found in: " + plainText.substring(0, 100) + "...");
  }

  private static void processEntries(List<TransitEntry> entries) throws IOException {
    System.out.println("========== Processing Entries ==========");
    System.out.println("Total entries to process: " + entries.size());

    // Counters for tracking
    int htnGbjCount = 0;
    int gbjLnjCount = 0;
    int otherCount = 0;

    for (TransitEntry entry : entries) {
      String route = entry.origin + "-" + entry.destination;
      System.out.println("Processing route: " + route + " cycle " + entry.cycle);

      if (entry.origin.equals("HTN") && entry.destination.equals("GBJ")) {
        htnGbjCount++;
        System.out.println("  HTN-GBJ entry found - updating CSVs");
        System.out.println("  Gas: " + entry.gasTime + " | Distillate: " + entry.distillateTime);

        // Update HTN-GBJ CSVs
        updateTransitCsv("data/Colonial/Transit/HTNGBJ-GAS.csv", entry.date, entry.cycle, entry.gasTime);
        updateTransitCsv("data/Colonial/Transit/HTNGBJ-DISTILLATES.csv", entry.date, entry.cycle, entry.distillateTime);
      }
      else if (entry.origin.equals("GBJ") && entry.destination.equals("LNJ")) {
        gbjLnjCount++;
        System.out.println("  GBJ-LNJ entry found - updating CSVs");
        System.out.println("  Gas: " + entry.gasTime + " | Distillate: " + entry.distillateTime);

        // Update GBJ-LNJ CSVs
        updateTransitCsv("data/Colonial/Transit/GBJLNJ-GAS.csv", entry.date, entry.cycle, entry.gasTime);
        updateTransitCsv("data/Colonial/Transit/GBJLNJ-DISTILLATES.csv", entry.date, entry.cycle, entry.distillateTime);
      }
      else {
        otherCount++;
        System.out.println("  Skipping non-target route: " + route);
      }
    }

    System.out.println("========== Processing Summary ==========");
    System.out.println("HTN-GBJ entries processed: " + htnGbjCount);
    System.out.println("GBJ-LNJ entries processed: " + gbjLnjCount);
    System.out.println("Other routes skipped: " + otherCount);

    // Verify at least some HTN-GBJ entries were found
    if (htnGbjCount == 0 && entries.size() > 0) {
      System.err.println("WARNING: No HTN-GBJ entries found despite having " + entries.size() + " total entries");
      System.err.println("First few entries origins/destinations:");
      entries.stream()
          .limit(5)
          .forEach(e -> System.err.println("  " + e.origin + "-" + e.destination));
    }
  }

  private static void updateTransitCsv(String filePath, LocalDate date, int cycle, double value) throws IOException {
    System.out.println("===== Updating " + filePath + " =====");

    Path path = Paths.get(filePath);
    List<String> lines = new ArrayList<>();

    // Initialize with header if file doesn't exist
    if (!Files.exists(path)) {
      System.out.println("  Creating new file with header");
      String[] header = new String[73];
      header[0] = "Date";
      for (int i = 1; i <= 72; i++) header[i] = String.valueOf(i);
      lines.add(String.join(",", header));
    } else {
      // Read existing file and skip empty/malformed rows
      System.out.println("  Reading existing file, filtering malformed rows");
      lines = Files.lines(path)
          .filter(line -> !line.trim().isEmpty())
          .filter(line -> {
            if (line.startsWith("Date,")) return true; // Keep header
            try {
              LocalDate.parse(line.split(",", -1)[0]);
              return true;
            } catch (Exception e) {
              System.err.println("  WARNING: Skipping malformed row: " + line);
              return false;
            }
          })
          .collect(Collectors.toList());
    }

    // Find or create row for our date
    boolean updated = false;
    for (int i = 1; i < lines.size(); i++) {
      String[] row = lines.get(i).split(",", -1);
      LocalDate rowDate = LocalDate.parse(row[0]);

      if (rowDate.isEqual(date)) {
        System.out.println("  Updating existing row for date " + date);
        row[cycle] = String.format("%.2f", value);
        lines.set(i, String.join(",", row));
        updated = true;
        break;
      } else if (rowDate.isAfter(date)) {
        System.out.println("  Inserting new row before row " + i);
        lines.add(i, createCsvRow(date, cycle, value));
        updated = true;
        break;
      }
    }

    if (!updated) {
      System.out.println("  Adding new row at end");
      lines.add(createCsvRow(date, cycle, value));
    }

    // Write the cleaned file
    System.out.println("  Writing " + lines.size() + " clean rows to file");
    Files.write(path, lines, StandardOpenOption.TRUNCATE_EXISTING);
    System.out.println("  Successfully updated " + filePath);

    // Verify write
    List<String> writtenLines = Files.readAllLines(path);
    System.out.println("  Verification - file now has " + writtenLines.size() + " rows");
    System.out.println("  Last row: " + (writtenLines.isEmpty() ? "EMPTY" : writtenLines.get(writtenLines.size()-1)));
  }

  private static String createCsvRow(LocalDate date, int cycle, double value) {
    String[] newRow = new String[73];
    newRow[0] = date.toString();
    Arrays.fill(newRow, 1, 73, "");
    newRow[cycle] = String.format("%.2f", value);
    return String.join(",", newRow);
  }

  public static void main(String[] args) {
    processTransitTimes();
  }
}