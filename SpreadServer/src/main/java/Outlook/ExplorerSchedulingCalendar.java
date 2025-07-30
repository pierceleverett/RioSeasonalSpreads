package Outlook;

import static Outlook.ExplorerParser.getAccessToken;

import Outlook.ExplorerCalendarList.CalendarList;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import com.microsoft.graph.requests.MessageCollectionRequest;
import com.microsoft.graph.requests.MessageCollectionRequestBuilder;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jetty.server.RequestLog.Collection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class ExplorerSchedulingCalendar {
  private static final String EMAIL_SUBJECT_FILTER = "explorer - scheduling calendar";

  public static class SchedulingCalendar {

    public String bulletinDate;
    public Map<String, Map<Integer, List<String>>> data;

    // Constructor
    public SchedulingCalendar(String bulletinDate,
        Map<String, Map<Integer, List<String>>> data) {
      this.bulletinDate = bulletinDate;
      this.data = data;
    }

    public static void main(String[] args) {
      try {
        System.out.println("[DEBUG] Starting calendar extraction");
        String accessToken = getAccessToken();
        System.out.println("[DEBUG] Access token retrieved");

        Message recentEmail = fetchMostRecentSchedulingCalendarEmail(accessToken,
            "automatedreports@rioenergy.com");
        if (recentEmail == null) {
          System.out.println("[WARN] No matching email found");
          return;
        }

        System.out.println("[DEBUG] Found matching message: " + recentEmail.subject);
        SchedulingCalendar data = parseCalendarFromEmail(recentEmail);
        System.out.println("[INFO] Parsing completed. Results:");
        System.out.println(data);
      } catch (IOException e) {
        System.out.println("[ERROR] Failed to process calendar: " + e.getMessage());
        e.printStackTrace();
      }
    }

    public static Message fetchMostRecentSchedulingCalendarEmail(String accessToken,
        String userPrincipalName) throws IOException {
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

    public static Message fetchSecondMostRecentCalendarEmail(String accessToken, String userPrincipalName) throws IOException {
      final String METHOD_NAME = "fetchSecondMostRecentCalendarEmail";
      System.out.printf("[%s] Starting with user: %s%n", METHOD_NAME, userPrincipalName);

      try {
        // Initialize Graph client
        IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
        GraphServiceClient<?> graphClient = GraphServiceClient.builder()
            .authenticationProvider(authProvider)
            .buildClient();

        // Create combined filter for date and subject
        OffsetDateTime searchStartDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(90);
        String dateFilter = String.format("receivedDateTime ge %s",
            searchStartDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        String combinedFilter = String.format("%s and contains(subject, '%s')",
            dateFilter, EMAIL_SUBJECT_FILTER);

        System.out.printf("[%s] Searching messages with filter: %s%n",
            METHOD_NAME, combinedFilter);

        // Query messages with combined filter
        MessageCollectionRequest messageRequest = graphClient.users(userPrincipalName)
            .messages()
            .buildRequest()
            .filter(combinedFilter)
            .select("subject,receivedDateTime,body")
            .orderBy("receivedDateTime desc")
            .top(100);

        MessageCollectionPage messagesPage = messageRequest.get();
        int totalMessages = 0;
        Message firstMatch = null;
        Message secondMatch = null;

        // Process all pages if needed
        while (messagesPage != null) {
          List<Message> currentPage = messagesPage.getCurrentPage();
          totalMessages += currentPage.size();

          for (Message message : currentPage) {
            System.out.printf("[%s] Processing email: %s (Received: %s)%n",
                METHOD_NAME, message.subject, message.receivedDateTime);

            // Verify we have body content
            if (message.body == null || message.body.content == null) {
              System.out.printf("[%s] Fetching full content for message: %s%n",
                  METHOD_NAME, message.id);
              message = graphClient.users(userPrincipalName)
                  .messages(message.id)
                  .buildRequest()
                  .select("body")
                  .get();

              if (message.body == null || message.body.content == null) {
                System.out.printf("[%s] Skipping message with empty body%n", METHOD_NAME);
                continue;
              }
            }

            // Track matches
            if (firstMatch == null) {
              firstMatch = message;
              System.out.printf("[%s] Found first match%n", METHOD_NAME);
            } else {
              secondMatch = message;
              System.out.printf("[%s] Found second match, returning%n", METHOD_NAME);
              return secondMatch;
            }
          }

          // Get next page if we haven't found our matches yet
          if (secondMatch == null) {
            MessageCollectionRequestBuilder nextPage = messagesPage.getNextPage();
            if (nextPage != null) {
              messagesPage = nextPage.buildRequest().get();
            } else {
              break;
            }
          } else {
            break;
          }
        }

        System.out.printf("[%s] Processed %d total messages%n", METHOD_NAME, totalMessages);

        if (firstMatch != null && secondMatch == null) {
          System.out.printf("[%s] Only found one matching email%n", METHOD_NAME);
        } else if (firstMatch == null) {
          System.out.printf("[%s] No matching emails found%n", METHOD_NAME);
        }

        return null;

      } catch (Exception e) {
        String errorMsg = String.format("[%s ERROR] Failed to fetch email: %s",
            METHOD_NAME, e.getMessage());
        System.err.println(errorMsg);
        throw new IOException(errorMsg, e);
      }
    }


    public static SchedulingCalendar parseCalendarFromEmail(Message message) {
      Map<String, Map<Integer, List<String>>> result = new LinkedHashMap<>();
      System.out.println("[DEBUG] Starting enhanced email parsing");
      message.receivedDateTime.toLocalDate();

      try {
        if (message == null || message.body == null || message.body.content == null) {
          System.out.println("[ERROR] Invalid message content");
          return null;
        }

        String emailContent = message.body.content;
        Document doc = Jsoup.parse(emailContent);
        System.out.println("[DEBUG] HTML parsed successfully");

        // Process each text section
        Elements textSections = doc.select("body");
        Element section = textSections.get(0);
        System.out.println("Text sections size: " + textSections.size());
        String currentMonth = null;

        String fullText = section.text().replaceAll("\\s+", " ").trim();
        System.out.println("[DEBUG] Processing full text section");
        System.out.println(fullText);
        String[] months = fullText.split("Sat");
        System.out.println("Number of sections: " + months.length);
        String month1 = months[1];
        String month2 = months[2];
        List<String> monthList = findMonths(fullText);
        System.out.println(monthList);
        String monthName1 = monthList.get(0);
        System.out.println("Month 1: " + monthName1);
        String monthName2 = monthList.get(1);
        System.out.println("Month 2: " + monthName2);

        // Split the text while preserving both the delimiters (day numbers) and content
        String[] parts1 = month1.split(
            "(?<!Cycle)(?<!&)(?<!&amp;)(?<!\\(C\\d{1,2}-) (?=\\d{1,2} )");
        Map<Integer, List<String>> dayMap1 = processDayParts(parts1);
        String[] parts2 = month2.split(
            "(?<!Cycle)(?<!&)(?<!&amp;)(?<!\\(C\\d{1,2}-) (?=\\d{1,2} )");
        Map<Integer, List<String>> dayMap2 = processDayParts(parts2);
        System.out.println(dayMap1);
        System.out.println(dayMap2);

        Map<String, Map<Integer, List<String>>> returnMap = new HashMap<>();
        returnMap.put(monthName1, dayMap1);
        returnMap.put(monthName2, dayMap2);
        String receivedDate = message.receivedDateTime.toLocalDate().toString();
        SchedulingCalendar returnObj = new SchedulingCalendar(receivedDate, returnMap);
        return returnObj;

      } catch (Exception e) {
        System.out.println("[ERROR] Error parsing calendar: " + e.getMessage());
        e.printStackTrace();
      }

      return null;
    }

    private static List<String> cleanEvents(List<String> events) {
      List<String> cleanedEvents = new ArrayList<>();
      for (String event : events) {
        // Remove everything starting from the next month pattern
        String[] parts = event.split(
            "(?i)(January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{4}");
        if (parts.length > 0 && !parts[0].trim().isEmpty()) {
          cleanedEvents.add(parts[0].trim());
        }
      }
      return cleanedEvents;
    }

    private static Map<Integer, List<String>> processDayParts(String[] parts) {
      Map<Integer, List<String>> dayMap = new LinkedHashMap<>();
      final String SECURITY_PATTERN = "The information contained in this bulletin";

      for (String part : parts) {
        part = part.trim();
        if (part.isEmpty())
          continue;

        // Split into day number and events text (if any)
        String[] dayAndEvents = part.split("\\s+", 2); // Split on first space only

        try {
          int day = Integer.parseInt(dayAndEvents[0]);
          List<String> events = new ArrayList<>();

          if (dayAndEvents.length > 1) {
            String eventsText = dayAndEvents[1];

            // Remove security disclaimer if present
            int securityIndex = eventsText.indexOf(SECURITY_PATTERN);
            if (securityIndex > 0) {
              eventsText = eventsText.substring(0, securityIndex).trim();
            }

            // Split events by "Cycle" while preserving the delimiter
            String[] eventParts = eventsText.split("(?=Cycle\\s\\d+:)");

            for (String event : eventParts) {
              event = event.trim();
              if (!event.isEmpty()) {
                events.add(event);
              }
            }
          }
          List<String> cleanEvents = cleanEvents(events);
          dayMap.put(day, cleanEvents);
          System.out.println("[DEBUG] Processed day " + day + " with events: " + events);

        } catch (NumberFormatException e) {
          System.out.println("[WARN] Failed to parse day number from: " + part);
        }
      }

      return dayMap;
    }

    public static List<String> findMonths(String fullText) {
      List<String> returnList = new LinkedList<>();
      LinkedList<String> MonthList = new LinkedList<>(Arrays.asList(
          "January",
          "February",
          "March",
          "April",
          "May",
          "June",
          "July",
          "August",
          "September",
          "October",
          "November",
          "December"
      ));

      String[] allWords = fullText.split(" ");
      for (String word : allWords) {
        if (MonthList.contains(word)) {
          returnList.add(word);
          break;
        }
      }

      String firstMonth = returnList.get(0);
      int firstIndex = MonthList.indexOf(firstMonth);
      int nextIndex;

      if (firstIndex != 11) {
        nextIndex = firstIndex + 1;
      } else {
        nextIndex = 0;
      }

      String secondMonth = MonthList.get(nextIndex);
      returnList.add(secondMonth);
      return returnList;
    }
  }
}
