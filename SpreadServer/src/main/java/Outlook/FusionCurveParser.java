package Outlook;

import static Outlook.ExplorerParser.getAccessToken;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.models.*;
import com.microsoft.graph.requests.AttachmentCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import okhttp3.Request;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.net.URL;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class FusionCurveParser {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy");

  public static class ForwardCurveData {
    public Map<String, Double> hoNyh = new LinkedHashMap<>();
    public Map<String, Double> rbobNyh = new LinkedHashMap<>();
  }

  private static class SimpleAuthProvider implements IAuthenticationProvider {
    private final String accessToken;

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

      Message targetMessage = fetchCurveReportEmail(accessToken, userPrincipalName);
      byte[] pdfBytes = extractPdfAttachment(accessToken, userPrincipalName, targetMessage);
      ForwardCurveData curveData = parseForwardCurvePdf(pdfBytes);
      OffsetDateTime receivedDateTime = targetMessage.receivedDateTime;
      LocalDate date = receivedDateTime.toLocalDate().minusDays(1);
      ForwardCurveUpdater.updateForwardCurveFiles(date,curveData);
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Error processing curve data: " + e.getMessage());
    }
  }

  public static Message fetchCurveReportEmail(String accessToken, String userPrincipalName) throws IOException {
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<Request> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    MessageCollectionPage messages = graphClient.users(userPrincipalName)
        .messages()
        .buildRequest()
        .select("subject,receivedDateTime")
        .orderBy("receivedDateTime desc")
        .top(20)
        .get();

    if (messages == null || messages.getCurrentPage().isEmpty()) {
      throw new IOException("No emails found in the mailbox");
    }

    // Debug: Print all subjects
    System.out.println("===== Email Subjects in Inbox =====");
    for (Message message : messages.getCurrentPage()) {
      System.out.println("- Subject: " + (message.subject != null ? message.subject : "[No Subject]") +
          " | Received: " + message.receivedDateTime);
    }
    System.out.println("===================================");

    for (Message message : messages.getCurrentPage()) {
      if (message.subject != null && message.subject.toLowerCase().contains("curve report")) {
        System.out.println("Found matching email: " + message.subject);
        return message;
      }
    }

    throw new IOException("No email containing 'curve report' in subject found");
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