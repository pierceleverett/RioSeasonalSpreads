package Colonial;

import static Outlook.ExplorerParser.getAccessToken;

import com.microsoft.graph.models.*;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.*;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
import javax.swing.text.html.Option;

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

  public static List<Message> fetchTransitTimeEmails(String accessToken, String userPrincipalName) throws IOException {
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<?> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    List<Message> relevantMessages = new ArrayList<>();
    MessageCollectionPage messagesPage;
    MessageCollectionRequestBuilder nextPage = null;

    // Request additional properties including body content
    LinkedList<QueryOption> requestOptions = new LinkedList<>();
    requestOptions.add(new QueryOption("$select", "subject,receivedDateTime,body,bodyPreview"));
    requestOptions.add(new QueryOption("$top", "50"));

    do {
      messagesPage = (nextPage == null)
          ? graphClient.users(userPrincipalName).messages()
          .buildRequest(requestOptions)
          .orderBy("receivedDateTime desc")
          .get()
          : nextPage.buildRequest().get();

      for (Message message : messagesPage.getCurrentPage()) {
        if (message.subject != null && message.subject.toLowerCase().contains("colonial - transit times")) {
          // Ensure we have the full body content
          if (message.body == null || message.body.content == null) {
            // If body is null, fetch the full message
            Message fullMessage = graphClient.users(userPrincipalName)
                .messages(message.id)
                .buildRequest()
                .select("body")
                .get();
            message.body = fullMessage.body;
          }
          relevantMessages.add(message);
        }
      }

      nextPage = messagesPage.getNextPage();
    } while (nextPage != null);

    return relevantMessages;
  }

  public static void processMessages(List<Message> messages) throws IOException {
    // Sort messages by date (oldest first)
    messages.sort(Comparator.comparing(m -> m.receivedDateTime));

    for (Message message : messages) {
      try {
        System.out.println("Processing message received on: " + message.receivedDateTime);

        // Verify we have message body content
        if (message.body == null || message.body.content == null || message.body.content.isEmpty()) {
          System.err.println("WARNING: Empty message body for message with subject: " + message.subject);
          continue;
        }

        System.out.println("Message body length: " + message.body.content.length());
        System.out.println("First 200 chars of body:\n" + message.body.content.substring(0, Math.min(200, message.body.content.length())));

        List<TransitEntry> entries = parseTransitTimeBody(message.body.content);
        System.out.println("Found " + entries.size() + " entries in this message");

        if (!entries.isEmpty()) {
          processEntries(entries);
        } else {
          System.err.println("WARNING: No entries parsed from message with subject: " + message.subject);
          // Print more of the body for debugging
          System.err.println("First 500 chars of body:\n" + message.body.content.substring(0, Math.min(500, message.body.content.length())));
        }
      } catch (Exception e) {
        System.err.println("Error processing message: " + e.getMessage());
        e.printStackTrace();
      }
    }
  }


  public static List<TransitEntry> parseTransitTimeBody(String body) {
    System.out.println("========== Starting to parse email body ==========");
    List<TransitEntry> entries = new ArrayList<>();

    if (body == null || body.isEmpty()) {
      System.err.println("ERROR: Empty body provided for parsing");
      return entries;
    }

    // Use jsoup to convert HTML to plain text
    Document doc = Jsoup.parse(body);
    String plainText = doc.text().replaceAll("\\s+", " ").trim();

    System.out.println("Plain text length: " + plainText.length());
    System.out.println("First 200 chars:\n" + plainText.substring(0, Math.min(200, plainText.length())));

    // Extract date
    LocalDate entryDate;
    try {
      entryDate = extractDateFromPlainText(plainText);
      System.out.println("Parsed date: " + entryDate);
    } catch (Exception e) {
      System.err.println("FAILED to parse date: " + e.getMessage());
      return entries;
    }

    // Find table section
    String tableStartMarker = "Average scheduled transit times between selected locations:";
    int tableStart = plainText.indexOf(tableStartMarker);
    if (tableStart == -1) {
      System.err.println("ERROR: Could not find table start marker");
      return entries;
    }

    String tableData = plainText.substring(tableStart + tableStartMarker.length())
        .split("The information contained")[0]
        .trim();

    String[] lines = tableData.split("\\r?\\n|(?<=\\d) (?=\\D)");

    int headerRowIndex = -1;
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].contains("From") && lines[i].contains("To") && lines[i].contains("Cycle")) {
        headerRowIndex = i;
        break;
      }
    }

    if (headerRowIndex == -1) {
      System.err.println("ERROR: Could not find table header row");
      return entries;
    }

    for (int i = headerRowIndex + 1; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty()) continue;

      String[] cells = line.split("\\s+");
      if (cells.length < 7) continue;

      try {
        if (!(cells[0].equals("HTN") && cells[1].equals("GBJ")) &&
            !(cells[0].equals("GBJ") && cells[1].equals("LNJ"))) {
          continue;
        }

        TransitEntry entry = new TransitEntry();
        entry.origin = cells[0];
        entry.destination = cells[1];
        entry.cycle = Integer.parseInt(cells[2]);
        entry.date = entryDate;

        entry.gasTime = (cells[3].isEmpty() || cells[4].isEmpty()) ? 0 :
            Integer.parseInt(cells[3]) + Integer.parseInt(cells[4]) / 24.0;

        entry.distillateTime = (cells[5].isEmpty() || cells[6].isEmpty()) ? 0 :
            Integer.parseInt(cells[5]) + Integer.parseInt(cells[6]) / 24.0;

        entries.add(entry);
      } catch (Exception e) {
        System.err.println("Error processing row: " + line);
        e.printStackTrace();
      }
    }

    System.out.println("========== Finished parsing ==========");
    System.out.println("Total entries parsed: " + entries.size());
    return entries;
  }


  public static LocalDate extractDateFromPlainText(String plainText) {
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

  public static void processEntries(List<TransitEntry> entries) throws IOException {
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

        // Update HTN-GBJ CSVs using updateTransitCsv
        updateTransitCsv("data/Colonial/Transit/HTNGBJ-GAS.csv", entry.date, entry.cycle, entry.gasTime);
        updateTransitCsv("data/Colonial/Transit/HTNGBJ-DISTILLATES.csv", entry.date, entry.cycle, entry.distillateTime);
      }
      else if (entry.origin.equals("GBJ") && entry.destination.equals("LNJ")) {
        gbjLnjCount++;
        System.out.println("  GBJ-LNJ entry found - updating CSVs");
        System.out.println("  Gas: " + entry.gasTime + " | Distillate: " + entry.distillateTime);

        // Update GBJ-LNJ CSVs using updateTransitCsv
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

    if (htnGbjCount == 0 && entries.size() > 0) {
      System.err.println("WARNING: No HTN-GBJ entries found despite having " + entries.size() + " total entries");
      System.err.println("First few entries origins/destinations:");
      entries.stream()
          .limit(5)
          .forEach(e -> System.err.println("  " + e.origin + "-" + e.destination));
    }
  }

  public static void updateTransitCsv(String filePath, LocalDate date, int cycle, double value) throws IOException {
    System.out.println("===== Updating " + filePath + " =====");
    System.out.println("Date: " + date + ", Cycle: " + cycle + ", Value: " + value);

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
      System.out.println("  Reading existing file");
      lines = Files.readAllLines(path);
    }

    // Find or create row for our date
    boolean updated = false;
    for (int i = 1; i < lines.size(); i++) {
      String[] row = lines.get(i).split(",", -1);
      try {
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
      } catch (DateTimeParseException e) {
        System.err.println("  WARNING: Skipping malformed row at line " + i);
        continue;
      }
    }

    if (!updated) {
      System.out.println("  Adding new row at end");
      lines.add(createCsvRow(date, cycle, value));
    }

    // Sort by date (keeping header first)
    lines.sort((line1, line2) -> {
      if (line1.startsWith("Date,")) return -1;
      if (line2.startsWith("Date,")) return 1;
      try {
        return LocalDate.parse(line1.split(",", -1)[0])
            .compareTo(LocalDate.parse(line2.split(",", -1)[0]));
      } catch (Exception e) {
        return 0;
      }
    });

    // Write the file
    System.out.println("  Writing " + lines.size() + " rows to file");
    Files.write(path, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    System.out.println("  Successfully updated " + filePath);

    // Verification
    List<String> writtenLines = Files.readAllLines(path);
    System.out.println("  Verification - file now has " + writtenLines.size() + " rows");
    if (!writtenLines.isEmpty()) {
      System.out.println("  First row: " + writtenLines.get(0));
      System.out.println("  Last row: " + writtenLines.get(writtenLines.size()-1));
    }
  }

  public static String createCsvRow(LocalDate date, int cycle, double value) {
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