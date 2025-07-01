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

public class ColonialFungible {
  private static final String GBJ_CSV_PATH = "data/Colonial/Fungible/GBJ.csv";
  private static final String LNJ_CSV_PATH = "data/Colonial/Fungible/LNJ.csv";
  private static final String USER_PRINCIPAL_NAME = "automatedreports@rioenergy.com";
  private static final String EMAIL_SUBJECT_FILTER = "colonial - fungible deliveries";
  private static final Set<String> processedDates = new HashSet<>();


  public static void main(String[] args) {
    try {
      System.out.println("Starting fungible delivery data processing");
      String accessToken = getAccessToken();
      List<Message> messages = fetchFungibleEmails(accessToken, USER_PRINCIPAL_NAME);
      processNewMessages(messages);
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
    requestOptions.add(new QueryOption("$top", "50"));

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

  public static void processMessages(List<Message> messages) throws IOException {
    Map<String, Map<String, List<String>>> gbjData = new HashMap<>();
    Map<String, Map<String, List<String>>> lnjData = new HashMap<>();

    messages.sort(Comparator.comparing(m -> m.receivedDateTime));

    for (Message message : messages) {
      try {
        if (message.body == null || message.body.content == null) continue;
        parseFungibleBody(message.body.content, gbjData, lnjData, processedDates);
      } catch (Exception e) {
        System.err.println("Failed to process message with subject: " + message.subject);
        e.printStackTrace();
      }
    }

    updateCsv(GBJ_CSV_PATH, gbjData);
    updateCsv(LNJ_CSV_PATH, lnjData);
  }

  public static void parseFungibleBody(String htmlContent,
      Map<String, Map<String, List<String>>> gbjData,
      Map<String, Map<String, List<String>>> lnjData,
      Set<String> processedDates) {
    Document doc = Jsoup.parse(htmlContent);
    String plainText = doc.text().replaceAll("\\s+", " ").trim();

    LocalDate emailDate;
    try {
      emailDate = extractDateFromPlainText(plainText);
      String dateKey = emailDate.format(DateTimeFormatter.ofPattern("MM/dd"));
      if (processedDates.contains(dateKey)) return;
      processedDates.add(dateKey);
    } catch (Exception e) {
      System.err.println("Failed to parse date: " + e.getMessage());
      return;
    }

    Element table = null;
    Elements tables = doc.select("table");
    for (Element t : tables) {
      Elements headers = t.select("tr:first-child th, tr:first-child td");
      for (Element header : headers) {
        String text = header.text().toLowerCase();
        if (text.contains("greensboro") || text.contains("linden")) {
          table = t;
          break;
        }
      }
      if (table != null) break;
    }

    if (table == null) {
      System.err.println("No relevant table found.");
      return;
    }

    Elements headers = table.select("tr:first-child th, tr:first-child td");
    int greensboroCol = -1, lindenCol = -1;
    for (int i = 0; i < headers.size(); i++) {
      String header = headers.get(i).text().trim().toLowerCase();
      if (header.contains("greensboro")) greensboroCol = i;
      else if (header.contains("linden")) lindenCol = i;
    }

    Elements rows = table.select("tr:gt(0)");
    for (Element row : rows) {
      Elements cells = row.select("td");
      if (cells.size() <= Math.max(greensboroCol, lindenCol)) continue;

      String rawFuel = cells.get(0).text().trim();
      String rawCycle = cells.get(1).text().trim();
      if (rawFuel.isEmpty() || rawCycle.length() != 3) continue;

      String fuel = rawFuel;
      String cycle = rawCycle.substring(0, 2);
      if (!fuel.startsWith("A") && !fuel.startsWith("D") && !fuel.startsWith("F") && !fuel.equals("62")) {
        continue; // Skip any fuel not in A, D, F, or 62
      }

      String bulletinDate = emailDate.format(DateTimeFormatter.ofPattern("MM/dd"));

      if (greensboroCol != -1) {
        String dateStr = cells.get(greensboroCol).text().trim();
        if (dateStr.equals(bulletinDate)) {
          System.out.printf("Match found: Greensboro | Fuel=%s | Cycle=%s | Date=%s%n", fuel, cycle, bulletinDate);
          gbjData.computeIfAbsent(fuel, k -> new HashMap<>()).computeIfAbsent(cycle, k -> new ArrayList<>()).add(bulletinDate);
        }
      }

      if (lindenCol != -1) {
        String dateStr = cells.get(lindenCol).text().trim();
        if (dateStr.equals(bulletinDate)) {
          System.out.printf("Match found: Linden | Fuel=%s | Cycle=%s | Date=%s%n", fuel, cycle, bulletinDate);
          lnjData.computeIfAbsent(fuel, k -> new HashMap<>()).computeIfAbsent(cycle, k -> new ArrayList<>()).add(bulletinDate);
        }
      }
    }
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

  public static void updateCsv(String csvPath, Map<String, Map<String, List<String>>> deliveryData) throws IOException {
    Path path = Paths.get(csvPath);
    List<String> lines = Files.exists(path) ? Files.readAllLines(path) : new ArrayList<>();
    List<String> updatedLines = new ArrayList<>();

    if (lines.isEmpty()) {
      System.err.println("CSV file is empty or missing: " + csvPath);
      return;
    }

    String[] headers = lines.get(0).split(",");
    updatedLines.add(lines.get(0)); // Add header row

    for (int i = 1; i < lines.size(); i++) {
      String[] row = lines.get(i).split(",", -1);
      String fuel = row[0];

      for (int j = 1; j < headers.length; j++) {
        String cycle = headers[j];

        if (deliveryData.containsKey(fuel) && deliveryData.get(fuel).containsKey(cycle)) {
          List<String> newDates = deliveryData.get(fuel).get(cycle);
          String existing = row[j];

          if (existing == null || existing.isEmpty()) {
            row[j] = String.join(";", newDates);
          } else {
            Set<String> combined = new LinkedHashSet<>(Arrays.asList(existing.split(";")));
            combined.addAll(newDates);
            row[j] = String.join(";", combined);
          }
        }
      }

      updatedLines.add(String.join(",", row));
    }

    Files.write(path, updatedLines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    System.out.println("Updated " + csvPath + " with " + (updatedLines.size() - 1) + " rows");
  }

  public static void processNewMessages(List<Message> messages) throws IOException {
    if (processedDates.isEmpty()) {
      System.out.println("No previously processed dates found. Parsing all messages.");
      processMessages(messages);
      return;
    }

    LocalDate latestProcessed = processedDates.stream()
        .map(date -> LocalDate.parse(date, DateTimeFormatter.ofPattern("MM/dd")))
        .max(LocalDate::compareTo)
        .orElse(LocalDate.MIN);

    System.out.println("Latest processed bulletin date: " + latestProcessed);

    Map<String, Map<String, List<String>>> gbjData = new HashMap<>();
    Map<String, Map<String, List<String>>> lnjData = new HashMap<>();

    for (Message message : messages) {
      if (message.receivedDateTime == null) continue;

      LocalDate receivedDate = LocalDate.parse(message.receivedDateTime.toString().substring(0, 10));
      if (receivedDate.isAfter(latestProcessed)) {
        System.out.println("Processing new message received on: " + receivedDate);
        parseFungibleBody(message.body.content, gbjData, lnjData, processedDates);
      }
    }

    updateCsv(GBJ_CSV_PATH, gbjData);
    updateCsv(LNJ_CSV_PATH, lnjData);
  }

}
