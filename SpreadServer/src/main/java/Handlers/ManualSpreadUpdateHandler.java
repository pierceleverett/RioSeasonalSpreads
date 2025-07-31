package Handlers;

import static Outlook.ExplorerParser.getAccessToken;
import static Outlook.FusionCurveParser.extractDateFromAttachment;
import static Outlook.FusionCurveParser.extractPdfAttachment;
import static Outlook.FusionCurveParser.fetchCurveReportEmails;
import static Outlook.FusionCurveParser.fetchRecentCurveReportEmails;
import static Outlook.FusionCurveParser.parseForwardCurvePdf;

import Outlook.ForwardCurveUpdater;
import Outlook.FusionCurveParser.ForwardCurveData;
import com.microsoft.graph.models.Message;
import java.io.IOException;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import spark.Request;
import spark.Response;
import spark.Route;

public class ManualSpreadUpdateHandler implements Route {
  @Override
  public Object handle(Request request, Response response) {
    try {
      String dataDateString = request.queryParams("date");
      System.out.println("Date String: " + dataDateString);
      String[] stringParts = dataDateString.split("-");
      Integer month = Integer.parseInt(stringParts[0]);
      Integer day = Integer.parseInt(stringParts[1]);
      Integer year = Integer.parseInt(stringParts[2]);
      LocalDate dataDate = LocalDate.of(year, month, day);
      System.out.println("Local date: " + dataDate);

      String accessToken = getAccessToken();
      String userPrincipalName = "automatedreports@rioenergy.com";
      String currentYear = Integer.toString(Year.now().getValue());
      String csvPath = "data/spreads/RBOB" + currentYear + ".csv";
      System.out.println("attempting to fetch messages");
      List<Message> messages = fetchRecentCurveReportEmails(accessToken, userPrincipalName);
      System.out.println("fetched messages");
      for (Message message : messages) {
        LocalDate date = extractDateFromAttachment(accessToken, userPrincipalName, message).minusDays(1);
        System.out.println("Date from email to match: " + date);
        if (date.equals(dataDate)) {
          System.out.println("found a matching email for date");
          byte[] pdfBytes = extractPdfAttachment(accessToken, userPrincipalName, message);
          System.out.println("extracted pdf");
          ForwardCurveData curveData = parseForwardCurvePdf(pdfBytes);
          System.out.println("got curved data");
          System.out.println("data day: " + date);
          ForwardCurveUpdater.updateForwardCurveFiles(date, curveData);
          System.out.println("updated curves");
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
      System.err.println("Error processing curve data: " + e.getMessage());
    }

    response.type("application/json");
    return "{\"status\":\"success\"}";

  }}