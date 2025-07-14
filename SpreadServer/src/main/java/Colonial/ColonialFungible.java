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
    requestOptions.add(new QueryOption("$top", "500")); // Increased to get more emails

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
    Map<String, Map<String, List<String>>> gbjData = new HashMap<>();
    Map<String, Map<String, List<String>>> lnjData = new HashMap<>();

    // Sort messages chronologically
    messages.sort(Comparator.comparing(m -> m.receivedDateTime));

    // Process all messages
    for (Message message : messages) {
      try {
        if (message.body == null || message.body.content == null) continue;
        parseFungibleBody(message.body.content, gbjData, lnjData);
      } catch (Exception e) {
        System.err.println("Failed to process message with subject: " + message.subject);
        e.printStackTrace();
      }
    }

    // Write the complete data to CSVs
    writeCompleteCsv(GBJ_CSV_PATH, gbjData);
    writeCompleteCsv(LNJ_CSV_PATH, lnjData);
  }

  public static void parseFungibleBody(String htmlContent,
      Map<String, Map<String, List<String>>> gbjData,
      Map<String, Map<String, List<String>>> lnjData) {
    Document doc = Jsoup.parse(htmlContent);
    String plainText = doc.text().replaceAll("\\s+", " ").trim();

    LocalDate emailDate;
    try {
      emailDate = extractDateFromPlainText(plainText);
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
        continue;
      }

      String bulletinDate = emailDate.format(DateTimeFormatter.ofPattern("MM/dd"));

      if (greensboroCol != -1) {
        String dateStr = cells.get(greensboroCol).text().trim();
        if (dateStr.equals(bulletinDate)) {
          gbjData.computeIfAbsent(fuel, k -> new HashMap<>())
              .computeIfAbsent(cycle, k -> new ArrayList<>())
              .add(bulletinDate);
        }
      }

      if (lindenCol != -1) {
        String dateStr = cells.get(lindenCol).text().trim();
        if (dateStr.equals(bulletinDate)) {
          lnjData.computeIfAbsent(fuel, k -> new HashMap<>())
              .computeIfAbsent(cycle, k -> new ArrayList<>())
              .add(bulletinDate);
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

    // Prepare data rows
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
    System.out.println("Updated " + csvPath + " with " + fuelTypes.size() + " fuel types");
  }
}