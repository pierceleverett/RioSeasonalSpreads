package Colonial;

import static Outlook.ExplorerParser.getAccessToken;
import com.microsoft.graph.models.*;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.*;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class MinColonialFungible {
  private static final String GBJ_CSV_PATH = "data/Colonial/Fungible/GBJall.csv";
  private static final String PROCESSED_DATES_PATH = "data/Colonial/Fungible/all_processed_dates.txt";
  private static final String USER_PRINCIPAL_NAME = "automatedreports@rioenergy.com";
  private static final String EMAIL_SUBJECT_FILTER = "colonial - fungible deliveries";

  public static void main(String[] args) {
    try {
      System.out.println("Starting fungible delivery data processing");
      String accessToken = getAccessToken();
      List<Message> messages = fetchFungibleEmails(accessToken, USER_PRINCIPAL_NAME);
      processAllMessages(messages);
      System.out.println("Fungible delivery data successfully processed");
    } catch (Exception e) {
      System.err.println("Error processing fungible delivery data: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public static List<Message> fetchFungibleEmails(String accessToken, String userPrincipalName) throws IOException {
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<?> graphClient = GraphServiceClient.builder().authenticationProvider(authProvider).buildClient();
    List<Message> relevantMessages = new ArrayList<>();
    LinkedList<QueryOption> requestOptions = new LinkedList<>();
    requestOptions.add(new QueryOption("$select", "subject,receivedDateTime,body"));
    requestOptions.add(new QueryOption("$top", "300")); // Increased to get more emails

    MessageCollectionPage messagesPage;
    MessageCollectionRequestBuilder nextPage = null;

    do {
      messagesPage = (nextPage == null)
          ? graphClient.users(userPrincipalName).messages().buildRequest(requestOptions).orderBy("receivedDateTime desc").get()
          : nextPage.buildRequest().get();

      for (Message message : messagesPage.getCurrentPage()) {
        if (message.subject != null && message.subject.toLowerCase().contains(EMAIL_SUBJECT_FILTER)) {
          if (message.body == null || message.body.content == null) {
            Message fullMessage = graphClient.users(userPrincipalName).messages(message.id).buildRequest().select("body").get();
            message.body = fullMessage.body;
          }
          relevantMessages.add(message);
        }
      }
      nextPage = messagesPage.getNextPage();
    } while (nextPage != null);

    return relevantMessages;
  }

  public static void processAllMessages(List<Message> messages) throws IOException {
    // Read existing data
    Map<String, Map<String, List<String>>> existingGbjData = readExistingCsv(GBJ_CSV_PATH);
    Set<String> processedDates = readProcessedDates();

    // Sort messages chronologically
    messages.sort(Comparator.comparing(m -> m.receivedDateTime));

    // Process all messages
    for (Message message : messages) {
      try {
        if (message.body == null || message.body.content == null) continue;

        // Extract and store the bulletin date
        Document doc = Jsoup.parse(message.body.content);
        String plainText = doc.text().replaceAll("\\s+", " ").trim();
        LocalDate emailDate = extractDateFromPlainText(plainText);
        String formattedDate = emailDate.format(DateTimeFormatter.ofPattern("MM/dd/yy"));

        // Skip if already processed
        if (processedDates.contains(formattedDate)) {
          continue;
        }

        // Parse and merge with existing data
        parseFungibleBody(message.body.content, existingGbjData);
        processedDates.add(formattedDate);

      } catch (Exception e) {
        System.err.println("Failed to process message with subject: " + message.subject);
        e.printStackTrace();
      }
    }

    // Write the merged data to CSVs
    writeCompleteCsv(GBJ_CSV_PATH, existingGbjData);

    // Save processed dates
    saveProcessedDates(processedDates);
  }

  private static void saveProcessedDates(Set<String> dates) throws IOException {
    Path path = Paths.get(PROCESSED_DATES_PATH);
    Path parentDir = path.getParent();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yy");

    // Create directory if it doesn't exist
    if (!Files.exists(parentDir)) {
      Files.createDirectories(parentDir);
    }

    // Read existing dates if file exists
    Set<String> allDates = new TreeSet<>();
    if (Files.exists(path)) {
      allDates.addAll(Files.readAllLines(path));
    }

    // Add new dates
    allDates.addAll(dates);

    // Write all dates back to file
    List<String> dateList = allDates.stream()
        .map(date -> LocalDate.parse(date, formatter))
        .sorted()
        .map(date -> date.format(formatter))
        .collect(Collectors.toList());

    Files.write(path, dateList,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING);

    System.out.println("Saved " + dateList.size() + " processed dates to " + PROCESSED_DATES_PATH);
  }

  public static void parseFungibleBody(String htmlContent,
      Map<String, Map<String, List<String>>> gbjData) {
    System.out.println("\n===== Starting to parse fungible body =====");

    Document doc = Jsoup.parse(htmlContent);
    String plainText = doc.text().replaceAll("\\s+", " ").trim();
    System.out.println("Plain text preview: " + plainText.substring(0, Math.min(100, plainText.length())) + "...");

    LocalDate emailDate;
    try {
      emailDate = extractDateFromPlainText(plainText);
      System.out.println("Extracted email date: " + emailDate);
    } catch (Exception e) {
      System.err.println("Failed to parse date: " + e.getMessage());
      return;
    }

    String bulletinDate = emailDate.format(DateTimeFormatter.ofPattern("MM/dd"));
    System.out.println("Formatted bulletin date: " + bulletinDate);

    // Find the relevant table
    System.out.println("\nSearching for Greensboro table...");
    Element table = null;
    Elements tables = doc.select("table");
    System.out.println("Found " + tables.size() + " tables in email");

    for (int i = 0; i < tables.size(); i++) {
      Element t = tables.get(i);
      System.out.println("\nChecking table #" + (i+1));

      Elements headers = t.select("tr:first-child th, tr:first-child td");
      System.out.println("Table has " + headers.size() + " headers");

      for (int j = 0; j < headers.size(); j++) {
        Element header = headers.get(j);
        String text = header.text().toLowerCase();
        System.out.println("  Header[" + j + "]: '" + text + "'");

        if (text.contains("greensboro")) {
          table = t;
          System.out.println("  FOUND Greensboro in header[" + j + "]");
          break;
        }
      }
      if (table != null) break;
    }

    if (table == null) {
      System.err.println("ERROR: No relevant table found with Greensboro column.");
      return;
    }

    // Find Greensboro column index
    System.out.println("\nDetermining Greensboro column index...");
    Elements headerCells = table.select("tr:first-child th, tr:first-child td");
    int greensboroCol = -1;

    for (int i = 0; i < headerCells.size(); i++) {
      String header = headerCells.get(i).text().trim().toLowerCase();
      System.out.println("  Header[" + i + "]: '" + header + "'");

      if (header.contains("greensboro")) {
        greensboroCol = i;
        System.out.println("  Greensboro column found at index: " + greensboroCol);
        break;
      }
    }

    if (greensboroCol == -1) {
      System.err.println("ERROR: Greensboro column not found in table headers.");
      return;
    }

    // Process each data row
    System.out.println("\nProcessing table rows...");
    Elements rows = table.select("tr:gt(0)");
    System.out.println("Found " + rows.size() + " data rows");

    int processedRows = 0;
    for (int rowNum = 0; rowNum < rows.size(); rowNum++) {
      Element row = rows.get(rowNum);
      Elements cells = row.select("td");

      System.out.println("\nRow #" + (rowNum+1) + " has " + cells.size() + " cells");

      if (cells.size() <= greensboroCol) {
        System.out.println("  SKIPPING - Not enough columns (" + cells.size() + ") for Greensboro col (" + greensboroCol + ")");
        continue;
      }

      // Get fuel type (first column)
      String rawFuel = cells.get(0).text().trim();
      System.out.println("  Raw fuel: '" + rawFuel + "'");

      if (rawFuel.isEmpty()) {
        System.out.println("  SKIPPING - Empty fuel type");
        continue;
      }

      // Standardize fuel type
      String fuel;
      if (rawFuel.startsWith("A") || rawFuel.startsWith("D") ||
          rawFuel.startsWith("F") || rawFuel.startsWith("H") ||
          rawFuel.startsWith("M") || rawFuel.startsWith("V")) {
        fuel = rawFuel.substring(0, 1);
        System.out.println("  Standardized fuel to: '" + fuel + "'");
      } else {
        fuel = rawFuel;
        System.out.println("  Using raw fuel as-is: '" + fuel + "'");
      }

      // Get cycle (second column)
      String rawCycle = cells.get(1).text().trim();
      System.out.println("  Raw cycle: '" + rawCycle + "'");

      if (rawCycle.length() != 3) {
        System.out.println("  SKIPPING - Invalid cycle length (expected 3, got " + rawCycle.length() + ")");
        continue;
      }

      String cycle = rawCycle.substring(0, 2);
      System.out.println("  Using cycle: '" + cycle + "'");

      // Get Greensboro date (greensboroCol column)
      String greensboroDate = cells.get(greensboroCol).text().trim();
      System.out.println("  Greensboro date: '" + greensboroDate + "'");

      if (greensboroDate.isEmpty()) {
        System.out.println("  SKIPPING - Empty Greensboro date");
        continue;
      }

      // Store the data
      System.out.println("  STORING: Fuel=" + fuel + ", Cycle=" + cycle + ", Date=" + greensboroDate);

      gbjData.computeIfAbsent(fuel, k -> new HashMap<>())
          .computeIfAbsent(cycle, k -> new ArrayList<>())
          .add(greensboroDate);

      processedRows++;
    }

    System.out.println("\n===== Finished parsing =====");
    System.out.println("Successfully processed " + processedRows + " rows");
    System.out.println("Current GBJ data size: " + gbjData.size() + " fuel types");
  }

  public static LocalDate extractDateFromPlainText(String plainText) {
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

  private static void writeCompleteCsv(String csvPath, Map<String, Map<String, List<String>>> deliveryData) throws IOException {
    // Determine all possible cycles (01-72)
    Set<String> allCycles = new TreeSet<>();
    for (int i = 1; i <= 72; i++) {
      allCycles.add(String.format("%02d", i));
    }

    // Determine all fuel types in order
    List<String> fuelTypes = new ArrayList<>(deliveryData.keySet());
    fuelTypes.sort(Comparator.naturalOrder());

    // Prepare header row
    List<String> lines = new ArrayList<>();
    StringBuilder header = new StringBuilder("Type");
    for (String cycle : allCycles) {
      header.append(",").append(cycle);
    }
    lines.add(header.toString());

    // Prepare data rows for individual fuel types
    for (String fuel : fuelTypes) {
      StringBuilder row = new StringBuilder(fuel);
      Map<String, List<String>> cycleData = deliveryData.get(fuel);

      for (String cycle : allCycles) {
        row.append(",");
        if (cycleData != null && cycleData.containsKey(cycle)) {
          List<String> dates = cycleData.get(cycle);
          if (!dates.isEmpty()) {
            row.append(String.join(";", dates));
          }
        }
      }
      lines.add(row.toString());
    }

    // Write to file
    Files.write(Paths.get(csvPath), lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    System.out.println("Updated " + csvPath + " with " + (fuelTypes.size() + 3) + " fuel types (including aggregates)");
  }

  private static Map<String, Map<String, List<String>>> readExistingCsv(String csvPath) throws IOException {
    Map<String, Map<String, List<String>>> existingData = new HashMap<>();

    if (!Files.exists(Paths.get(csvPath))) {
      return existingData;
    }

    List<String> lines = Files.readAllLines(Paths.get(csvPath));
    if (lines.isEmpty()) return existingData;

    // Skip header
    for (int i = 1; i < lines.size(); i++) {
      String[] parts = lines.get(i).split(",");
      if (parts.length < 2) continue;

      String fuelType = parts[0];
      Map<String, List<String>> cycleData = new HashMap<>();

      // Cycles are columns 1-72 (01-72)
      for (int cycle = 1; cycle <= 72 && cycle < parts.length; cycle++) {
        String cycleStr = String.format("%02d", cycle);
        if (!parts[cycle].isEmpty()) {
          List<String> dates = new ArrayList<>(Arrays.asList(parts[cycle].split(";")));
          cycleData.put(cycleStr, dates);
        }
      }

      if (!cycleData.isEmpty()) {
        existingData.put(fuelType, cycleData);
      }
    }

    return existingData;
  }

  private static Set<String> readProcessedDates() throws IOException {
    Set<String> dates = new HashSet<>();
    Path path = Paths.get(PROCESSED_DATES_PATH);

    if (!Files.exists(path)) {
      return dates;
    }

    dates.addAll(Files.readAllLines(path));
    return dates;
  }
}