package Colonial;

import static Outlook.ExplorerParser.getAccessToken;
import com.microsoft.graph.models.*;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.*;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class ColonialFungible {
  private static final String GBJ_CSV_PATH = "data/Colonial/Fungible/GBJ.csv";
  private static final String LNJ_CSV_PATH = "data/Colonial/Fungible/LNJ.csv";
  private static final String USER_PRINCIPAL_NAME = "automatedreports@rioenergy.com";
  private static final String EMAIL_SUBJECT_FILTER = "colonial - fungible deliveries";
  private static final String GBJ_CONTEXT = "Greensboro";
  private static final String LNJ_CONTEXT = "Linden";

  public static void main(String[] args) {
    try {
      System.out.println("Starting fungible delivery data processing");
      String accessToken = getAccessToken();
      List<Message> messages = fetchFungibleEmails(accessToken, USER_PRINCIPAL_NAME);
      processMessages(messages);
      System.out.println("Fungible delivery data successfully processed");
    } catch (Exception e) {
      System.err.println("Error processing fungible delivery data: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public static List<Message> fetchFungibleEmails(String accessToken, String userPrincipalName) throws IOException {
    System.out.println("Fetching fungible emails for user: " + userPrincipalName);
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<?> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    List<Message> relevantMessages = new ArrayList<>();
    LinkedList<QueryOption> requestOptions = new LinkedList<>();
    requestOptions.add(new QueryOption("$select", "subject,receivedDateTime,body"));
    requestOptions.add(new QueryOption("$top", "50"));
    // Removed the complex filter and will filter client-side instead

    MessageCollectionPage messagesPage;
    MessageCollectionRequestBuilder nextPage = null;
    int totalMessagesProcessed = 0;

    do {
      System.out.println("Fetching next page of messages");
      messagesPage = (nextPage == null)
          ? graphClient.users(userPrincipalName).messages()
          .buildRequest(requestOptions)
          .orderBy("receivedDateTime desc")
          .get()
          : nextPage.buildRequest().get();

      for (Message message : messagesPage.getCurrentPage()) {
        // Client-side filtering instead of server-side
        if (message.subject != null && message.subject.toLowerCase().contains(EMAIL_SUBJECT_FILTER)) {
          System.out.println("Processing relevant message with subject: " + message.subject);
          if (message.body == null || message.body.content == null) {
            System.out.println("Fetching full message body for message ID: " + message.id);
            Message fullMessage = graphClient.users(userPrincipalName)
                .messages(message.id)
                .buildRequest()
                .select("body")
                .get();
            message.body = fullMessage.body;
          }
          relevantMessages.add(message);
        }
        totalMessagesProcessed++;
      }
      nextPage = messagesPage.getNextPage();
    } while (nextPage != null && relevantMessages.size() < 50); // Limit to 50 relevant messages

    System.out.println("Fetched " + relevantMessages.size() + " relevant messages out of " + totalMessagesProcessed + " processed");
    return relevantMessages;
  }

  public static void processMessages(List<Message> messages) throws IOException {
    System.out.println("Processing " + messages.size() + " messages");
    Map<String, Map<String, List<String>>> gbjData = new HashMap<>();
    Map<String, Map<String, List<String>>> lnjData = new HashMap<>();

    for (Message message : messages) {
      try {
        if (message.body != null && message.body.content != null) {
          System.out.println("Parsing message body for message with subject: " + message.subject);
          String plainText = Jsoup.parse(message.body.content).text();
          parseFungibleBody(plainText, gbjData, lnjData);
        }
      } catch (Exception e) {
        System.err.println("Failed to process message with subject: " + message.subject);
        e.printStackTrace();
      }
    }

    updateCsv(GBJ_CSV_PATH, gbjData);
    updateCsv(LNJ_CSV_PATH, lnjData);
  }

  public static void parseFungibleBody(String text, Map<String, Map<String, List<String>>> gbjData,
      Map<String, Map<String, List<String>>> lnjData) {
    System.out.println("Parsing fungible body text");
    Pattern rowPattern = Pattern.compile("([A-Z0-9]{1,3})\\s+(\\d{3})\\s+((?:\\d{2}/\\d{2}\\s*)+)");
    Matcher matcher = rowPattern.matcher(text);
    int rowsProcessed = 0;

    while (matcher.find()) {
      String fuel = matcher.group(1);
      String cycle = matcher.group(2);
      String[] dates = matcher.group(3).trim().split("\\s+");
      rowsProcessed++;

      System.out.println("Found data - Fuel: " + fuel + ", Cycle: " + cycle + ", Dates: " + Arrays.toString(dates));

      String context = text.substring(matcher.start(), Math.min(text.length(), matcher.end() + 100));
      if (context.contains(GBJ_CONTEXT)) {
        System.out.println("Adding to GBJ data");
        gbjData.computeIfAbsent(fuel, k -> new HashMap<>())
            .computeIfAbsent(cycle, k -> new ArrayList<>())
            .addAll(Arrays.asList(dates));
      }
      if (context.contains(LNJ_CONTEXT)) {
        System.out.println("Adding to LNJ data");
        lnjData.computeIfAbsent(fuel, k -> new HashMap<>())
            .computeIfAbsent(cycle, k -> new ArrayList<>())
            .addAll(Arrays.asList(dates));
      }
    }
    System.out.println("Processed " + rowsProcessed + " data rows from email body");
  }

  public static void updateCsv(String csvPath, Map<String, Map<String, List<String>>> deliveryData) throws IOException {
    System.out.println("Updating CSV file: " + csvPath);
    Path path = Paths.get(csvPath);

    if (!Files.exists(path)) {
      System.err.println("CSV file does not exist: " + csvPath);
      return;
    }

    List<String> lines = Files.readAllLines(path);
    if (lines.isEmpty()) {
      System.err.println("CSV file is empty: " + csvPath);
      return;
    }

    System.out.println("Current CSV headers: " + lines.get(0));
    System.out.println("Delivery data keys: " + deliveryData.keySet());

    List<String> updatedLines = new ArrayList<>();
    updatedLines.add(lines.get(0));
    int updatesMade = 0;

    String[] headers = lines.get(0).split(",");
    for (int i = 1; i < lines.size(); i++) {
      String[] row = lines.get(i).split(",", -1);
      String fuel = row[0];
      System.out.println("Processing row for fuel: " + fuel);

      for (int j = 1; j < headers.length; j++) {
        String cycle = headers[j];
        if (deliveryData.containsKey(fuel) && deliveryData.get(fuel).containsKey(cycle)) {
          String newValue = String.join(";", deliveryData.get(fuel).get(cycle));
          if (!newValue.equals(row[j])) {
            System.out.println("Updating cycle " + cycle + " from [" + row[j] + "] to [" + newValue + "]");
            row[j] = newValue;
            updatesMade++;
          }
        }
      }
      updatedLines.add(String.join(",", row));
    }

    Files.write(path, updatedLines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    System.out.println("Updated " + csvPath + " with " + (updatedLines.size() - 1) + " rows (" + updatesMade + " changes made)");
  }
}