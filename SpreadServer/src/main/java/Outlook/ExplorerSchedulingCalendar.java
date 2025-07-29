package Outlook;

import static Outlook.ExplorerParser.getAccessToken;

import Outlook.ExplorerCalendarList.CalendarList;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ExplorerSchedulingCalendar {
  private static final String EMAIL_SUBJECT_FILTER = "explorer - scheduling calendar";

  public static void main(String[] args) {
    try {
      System.out.println("[DEBUG] Starting calendar extraction");
      String accessToken = getAccessToken();
      System.out.println("[DEBUG] Access token retrieved");

      Message recentEmail = fetchMostRecentSchedulingCalendarEmail(accessToken, "automatedreports@rioenergy.com");
      if (recentEmail == null) {
        System.out.println("[WARN] No matching email found");
        return;
      }

      System.out.println("[DEBUG] Found matching message: " + recentEmail.subject);
      Map<String, Map<Integer, List<String>>> data = parseCalendarFromEmail(recentEmail);
      System.out.println("[INFO] Parsing completed. Results:");
      System.out.println(data);
    } catch (IOException e) {
      System.out.println("[ERROR] Failed to process calendar: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public static Message fetchMostRecentSchedulingCalendarEmail(String accessToken, String userPrincipalName) throws IOException {
    try {
      System.out.println("[DEBUG] Fetching most recent scheduling calendar email");
      IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
      GraphServiceClient<?> graphClient = GraphServiceClient.builder()
          .authenticationProvider(authProvider)
          .buildClient();

      String dateFilter = "receivedDateTime ge " +
          OffsetDateTime.now(ZoneOffset.UTC).minusDays(30)
              .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

      MessageCollectionPage messagesPage = graphClient.users(userPrincipalName)
          .messages()
          .buildRequest()
          .filter(dateFilter + " and contains(subject, '" + EMAIL_SUBJECT_FILTER + "')")
          .top(1)
          .orderBy("receivedDateTime desc")
          .get();

      if (!messagesPage.getCurrentPage().isEmpty()) {
        Message message = messagesPage.getCurrentPage().get(0);
        System.out.println("[DEBUG] Found potential message: " + message.subject);

        if (message.body == null || message.body.content == null) {
          System.out.println("[DEBUG] Fetching full message content");
          message = graphClient.users(userPrincipalName)
              .messages(message.id)
              .buildRequest()
              .select("body,subject,receivedDateTime")
              .get();
        }
        return message;
      }
      System.out.println("[DEBUG] No messages found matching criteria");
      return null;
    } catch (Exception e) {
      System.out.println("[ERROR] Failed to fetch email: " + e.getMessage());
      throw new IOException("Failed to fetch origin email", e);
    }
  }

  public static Map<String, Map<Integer, List<String>>> parseCalendarFromEmail(Message message) {
    Map<String, Map<Integer, List<String>>> result = new LinkedHashMap<>();
    System.out.println("[DEBUG] Starting enhanced email parsing");

    try {
      if (message == null || message.body == null || message.body.content == null) {
        System.out.println("[ERROR] Invalid message content");
        return result;
      }

      String emailContent = message.body.content;
      Document doc = Jsoup.parse(emailContent);
      System.out.println("[DEBUG] HTML parsed successfully");

      // Process each text section
      Elements textSections = doc.select("body");
      String currentMonth = null;

      for (Element section : textSections) {
        String fullText = section.text().replaceAll("\\s+", " ").trim();
        System.out.println("[DEBUG] Processing full text section");

        // Split the text while preserving both the delimiters (day numbers) and content
        String[] parts = fullText.split("(?<!Cycle)(?<!&)(?<!&amp;)(?<!\\(C\\d{1,2}-) (?=\\d{1,2} )");

        for (int i = 0; i < parts.length; i++) {
          String part = parts[i].trim();
          if (part.isEmpty()) continue;

          System.out.println("[DEBUG] Processing part: '" + part + "'");

          // Check for month header
          if (part.matches("(?i)(January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{4}")) {
            currentMonth = part.split("\\s+")[0];
            result.put(currentMonth, new LinkedHashMap<>());
            System.out.println("[DEBUG] Found month: " + currentMonth);
            continue;
          }

          // Check if this is a day number (standalone digit)
          if (currentMonth != null && part.matches("^\\d+$") && i < parts.length - 1) {
            try {
              int day = Integer.parseInt(part);
              String eventsText = parts[i+1].trim();
              i++; // Skip next part since we're using it as events

              System.out.println("[DEBUG] Found day " + day + " with events: '" + eventsText + "'");

              // Split events by "Cycle" pattern
              List<String> events = new ArrayList<>();
              String[] eventParts = eventsText.split("(?=Cycle\\s\\d+:)");

              for (String event : eventParts) {
                String trimmed = event.trim();
                if (!trimmed.isEmpty()) {
                  events.add(trimmed);
                  System.out.println("[DEBUG] Added event: '" + trimmed + "'");
                }
              }

              result.get(currentMonth).put(day, events);
            } catch (NumberFormatException e) {
              System.out.println("[WARN] Failed to parse day number: '" + part + "'");
            }
          }
        }
      }

      // Final debug output
      System.out.println("[INFO] Parsing complete. Results:");
      for (Map.Entry<String, Map<Integer, List<String>>> monthEntry : result.entrySet()) {
        System.out.println("[INFO] Month: " + monthEntry.getKey());
        for (Map.Entry<Integer, List<String>> dayEntry : monthEntry.getValue().entrySet()) {
          System.out.println("[INFO]   Day " + dayEntry.getKey() + ":");
          for (String event : dayEntry.getValue()) {
            System.out.println("[INFO]     - " + event);
          }
        }
      }

    } catch (Exception e) {
      System.out.println("[ERROR] Error parsing calendar: " + e.getMessage());
      e.printStackTrace();
    }

    return result;
  }
}