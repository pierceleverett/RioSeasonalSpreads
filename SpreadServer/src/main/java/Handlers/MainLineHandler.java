package Handlers;

import static GCSpreads.GCSpreadCalc.computeDifference;
import static Noms.MainLine.processMainLineDates;

import Noms.MainLine;
import Noms.MainLine.ClerkHolidayService;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import spark.Request;
import spark.Response;
import spark.Route;

public class MainLineHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
    String userID = request.queryParams("userid");
    if (userID == null) {
      throw new IOException("You must input a userID");
    }

    ClerkHolidayService service = new ClerkHolidayService("sk_test_1qlrksRhhWCq5JoqgdF5oCOMdl3paX4vn6D4EAGhkf");
    Set<LocalDate> holidays = service.getUserHolidays(userID);
    MainLine.HOLIDAYS = holidays;
    MainLine.MainLineData mainLineData = MainLine.extractLatestMainLineData();
    Map<String, Map<String, String>> processedDates = processMainLineDates(mainLineData);

    Moshi moshi = new Moshi.Builder().build();
    Type innerMapType = Types.newParameterizedType(Map.class, String.class, String.class);
    Type outerMapType = Types.newParameterizedType(Map.class, String.class, innerMapType);
    JsonAdapter<Map<String, Map<String, String>>> adapter = moshi.adapter(outerMapType);

    return adapter.toJson(processedDates);
  }
}
