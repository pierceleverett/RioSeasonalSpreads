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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ColonialFungible {
  private static final Logger logger = LoggerFactory.getLogger(ColonialFungible.class);
  private static final String GBJ_CSV_BASE = "data/Colonial/Fungible/GBJ";
  private static final String LNJ_CSV_BASE = "data/Colonial/Fungible/LNJ";
  private static final String PROCESSED_DATES_PATH = "data/Colonial/Fungible/processed_dates.txt";
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
    requestOptions.add(new QueryOption("$top", "500"));

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
    Set<String> processedDates = readProcessedDates();
    messages.sort(Comparator.comparing(m -> m.receivedDateTime));

    for (Message message : messages) {
      try {
        if (message.body == null || message.body.content == null) continue;

        Document doc = Jsoup.parse(message.body.content);
        String plainText = doc.text().replaceAll("\\s+", " ").trim();
        LocalDate emailDate = extractDateFromPlainText(plainText);
        String formattedDate = emailDate.format(DateTimeFormatter.ofPattern("MM/dd/yy"));

        if (processedDates.contains(formattedDate)) {
          continue;
        }

        // Initialize data structures for current and adjacent years
        int currentYear = emailDate.getYear();
        Map<String, Map<String, List<String>>> gbjCurrent = readExistingCsv(GBJ_CSV_BASE, currentYear);
        Map<String, Map<String, List<String>>> lnjCurrent = readExistingCsv(LNJ_CSV_BASE, currentYear);

        Map<String, Map<String, List<String>>> gbjAdjacent = null;
        Map<String, Map<String, List<String>>> lnjAdjacent = null;

        // Only load adjacent year if we're in transition period
        if (isTransitionPeriod(emailDate)) {
          int adjacentYear = emailDate.getMonthValue() >= 11 ? currentYear + 1 : currentYear - 1;
          gbjAdjacent = readExistingCsv(GBJ_CSV_BASE, adjacentYear);
          lnjAdjacent = readExistingCsv(LNJ_CSV_BASE, adjacentYear);
        }

        // Parse the message body
        parseFungibleBody(message.body.content, emailDate, gbjCurrent, lnjCurrent, gbjAdjacent, lnjAdjacent);

        // Write all affected files
        writeCompleteCsv(GBJ_CSV_BASE, currentYear, gbjCurrent);
        writeCompleteCsv(LNJ_CSV_BASE, currentYear, lnjCurrent);

        if (gbjAdjacent != null) {
          int adjacentYear = emailDate.getMonthValue() >= 11 ? currentYear + 1 : currentYear - 1;
          writeCompleteCsv(GBJ_CSV_BASE, adjacentYear, gbjAdjacent);
          writeCompleteCsv(LNJ_CSV_BASE, adjacentYear, lnjAdjacent);
        }

        processedDates.add(formattedDate);
      } catch (Exception e) {
        System.err.println("Failed to process message with subject: " + message.subject);
        e.printStackTrace();
      }
    }

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

  static boolean isTransitionPeriod(LocalDate date) {
    int month = date.getMonthValue();
    return month >= 11 || month <= 2;
  }

  public static void parseFungibleBody(String htmlContent, LocalDate emailDate,
      Map<String, Map<String, List<String>>> gbjCurrent,
      Map<String, Map<String, List<String>>> lnjCurrent,
      Map<String, Map<String, List<String>>> gbjAdjacent,
      Map<String, Map<String, List<String>>> lnjAdjacent) {
    logger.info("Parsing fungible body for date {}", emailDate);
    Document doc = Jsoup.parse(htmlContent);
    String bulletinDateStr = emailDate.format(DateTimeFormatter.ofPattern("MM/dd"));

    Element table = findRelevantTable(doc);
    if (table == null) {
      System.err.println("No relevant table found.");
      return;
    }

    TableColumns columns = identifyTableColumns(table);
    if (columns.greensboroCol == -1 && columns.lindenCol == -1) {
      System.err.println("No valid columns found in table.");
      return;
    }

    processTableRows(table, emailDate, bulletinDateStr, columns,
        gbjCurrent, lnjCurrent, gbjAdjacent, lnjAdjacent);
  }

  private static Element findRelevantTable(Document doc) {
    Elements tables = doc.select("table");
    for (Element t : tables) {
      Elements headers = t.select("tr:first-child th, tr:first-child td");
      for (Element header : headers) {
        String text = header.text().toLowerCase();
        if (text.contains("greensboro") || text.contains("linden")) {
          return t;
        }
      }
    }
    return null;
  }

  private static class TableColumns {
    int greensboroCol = -1;
    int lindenCol = -1;
  }

  private static TableColumns identifyTableColumns(Element table) {
    TableColumns columns = new TableColumns();
    Elements headers = table.select("tr:first-child th, tr:first-child td");

    for (int i = 0; i < headers.size(); i++) {
      String header = headers.get(i).text().trim().toLowerCase();
      if (header.contains("greensboro")) columns.greensboroCol = i;
      else if (header.contains("linden")) columns.lindenCol = i;
    }

    return columns;
  }

  private static void processTableRows(Element table, LocalDate emailDate, String bulletinDateStr,
      TableColumns columns,
      Map<String, Map<String, List<String>>> gbjCurrent,
      Map<String, Map<String, List<String>>> lnjCurrent,
      Map<String, Map<String, List<String>>> gbjAdjacent,
      Map<String, Map<String, List<String>>> lnjAdjacent) {

    Elements rows = table.select("tr:gt(0)");
    logger.info("Processing {} rows in fungible table for date {}", rows.size(), emailDate);

    for (Element row : rows) {
      Elements cells = row.select("td");
      if (cells.size() <= Math.max(columns.greensboroCol, columns.lindenCol)) {
        logger.debug("Skipping row - not enough columns");
        continue;
      }

      String rawFuel = cells.get(0).text().trim();
      String rawCycle = cells.get(1).text().trim();
      if (rawFuel.isEmpty() || rawCycle.length() != 3) {
        logger.debug("Skipping row - invalid fuel '{}' or cycle '{}'", rawFuel, rawCycle);
        continue;
      }

      String fuel = rawFuel;
      String cycle = rawCycle.substring(0, 2);
      if (!isValidFuelType(fuel)) {
        logger.debug("Skipping row - invalid fuel type: {}", fuel);
        continue;
      }

      int cycleNum = Integer.parseInt(cycle);
      logger.debug("Processing fuel: {}, cycle: {}", fuel, cycle);

      // For Greensboro data
      if (columns.greensboroCol != -1) {
        String dateStr = cells.get(columns.greensboroCol).text().trim();
        if (dateStr.equals(bulletinDateStr)) {
          boolean useAdjacent = shouldUseAdjacentYear(emailDate, cycleNum);
          Map<String, Map<String, List<String>>> targetGbj = useAdjacent ? gbjAdjacent : gbjCurrent;
          if (targetGbj != null) {
            targetGbj.computeIfAbsent(fuel, k -> new HashMap<>())
                .computeIfAbsent(cycle, k -> new ArrayList<>())
                .add(bulletinDateStr);
            logger.info("Added GBJ data - Fuel: {}, Cycle: {}, Year: {}, Date: {}",
                fuel, cycle, useAdjacent ? "adjacent" : "current", bulletinDateStr);
          }
        } else {
          logger.debug("GBJ date mismatch: {} != {}", dateStr, bulletinDateStr);
        }
      }

      // For Linden data
      if (columns.lindenCol != -1) {
        String dateStr = cells.get(columns.lindenCol).text().trim();
        if (dateStr.equals(bulletinDateStr)) {
          boolean useAdjacent = shouldUseAdjacentYear(emailDate, cycleNum);
          Map<String, Map<String, List<String>>> targetLnj = useAdjacent ? lnjAdjacent : lnjCurrent;
          if (targetLnj != null) {
            targetLnj.computeIfAbsent(fuel, k -> new HashMap<>())
                .computeIfAbsent(cycle, k -> new ArrayList<>())
                .add(bulletinDateStr);
            logger.info("Added LNJ data - Fuel: {}, Cycle: {}, Year: {}, Date: {}",
                fuel, cycle, useAdjacent ? "adjacent" : "current", bulletinDateStr);
          }
        } else {
          logger.debug("LNJ date mismatch: {} != {}", dateStr, bulletinDateStr);
        }
      }
    }
  }

  private static boolean isValidFuelType(String fuel) {
    return fuel.startsWith("A") || fuel.startsWith("D") || fuel.startsWith("F") || fuel.equals("62");
  }

  private static boolean shouldUseAdjacentYear(LocalDate date, int cycleNum) {
    int month = date.getMonthValue();
    if (month >= 11 && cycleNum < 20) {
      logger.debug("Cycle {} in Nov/Dec - using next year", cycleNum);
      return true;
    }
    if (month <= 2 && cycleNum > 60) {
      logger.debug("Cycle {} in Jan/Feb - using previous year", cycleNum);
      return true;
    }
    logger.debug("Cycle {} - using current year", cycleNum);
    return false;
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

  private static Map<String, Map<String, List<String>>> readExistingCsv(String basePath, int year) throws IOException {
    String csvPath = basePath + year + ".csv";
    Map<String, Map<String, List<String>>> existingData = new HashMap<>();

    if (!Files.exists(Paths.get(csvPath))) {
      return existingData;
    }

    List<String> lines = Files.readAllLines(Paths.get(csvPath));
    if (lines.isEmpty()) return existingData;

    for (int i = 1; i < lines.size(); i++) {
      String[] parts = lines.get(i).split(",");
      if (parts.length < 2) continue;

      String fuelType = parts[0];
      Map<String, List<String>> cycleData = new HashMap<>();

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

  private static void writeCompleteCsv(String basePath, int year,
      Map<String, Map<String, List<String>>> deliveryData) throws IOException {
    String csvPath = basePath + year + ".csv";
    Set<String> allCycles = new TreeSet<>();
    for (int i = 1; i <= 72; i++) {
      allCycles.add(String.format("%02d", i));
    }

    List<String> fuelTypes = new ArrayList<>(deliveryData.keySet());
    fuelTypes.sort(Comparator.naturalOrder());

    List<String> lines = new ArrayList<>();
    StringBuilder header = new StringBuilder("Type");
    for (String cycle : allCycles) {
      header.append(",").append(cycle);
    }
    lines.add(header.toString());

    // Write individual fuel types
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

    // Add aggregate rows
    addAggregateRow(lines, deliveryData, allCycles, "A", Arrays.asList("A2", "A3", "A4"));
    addAggregateRow(lines, deliveryData, allCycles, "D", Arrays.asList("D2", "D3", "D4"));
    addAggregateRow(lines, deliveryData, allCycles, "F", Arrays.asList("F1", "F3", "F4", "F5"));

    // Create directory if it doesn't exist
    Path path = Paths.get(csvPath);
    Path parentDir = path.getParent();
    if (!Files.exists(parentDir)) {
      Files.createDirectories(parentDir);
    }

    Files.write(path, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    System.out.println("Updated " + csvPath + " with " + (fuelTypes.size() + 3) + " fuel types (including aggregates)");
  }

  private static void addAggregateRow(List<String> lines,
      Map<String, Map<String, List<String>>> deliveryData,
      Set<String> allCycles,
      String aggregateType,
      List<String> subTypes) {
    StringBuilder row = new StringBuilder(aggregateType);

    for (String cycle : allCycles) {
      row.append(",");
      Set<String> allDates = new LinkedHashSet<>(); // Using Set to avoid duplicates

      // Collect dates from all sub-types for this cycle
      for (String subType : subTypes) {
        if (deliveryData.containsKey(subType) && deliveryData.get(subType).containsKey(cycle)) {
          allDates.addAll(deliveryData.get(subType).get(cycle));
        }
      }

      if (!allDates.isEmpty()) {
        row.append(String.join(";", allDates));
      }
    }

    lines.add(row.toString());
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