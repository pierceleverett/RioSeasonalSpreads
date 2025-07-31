package Outlook;

import static Outlook.ExplorerParser.getAccessToken;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.*;
import java.util.List;
import java.util.stream.Collectors;
import okhttp3.Request;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.*;

public class FusionCurveParser {
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy");

  public static final Map<String, Integer> MONTH_MAP = Map.ofEntries(
      Map.entry("Jan", 1),
      Map.entry("Feb", 2),
      Map.entry("Mar", 3),
      Map.entry("Apr", 4),
      Map.entry("May", 5),
      Map.entry("Jun", 6),
      Map.entry("Jul", 7),
      Map.entry("Aug", 8),
      Map.entry("Sep", 9),
      Map.entry("Oct", 10),
      Map.entry("Nov", 11),
      Map.entry("Dec", 12)
  );

  public static class ForwardCurveData {
    public Map<String, Double> hoNyh = new LinkedHashMap<>();
    public Map<String, Double> rbobNyh = new LinkedHashMap<>();
  }

  public static class SimpleAuthProvider implements IAuthenticationProvider {
    public final String accessToken;

    public SimpleAuthProvider(String accessToken) {
      this.accessToken = accessToken;
    }

    @Override
    public CompletableFuture<String> getAuthorizationTokenAsync(URL requestUrl) {
      return CompletableFuture.completedFuture(accessToken);
    }
  }

  public static void main(String[] args) {
    try {
      String accessToken = getAccessToken();
      String userPrincipalName = "automatedreports@rioenergy.com";
      String csvPath = "data/spreads/RBOB2025.csv";
      System.out.println("attempting to fetch messages");
      List<Message> messages = fetchCurveReportEmails(accessToken, userPrincipalName, csvPath);
      System.out.println("fetched messages");
      for (Message message : messages) {
        byte[] pdfBytes = extractPdfAttachment(accessToken, userPrincipalName, message);
        System.out.println("extracted pdf");
        ForwardCurveData curveData = parseForwardCurvePdf(pdfBytes);
        System.out.println("got curved data");
        OffsetDateTime receivedDateTime = message.receivedDateTime;
        System.out.println("message time: " + receivedDateTime);
        LocalDate date = receivedDateTime.toLocalDate().minusDays(1);
        System.out.println("data day: " + date);
        ForwardCurveUpdater.updateForwardCurveFiles(date, curveData);
        System.out.println("updated curves");
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Error processing curve data: " + e.getMessage());
    }
  }

  public static List<Message> fetchCurveReportEmails(String accessToken, String userPrincipalName, String csvPath) throws IOException {
    LocalDate lastDateInCsv = readLastDateFromCsv(csvPath);
    System.out.println("Last day in csv: " + lastDateInCsv);
    LocalDate yesterday = LocalDate.now().minusDays(1);
    System.out.println("Yestreday: " + yesterday);

    if (!lastDateInCsv.isBefore(yesterday)) {
      System.out.println("No missing dates. Data is up to date.");
      return Collections.emptyList();
    }

    Set<LocalDate> missingDates = lastDateInCsv.datesUntil(yesterday.plusDays(1)).collect(
        Collectors.toSet());
    missingDates.remove(lastDateInCsv);
    System.out.println("Missing dates: " + missingDates);

    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<Request> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    List<Message> relevantMessages = new ArrayList<>();
    MessageCollectionPage messagesPage = null;
    MessageCollectionRequestBuilder nextPage = null;
    boolean allDatesFound = false;

    do {
      messagesPage = (nextPage == null)
          ? graphClient.users(userPrincipalName).messages()
          .buildRequest()
          .select("subject,receivedDateTime")
          .orderBy("receivedDateTime desc")
          .top(50)
          .get()
          : nextPage.buildRequest().get();

      for (Message message : messagesPage.getCurrentPage()) {
        if (message.subject != null && message.subject.toLowerCase().contains("curve report")) {
          System.out.println("found email with curve report");

          LocalDate dataDate = message.receivedDateTime.toLocalDate().minusDays(1);
          if (missingDates.contains(dataDate)) {
              relevantMessages.add(message);
              missingDates.remove(dataDate);
          }

            if (missingDates.isEmpty()) {
              allDatesFound = true;
              break;
            }
          }
        }


      nextPage = messagesPage.getNextPage();

    } while (nextPage != null && !allDatesFound);


    if (relevantMessages.isEmpty()) {
      throw new IOException("No relevant 'curve report' emails found for missing dates.");
    }

    return relevantMessages;
  }

  public static List<Message> fetchRecentCurveReportEmails(String accessToken, String userPrincipalName) throws IOException {
    final String METHOD_NAME = "fetchRecentCurveReportEmails";
    System.out.printf("[%s] Starting with user: %s%n", METHOD_NAME, userPrincipalName);
    List<Message> matchingEmails = new ArrayList<>();

    try {
      // Initialize Graph client
      IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
      GraphServiceClient<?> graphClient = GraphServiceClient.builder()
          .authenticationProvider(authProvider)
          .buildClient();

      // Create date filter for last 5 days
      OffsetDateTime searchStartDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5);
      String dateFilter = String.format("receivedDateTime ge %s",
          searchStartDate.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
      String subjectFilter = String.format("contains(subject, '%s')", "Fwd Curve Report");
      String combinedFilter = String.format("%s and %s", dateFilter, subjectFilter);

      System.out.printf("[%s] Searching messages with filter: %s%n", METHOD_NAME, combinedFilter);

      // Query messages with pagination
      MessageCollectionPage messagesPage = graphClient.users(userPrincipalName)
          .messages()
          .buildRequest()
          .filter(combinedFilter)
          .select("subject,receivedDateTime,body")
          .orderBy("receivedDateTime desc")
          .top(100)
          .get();

      // Process all pages
      while (messagesPage != null) {
        for (Message message : messagesPage.getCurrentPage()) {
          System.out.printf("[%s] Found matching email: %s (Received: %s)%n",
              METHOD_NAME, message.subject, message.receivedDateTime);

          // Ensure we have message body content
          if (message.body == null || message.body.content == null) {
            System.out.printf("[%s] Fetching full content for message: %s%n",
                METHOD_NAME, message.id);
            message = graphClient.users(userPrincipalName)
                .messages(message.id)
                .buildRequest()
                .select("body")
                .get();
          }

          matchingEmails.add(message);
        }

        // Get next page if available
        MessageCollectionRequestBuilder nextPage = messagesPage.getNextPage();
        if (nextPage == null) {
          break;
        }
        messagesPage = nextPage.buildRequest().get();
      }

      System.out.printf("[%s] Found %d matching emails in last 5 days%n",
          METHOD_NAME, matchingEmails.size());

    } catch (Exception e) {
      String errorMsg = String.format("[%s ERROR] Failed to fetch emails: %s",
          METHOD_NAME, e.getMessage());
      System.err.println(errorMsg);
      throw new IOException(errorMsg, e);
    }

    return matchingEmails;
  }

  private static LocalDate readLastDateFromCsv(String csvPath) throws IOException {
    List<String> lines = Files.readAllLines(Paths.get(csvPath));
    System.out.println("read all lines, size: " + lines.size());

    // Find the last non-empty line
    String lastLine = null;
    for (int i = lines.size() - 1; i >= 0; i--) {
      if (!lines.get(i).trim().isEmpty()) {
        lastLine = lines.get(i);
        break;
      }
    }

    if (lastLine == null) {
      throw new IOException("CSV file is empty or contains only blank lines.");
    }

    System.out.println("Last line: " + lastLine);
    String[] parts = lastLine.split(",");
    return LocalDate.parse(parts[0], DateTimeFormatter.ofPattern("M/d/yyyy"));
  }


  private static LocalDate extractDateFromPDFName(String subject) {
    try {
      String[] words = subject.split("_");
      Integer day = Integer.parseInt(words[1]);
      Integer month = MONTH_MAP.get(words[2]);
      Integer year = Integer.parseInt(words[3]);
      return LocalDate.of(year, month, day);
    } catch (Exception e) {
      System.err.println("Failed to parse date from subject: " + subject);
    }
    return null;
  }

  public static byte[] extractPdfAttachment(String accessToken, String userPrincipalName, Message message) throws IOException {
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<Request> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    AttachmentCollectionPage attachments = graphClient.users(userPrincipalName)
        .messages(message.id)
        .attachments()
        .buildRequest()
        .get();

    if (attachments == null || attachments.getCurrentPage().isEmpty()) {
      throw new IOException("No attachments found in the email");
    }

    for (Attachment attachment : attachments.getCurrentPage()) {
      if (attachment instanceof FileAttachment &&
          attachment.name != null &&
          attachment.name.toLowerCase().endsWith(".pdf")) {
        byte[] content = ((FileAttachment) attachment).contentBytes;
        if (content == null || content.length == 0) {
          throw new IOException("PDF attachment is empty");
        }
        return content;
      }
    }

    throw new IOException("No PDF attachment found in the email");
  }

  public static LocalDate extractDateFromAttachment(String accessToken, String userPrincipalName, Message message) throws IOException {
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<Request> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    AttachmentCollectionPage attachments = graphClient.users(userPrincipalName)
        .messages(message.id)
        .attachments()
        .buildRequest()
        .get();

    if (attachments == null || attachments.getCurrentPage().isEmpty()) {
      throw new IOException("No attachments found in the email");
    }

    for (Attachment attachment : attachments.getCurrentPage()) {
      if (attachment instanceof FileAttachment &&
          attachment.name != null &&
          attachment.name.toLowerCase().endsWith(".pdf")) {
        LocalDate date = extractDateFromPDFName(attachment.name);
        if (date == null) {
          throw new IOException("PDF attachment is empty");
        }
        return date;
      }
    }

    throw new IOException("No PDF attachment found in the email");
  }

  public static ForwardCurveData parseForwardCurvePdf(byte[] pdfBytes) throws IOException {
    ForwardCurveData result = new ForwardCurveData();
    try (PDDocument document = PDDocument.load(pdfBytes)) {
      String text = new PDFTextStripper().getText(document);
      Pattern pattern = Pattern.compile("(\\w{3}/\\d{4})\\s+([\\d.]+)(?:\\s+([\\d.]+))?");
      Matcher matcher = pattern.matcher(text);
      while (matcher.find()) {
        String date = matcher.group(1);
        try {
          double hoValue = Double.parseDouble(matcher.group(2));
          result.hoNyh.put(date, hoValue);
          if (matcher.group(3) != null) {
            double rbobValue = Double.parseDouble(matcher.group(3));
            result.rbobNyh.put(date, rbobValue);
          }
        } catch (NumberFormatException e) {
          System.err.println("Skipping invalid number format for date: " + date);
        }
      }
    }

    if (result.hoNyh.isEmpty() && result.rbobNyh.isEmpty()) {
      throw new IOException("No valid curve data found in the PDF");
    }

    return result;
  }
}
