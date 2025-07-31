package Handlers;

import static GCSpreads.GCSpreadUpdater.getLocalDate;
import static GCSpreads.GCSpreadUpdater.getPricingDataForDate;
import static Outlook.ExplorerParser.getAccessToken;

import GCSpreads.GCcsvupdater;
import Outlook.FusionCurveParser.SimpleAuthProvider;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.requests.GraphServiceClient;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class ManualGCUpdater implements Route {

  public Object handle(Request request, Response response) throws Exception {
    String date = request.queryParams("date");
    LocalDate inputDate = getLocalDate(date);
    System.out.println("Looking for data for: " + date);

    String accessToken = getAccessToken();
    IAuthenticationProvider authProvider = new SimpleAuthProvider(accessToken);
    GraphServiceClient<?> graphClient = GraphServiceClient.builder()
        .authenticationProvider(authProvider)
        .buildClient();

    Map<String, Double> data = getPricingDataForDate(graphClient, inputDate);
    System.out.println("Found data, writing to csv");
    GCcsvupdater.updateSpreadCSVs(data, inputDate.plusDays(1));
    return "Success";
  }

}
