package Handlers;

import static Utilities.FiveYearAvgCalc.AvgCalc;
import static Utilities.SpreadCalculator.spreadCalculator;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class SpreadHandler implements Route {
  // Month configuration map
  private static final Map<String, String[]> MONTH_CONFIG = new HashMap<>();
  static {
    MONTH_CONFIG.put("F", new String[]{"01", "01", "12", "31", "-1", "-1"}); // Jan-Dec (prev year)
    MONTH_CONFIG.put("G", new String[]{"02", "01", "01", "31", "-1", "0"});  // Feb (prev year) - Jan (current)
    MONTH_CONFIG.put("H", new String[]{"03", "01", "02", "28", "-1", "0"});  // Mar (prev year) - Feb (current)
    MONTH_CONFIG.put("J", new String[]{"04", "01", "03", "31", "-1", "0"});  // Apr (prev year) - Mar (current)
    MONTH_CONFIG.put("K", new String[]{"05", "01", "04", "30", "-1", "0"});  // May (prev year) - Apr (current)
    MONTH_CONFIG.put("M", new String[]{"06", "01", "05", "31", "-1", "0"});  // Jun (prev year) - May (current)
    MONTH_CONFIG.put("N", new String[]{"07", "01", "06", "30", "-1", "0"});  // Jul (prev year) - Jun (current)
    MONTH_CONFIG.put("Q", new String[]{"08", "01", "07", "31", "-1", "0"});  // Aug (prev year) - Jul (current)
    MONTH_CONFIG.put("U", new String[]{"09", "01", "08", "31", "-1", "0"});  // Sep (prev year) - Aug (current)
    MONTH_CONFIG.put("V", new String[]{"10", "01", "09", "30", "-1", "0"});  // Oct (prev year) - Sep (current)
    MONTH_CONFIG.put("X", new String[]{"11", "01", "10", "31", "-1", "0"});  // Nov (prev year) - Oct (current)
    MONTH_CONFIG.put("Z", new String[]{"12", "01", "11", "30", "-1", "0"});  // Dec (prev year) - Nov (current)
  }

  public Object handle(Request request, Response response) throws Exception {
    try {
      System.out.println("entered handler");
      String startMonth = request.queryParams("startMonth");
      String endMonth = request.queryParams("endMonth");
      String commodity = request.queryParams("commodity");

      if (startMonth == null || endMonth == null) {
        throw new IOException("Please input months");
      }

      // Get current year and check if we need to roll forward
      int currentYear = Year.now().getValue();
      boolean shouldRollForward = shouldRollForward(startMonth, endMonth, currentYear);

      // Calculate base year offset (0 for current, 1 for next year if rolled forward)
      int baseYearOffset = shouldRollForward ? 1 : 0;
      ArrayList<String> yearList = new ArrayList<>();

      // Calculate spreads for each year with proper rollover handling
      Map<String, Float> spreadMap1 = spreadCalculator(commodity, startMonth, endMonth,
          String.valueOf(2020 + baseYearOffset));
      yearList.add(String.valueOf(2020 + baseYearOffset));

      Map<String, Float> spreadMap2 = spreadCalculator(commodity, startMonth, endMonth,
          String.valueOf(2021 + baseYearOffset));
          yearList.add(String.valueOf(2021 + baseYearOffset));

      Map<String, Float> spreadMap3 = spreadCalculator(commodity, startMonth, endMonth,
          String.valueOf(2022 + baseYearOffset));
          yearList.add(String.valueOf(2022 + baseYearOffset));

      Map<String, Float> spreadMap4 = spreadCalculator(commodity, startMonth, endMonth,
          String.valueOf(2023 + baseYearOffset));
          yearList.add(String.valueOf(2023 + baseYearOffset));

      Map<String, Float> spreadMap5 = spreadCalculator(commodity, startMonth, endMonth,
          String.valueOf(2024 + baseYearOffset));
          yearList.add(String.valueOf(2024 + baseYearOffset));

      Map<String, Float> spreadMap6 = spreadCalculator(commodity, startMonth, endMonth,
          String.valueOf(2025 + baseYearOffset));
          yearList.add(String.valueOf(2025 + baseYearOffset));

      System.out.println("all data gathered");

      // Prepare the response map
      Map<String, Map<String, Float>> allYearSpreads = new LinkedHashMap<>();
      allYearSpreads.put(yearList.get(0), spreadMap1);
      allYearSpreads.put(yearList.get(1), spreadMap2);
      allYearSpreads.put(yearList.get(2), spreadMap3);
      allYearSpreads.put(yearList.get(3), spreadMap4);
      allYearSpreads.put(yearList.get(4), spreadMap5);

      // Calculate 5-year average (using the last 5 years)
      Map<String, Float> fiveyearavg = AvgCalc(allYearSpreads, yearList);
      System.out.println("5 year avg calculated");

      // Add the new year if rolled forward
        allYearSpreads.put(yearList.get(5), spreadMap6);


      allYearSpreads.put("5YEARAVG", fiveyearavg);

      System.out.println("final map created");

      // Set up Moshi for JSON serialization
      Moshi moshi = new Moshi.Builder().build();
      Type innerMapType = Types.newParameterizedType(Map.class, String.class, Float.class);
      Type outerMapType = Types.newParameterizedType(Map.class, String.class, innerMapType);
      JsonAdapter<Map<String, Map<String, Float>>> adapter = moshi.adapter(outerMapType);

      return adapter.toJson(allYearSpreads);

    } catch (Exception e) {
      System.err.println("ERROR RETURNING MAP");
      e.printStackTrace();
      response.status(500);
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }

  /**
   * Determines if the spread should roll forward to the next year's contract
   * based on the current date and the spread's expiration.
   */
  private boolean shouldRollForward(String startMonth, String endMonth, int currentYear) {
    if (!MONTH_CONFIG.containsKey(startMonth) || !MONTH_CONFIG.containsKey(endMonth)) {
      return false;
    }

    String[] startConfig = MONTH_CONFIG.get(startMonth);
    // Get the end month's expiration date (month/day from config)
    int yearAdj = Integer.parseInt(startConfig[5]);
    int expirationYear = currentYear + yearAdj;
    int expirationMonth = Integer.parseInt(startConfig[2]);
    int expirationDay = Integer.parseInt(startConfig[3]);


    // Create expiration date for current year
    LocalDate expirationDate = LocalDate.of(expirationYear, expirationMonth, expirationDay);
    LocalDate today = LocalDate.now();

    // If today is after expiration date, we should roll forward
    System.out.println(today.isAfter(expirationDate));
    return today.isAfter(expirationDate);
  }
}