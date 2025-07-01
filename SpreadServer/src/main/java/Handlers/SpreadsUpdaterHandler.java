package Handlers;
import static Outlook.ExplorerParser.getAccessToken;
import static Outlook.FusionCurveParser.extractPdfAttachment;
import static Outlook.FusionCurveParser.fetchCurveReportEmails;
import static Outlook.FusionCurveParser.parseForwardCurvePdf;

import Outlook.ForwardCurveUpdater;
import Outlook.FusionCurveParser.ForwardCurveData;
import com.microsoft.graph.models.Message;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import spark.Request;
import spark.Response;
import spark.Route;

public class SpreadsUpdaterHandler implements Route {
  @Override
  public Object handle(Request request, Response response) {
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

      response.type("application/json");
      return "{\"status\":\"success\"}";

  }}




