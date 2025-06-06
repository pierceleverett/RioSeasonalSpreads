package Handlers;

import static Utilities.FiveYearAvgCalc.AvgCalc;
import static Utilities.SpreadCalculator.spreadCalculator;

import Utilities.FiveYearAvgCalc;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;
import Utilities.FiveYearAvgCalc;

public class SpreadHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
    try {
      System.out.println("entered handler");
      String startMonth = request.queryParams("startMonth");
      String endMonth = request.queryParams("endMonth");
      String commodity = request.queryParams("commodity");
      if (startMonth == null || endMonth == null) {
        throw new IOException("Please input months");
      }

      Map<String, Float> spreadMap2020 = spreadCalculator(commodity, startMonth, endMonth, "2020");
      System.out.println("2020 spread calculated");
      Map<String, Float> spreadMap2021 = spreadCalculator(commodity, startMonth, endMonth, "2021");
      System.out.println("2021 spread calculated");
      Map<String, Float> spreadMap2022 = spreadCalculator(commodity, startMonth, endMonth, "2022");
      System.out.println("2022 spread calculated");
      Map<String, Float> spreadMap2023 = spreadCalculator(commodity, startMonth, endMonth, "2023");
      System.out.println("2023 spread calculated");
      Map<String, Float> spreadMap2024 = spreadCalculator(commodity, startMonth, endMonth, "2024");
      System.out.println("2024 spread calculated");
      Map<String, Float> spreadMap2025 = spreadCalculator(commodity, startMonth, endMonth, "2025");
      System.out.println("all data gathered");
      Map<String, Map<String, Float>> allYearSpreads = new LinkedHashMap<>();
      allYearSpreads.put("2020", spreadMap2020);
      allYearSpreads.put("2021", spreadMap2021);
      allYearSpreads.put("2022", spreadMap2022);
      allYearSpreads.put("2023", spreadMap2023);
      allYearSpreads.put("2024", spreadMap2024);
      Map<String, Float> fiveyearavg = AvgCalc(allYearSpreads);
      System.out.println("5 year avg calculated");
      allYearSpreads.put("2025", spreadMap2025);
      allYearSpreads.put("5YEARAVG", fiveyearavg);

      System.out.println("final map created");


      // Set up Moshi
      Moshi moshi = new Moshi.Builder().build();

      // Define the correct type for Map<String, Float>
      Type innerMapType = Types.newParameterizedType(Map.class, String.class, Float.class);
      Type outerMapType = Types.newParameterizedType(Map.class, String.class, innerMapType);
      JsonAdapter<Map<String, Map<String, Float>>> adapter = moshi.adapter(outerMapType);


      // Convert map to JSON string
      return adapter.toJson(allYearSpreads);



    } catch (Exception e) {
      System.err.println("ERROR RETURNING MAP");
      e.printStackTrace();
      response.status(500);
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }
}




