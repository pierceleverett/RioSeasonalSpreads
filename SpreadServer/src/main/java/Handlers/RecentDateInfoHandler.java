package Handlers;

import static Noms.MainLine.fetchMostRecentMainLineEmail;
import static Noms.MainLine.fetchSecondMostRecentDateInfoEmail;
import static Noms.MainLine.parseMainLineEmail;
import static Outlook.ExplorerParser.getAccessToken;

import Noms.MainLine.MainLineData;
import Utilities.MainLineDataJsonAdapter;
import com.microsoft.graph.models.Message;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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
      Message secondMessage = fetchSecondMostRecentDateInfoEmail(accessToken, "automatedreports@rioenergy.com");
      MainLineData dateInfo = parseMainLineEmail(recentMessage);
      MainLineData secondDateInfo = parseMainLineEmail(secondMessage);
      Map<String, MainLineData> returnMap = new HashMap<>();
      returnMap.put("Recent Data", dateInfo);
      returnMap.put("Old Data", secondDateInfo);

      // Set response type to JSON
      response.type("application/json");

      // Convert to JSON
      Moshi moshi = new Moshi.Builder()
          .add(new MainLineDataJsonAdapter.Factory())
          .build();

      JsonAdapter<Map<String, MainLineData>> adapter = moshi.adapter(
          Types.newParameterizedType(Map.class, String.class, MainLineData.class));

      return adapter.toJson(returnMap);

    } catch (IOException e) {
      e.printStackTrace();
      response.status(500);
      return "{\"error\":\"Failed to process request\"}";
    }
  }
}