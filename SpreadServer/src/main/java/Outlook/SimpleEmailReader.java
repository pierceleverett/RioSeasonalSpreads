package Outlook;

import static Outlook.ExplorerParser.getAccessToken;

import Outlook.FusionCurveParser.SimpleAuthProvider;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import com.microsoft.graph.requests.MessageCollectionRequestBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SimpleEmailReader {

  public static void main(String[] args) throws IOException {
    String accessToken = getAccessToken();
    System.out.println(fetchRecentCurveReportEmails(accessToken, "automatedreports@rioenergy.com"));
  }

  public static List<Message> fetchRecentCurveReportEmails(String accessToken, String userPrincipalName) throws IOException {
    final String METHOD_NAME = "fetchRecentCurveReportEmails";
    System.out.printf("[%s] Starting with user: %s%n", METHOD_NAME, userPrincipalName);
    List<Message> emails = new ArrayList<>();

    try {
      // Initialize Graph client
      IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
      GraphServiceClient<?> graphClient = GraphServiceClient.builder()
          .authenticationProvider(authProvider)
          .buildClient();

      System.out.printf("[%s] Fetching top 100 emails%n", METHOD_NAME);

      // Query top 100 messages (no filters, just get the latest)
      MessageCollectionPage messagesPage = graphClient.users(userPrincipalName)
          .messages()
          .buildRequest()
          .select("subject,receivedDateTime") // Only get subject and date
          .orderBy("receivedDateTime desc")   // Get most recent first
          .top(100)
          .get();

      // Process all pages
      while (messagesPage != null) {
        for (Message message : messagesPage.getCurrentPage()) {
          System.out.printf("[%s] Email: %s (Received: %s)%n",
              METHOD_NAME, message.subject, message.receivedDateTime);
          emails.add(message);
        }

        // Get next page if available
        MessageCollectionRequestBuilder nextPage = messagesPage.getNextPage();
        if (nextPage == null) {
          break;
        }
        messagesPage = nextPage.buildRequest().get();
      }

      System.out.printf("[%s] Found %d emails%n", METHOD_NAME, emails.size());

    } catch (Exception e) {
      String errorMsg = String.format("[%s ERROR] Failed to fetch emails: %s",
          METHOD_NAME, e.getMessage());
      System.err.println(errorMsg);
      throw new IOException(errorMsg, e);
    }

    return emails;
  }

}
