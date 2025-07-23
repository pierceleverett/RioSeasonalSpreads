package Colonial;

import static Outlook.ExplorerParser.getAccessToken;
import com.microsoft.graph.models.*;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.*;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class MinColonialUpdater {
  private static final String PROCESSED_DATES_PATH = "data/Colonial/Fungible/all_processed_dates.txt";
  private static final String USER_PRINCIPAL_NAME = "automatedreports@rioenergy.com";
  private static final String EMAIL_SUBJECT_FILTER = "colonial - fungible deliveries";
  public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yy");

  public static void main(String[] args) {
    try {
      System.out.println("=== Colonial Fungible Processor ===");

      // Load the last processed date
      LocalDate lastProcessedDate = getLastProcessedDate();
      System.out.println("Last processed date: " +
          (lastProcessedDate != null ? lastProcessedDate.format(DATE_FORMATTER) : "None"));

      // Process new bulletins
      processNewBulletins(lastProcessedDate);

      System.out.println("=== Processing completed successfully ===");
    } catch (Exception e) {
      System.err.println("!!! Processing failed !!!");
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  public static LocalDate getLastProcessedDate() throws IOException {
    Path path = Paths.get(PROCESSED_DATES_PATH);
    if (!Files.exists(path)) {
      return null;
    }

    List<String> dates = Files.readAllLines(path);
    if (dates.isEmpty()) {
      return null;
    }

    // Get the most recent date (last line in the file)
    String lastDateStr = dates.get(dates.size() - 1);
    return LocalDate.parse(lastDateStr, DATE_FORMATTER);
  }

  // Replace the processNewBulletins method with this updated version
  public static void processNewBulletins(LocalDate lastProcessedDate) throws IOException {
    String accessToken = getAccessToken();
    List<Message> messages = fetchFungibleEmails(accessToken, USER_PRINCIPAL_NAME, lastProcessedDate);

    // Filter messages to only those after last processed date
    List<Message> newMessages = messages.stream()
        .filter(msg -> lastProcessedDate == null ||
            msg.receivedDateTime.isAfter(
                lastProcessedDate.plusDays(1)  // Start from next day
                    .atStartOfDay()           // Beginning of that day
                    .atZone(ZoneId.of("America/Chicago"))  // In Central Time
                    .toOffsetDateTime()))     // Convert to OffsetDateTime
        .collect(Collectors.toList());

    System.out.println("Found " + newMessages.size() + " new bulletins to process");

    if (newMessages.isEmpty()) {
      System.out.println("No new bulletins to process");
      return;
    }

    // Process the new messages using existing functionality
    MinColonialFungible.processAllMessages(newMessages);
  }

  public static List<Message> fetchFungibleEmails(String accessToken, String userPrincipalName, LocalDate lastProcessedDate) throws IOException {
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<?> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    List<Message> relevantMessages = new ArrayList<>();
    LinkedList<QueryOption> requestOptions = new LinkedList<>();

    // Apply filter if lastProcessedDate is available
    if (lastProcessedDate != null) {
      String start = lastProcessedDate.toString() + "T00:00:00Z";
      String filter = "receivedDateTime ge " + start;
      requestOptions.add(new QueryOption("$filter", filter));
    }

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
        if (message.subject != null &&
            message.subject.toLowerCase().contains("colonial - fungible deliveries")) {

          if (message.body == null || message.body.content == null) {
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
}