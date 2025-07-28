package Handlers;

import static Noms.MainLine.fetchMostRecentMainLineEmail;
import static Noms.MainLine.parseMainLineEmail;
import static Outlook.ExplorerParser.getAccessToken;

import Noms.MainLine.MainLineData;
import Utilities.MainLineDataJsonAdapter;
import com.microsoft.graph.models.Message;
import com.squareup.moshi.Moshi;
import java.io.IOException;
import spark.Request;
import spark.Response;
import spark.Route;

public class RecentDateInfoHandler implements Route {
  private final Moshi moshi;

  public RecentDateInfoHandler() {
    this.moshi = new Moshi.Builder()
        .add(new MainLineDataJsonAdapter.Factory())
        .build();
  }

  public Object handle(Request request, Response response) throws Exception {
    try {
      // Get data
      String accessToken = getAccessToken();
      Message recentMessage = fetchMostRecentMainLineEmail(accessToken, "automatedreports@rioenergy.com");
      MainLineData dateInfo = parseMainLineEmail(recentMessage);

      // Set response type to JSON
      response.type("application/json");

      // Convert to JSON
      return moshi.adapter(MainLineData.class).toJson(dateInfo);

    } catch (IOException e) {
      e.printStackTrace();
      response.status(500);
      return "{\"error\":\"Failed to process request\"}";
    }
  }
}