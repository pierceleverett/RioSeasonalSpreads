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
      String rollForwardParam = request.queryParams("rollforward");

      if (startMonth == null || endMonth == null) {
        throw new IOException("Please input months");
      }

      // Get current year
      int currentYear = Year.now().getValue();

      // Track if we automatically rolled forward
      boolean wasRollForwardParamNull = (rollForwardParam == null);
      boolean automaticallyRolledForward = false;

      // Determine if we should roll forward
      boolean shouldRollForward;
      if (rollForwardParam == null) {
        // If rollforward param is null, use the shouldRollForward logic
        shouldRollForward = shouldRollForward(startMonth, endMonth, currentYear);
        automaticallyRolledForward = shouldRollForward; // Only automatic if param was null AND we decided to roll
      } else {
        // If rollforward param is provided, use its boolean value
        shouldRollForward = Boolean.parseBoolean(rollForwardParam);
        automaticallyRolledForward = false; // Not automatic if parameter was explicitly set
      }

      System.out.println("Roll forward: " + shouldRollForward);
      System.out.println("Automatically rolled forward: " + automaticallyRolledForward);

      // Get current year and month for validation
      LocalDate today = LocalDate.now();
      int currentMonth = today.getMonthValue();

      // Convert month codes to numbers for comparison
      Map<String, Integer> monthToInt = Map.ofEntries(
          Map.entry("F", 1), Map.entry("G", 2), Map.entry("H", 3), Map.entry("J", 4),
          Map.entry("K", 5), Map.entry("M", 6), Map.entry("N", 7), Map.entry("Q", 8),
          Map.entry("U", 9), Map.entry("V", 10), Map.entry("X", 11), Map.entry("Z", 12)
      );

      int startMonthInt = monthToInt.get(startMonth);
      int endMonthInt = monthToInt.get(endMonth);

      // For rolling spreads (start month > end month), validate they're forward-looking
      if (startMonthInt > endMonthInt) {
        if (startMonthInt <= currentMonth) {
          throw new IOException("Invalid rolling spread: Start month " + startMonth +
              " must be after current month " + currentMonth);
        }
      }

      ArrayList<String> yearList = new ArrayList<>();

      // Calculate spreads for each historical year
      // Let SpreadCalculator handle the cross-year logic internally
      Map<String, Float> spreadMap1 = getSpreadForBaseYear(commodity, startMonth, endMonth, currentYear - 4, shouldRollForward);
      yearList.add(String.valueOf(currentYear - 5));

      Map<String, Float> spreadMap2 = getSpreadForBaseYear(commodity, startMonth, endMonth, currentYear - 3, shouldRollForward);
      yearList.add(String.valueOf(currentYear - 4));

      Map<String, Float> spreadMap3 = getSpreadForBaseYear(commodity, startMonth, endMonth, currentYear - 2, shouldRollForward);
      yearList.add(String.valueOf(currentYear - 3));

      Map<String, Float> spreadMap4 = getSpreadForBaseYear(commodity, startMonth, endMonth, currentYear - 1, shouldRollForward);
      yearList.add(String.valueOf(currentYear - 2));

      Map<String, Float> spreadMap5 = getSpreadForBaseYear(commodity, startMonth, endMonth, currentYear, shouldRollForward);
      yearList.add(String.valueOf(currentYear - 1));

      // Calculate current year spread - apply roll forward logic if needed
      Map<String, Float> spreadMap6;
      int currentDisplayYear = currentYear;
      if (shouldRollForward) {
        // If rolling forward, use next year as the base year
        currentDisplayYear = currentYear + 1;
        System.out.println("Using rolled forward data for current year (base year: " + currentDisplayYear + ")");
      } else {
        System.out.println("Using current year data (base year: " + currentDisplayYear + ")");
      }
      spreadMap6 = spreadCalculator(commodity, startMonth, endMonth, String.valueOf(currentDisplayYear));
      yearList.add(String.valueOf(currentYear)); // Keep the label as current year for display

      System.out.println("all data gathered");

      // Prepare the response map with a wrapper that includes metadata
      Map<String, Object> responseWrapper = new LinkedHashMap<>();

      // Add the main spreads data
      Map<String, Map<String, Float>> allYearSpreads = new LinkedHashMap<>();
      allYearSpreads.put(yearList.get(0), spreadMap1);
      allYearSpreads.put(yearList.get(1), spreadMap2);
      allYearSpreads.put(yearList.get(2), spreadMap3);
      allYearSpreads.put(yearList.get(3), spreadMap4);
      allYearSpreads.put(yearList.get(4), spreadMap5);

      // Calculate 5-year average (using the last 5 years)
      Map<String, Float> fiveyearavg = AvgCalc(allYearSpreads, yearList);
      System.out.println("5 year avg calculated");

      // Add the current year (which may be rolled forward)
      allYearSpreads.put(yearList.get(5), spreadMap6);
      allYearSpreads.put("5YEARAVG", fiveyearavg);

      // Add the spreads to the response wrapper
      responseWrapper.put("spreads", allYearSpreads);

      // Add metadata including roll forward information
      responseWrapper.put("RolledForward", automaticallyRolledForward);
      responseWrapper.put("RollForwardRequested", shouldRollForward);
      responseWrapper.put("RollForwardParamWasNull", wasRollForwardParamNull);

      System.out.println("final map created");

      // Set up Moshi for JSON serialization
      Moshi moshi = new Moshi.Builder().build();
      Type responseWrapperType = Types.newParameterizedType(Map.class, String.class, Object.class);
      JsonAdapter<Map<String, Object>> adapter = moshi.adapter(responseWrapperType);

      return adapter.toJson(responseWrapper);

    } catch (Exception e) {
      System.err.println("ERROR RETURNING MAP");
      e.printStackTrace();
      response.status(500);
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }

  /**
   * Gets spread for a base year, applying roll forward logic to historical years if needed
   * to maintain consistency with the current year roll forward decision
   */
  private Map<String, Float> getSpreadForBaseYear(String commodity, String startMonth, String endMonth,
      int baseYear, boolean shouldRollForward) throws Exception {
    // For historical years, we don't apply roll forward logic
    // because we want to compare the same relative time periods
    // SpreadCalculator will handle cross-year spreads internally

    return spreadCalculator(commodity, startMonth, endMonth, String.valueOf(baseYear));
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
    boolean shouldRoll = today.isAfter(expirationDate);
    System.out.println("Contract expiration: " + expirationDate + ", Today: " + today + ", Should roll: " + shouldRoll);
    return shouldRoll;
  }
}