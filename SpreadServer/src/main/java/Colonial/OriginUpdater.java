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
import java.util.*;
import java.util.List;

public class OriginUpdater {
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yy");
  public static List<String> processedCycles = new ArrayList<>();

  public static void main(String[] args) {
    try {
      System.out.println("=== Colonial Origin Updater ===");
      String accessToken = getAccessToken();

      System.out.println("Updating origin data from most recent email...");
      Map<String, List<String>> response = updateFromMostRecentOriginEmail(accessToken);

      System.out.println("=== Update completed ===");
    } catch (Exception e) {
      System.err.println("Error in OriginUpdater: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public static Map<String, List<String>> updateFromMostRecentOriginEmail(String accessToken) throws IOException {
    processedCycles.clear();
    Message message = fetchMostRecentOriginEmail(accessToken, "automatedreports@rioenergy.com");
    if (message == null) {
      System.out.println("No origin starts email found in the last 30 days");
      return null;
    }

    System.out.println("Processing origin starts email received on: " + message.receivedDateTime);
    LocalDate bulletinDate = ColonialOrigin.extractBulletinDate(message.body.content);

    // Use ColonialOrigin's processing logic
    ColonialOrigin.processOriginStartsEmail(message.body.content, bulletinDate);

    // Track processed cycles (if needed for reporting)
    HashMap<String, List<String>> returnMap = new HashMap<>();
    returnMap.put("Processed Cycles", processedCycles);
    returnMap.put("Bulletin Date", Collections.singletonList(bulletinDate.toString()));
    return returnMap;
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
          .select("subject,receivedDateTime,body")
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
      }
    } catch (Exception e) {
      System.err.println("Error fetching email: " + e.getMessage());
      e.printStackTrace();
    }
    return null;
  }
}