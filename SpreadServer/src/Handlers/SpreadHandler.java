package Handlers;
import static Utilities.SpreadCalculator.spreadCalculator;

import Utilities.SpreadCalculator;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class GraphHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
    try {
      String startMonth = request.queryParams("startMonth");
      String endMonth = request.queryParams("endMonth");
      if (startMonth == null || endMonth == null) {
        throw new IOException("Please input months");
      }

      Map<String, Float> spreadMap = spreadCalculator(startMonth, endMonth, "2023");

      Moshi moshi = new Moshi.Builder().build();
      JsonAdapter<Map<String, Float>> adapter = moshi.adapter(Map<String, Float>.class);
      return spreadMap.toJson();



    } catch (Exception e) {
      System.err.println("ERROR GRAPH HANDLER:");
      e.printStackTrace();
      response.status(500);
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }
}




