package Handlers;

import static Outlook.ExplorerParser.getAccessToken;
import static Outlook.ExplorerSchedulingPackager.eventsByCycle;
import static Outlook.ExplorerSchedulingPackager.getStartDates;

import Outlook.ExplorerSchedulingCalendar.SchedulingCalendar;
import com.microsoft.graph.models.Message;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class ExplorerSchedulingHandler implements Route {


  public static String convertReturnMapToJson(Map<String, Map<Integer, Map<String, String>>> returnMap) {
    Moshi moshi = new Moshi.Builder().build();

    Type type = Types.newParameterizedType(
        Map.class,
        String.class,
        Types.newParameterizedType(
            Map.class,
            Integer.class,
            Types.newParameterizedType(
                Map.class,
                String.class,
                String.class
            )
        )
    );

    JsonAdapter<Map<String, Map<Integer, Map<String, String>>>> adapter = moshi.adapter(type);
    return adapter.toJson(returnMap);
  }


  public Object handle(Request request, Response response) throws Exception {
    String accessToken = getAccessToken();
    Message inputMessage = SchedulingCalendar.fetchMostRecentSchedulingCalendarEmail(accessToken,
        "automatedreports@rioenergy.com");
    SchedulingCalendar inputCal = SchedulingCalendar.parseCalendarFromEmail(inputMessage);
    Map<Integer, Map<String, List<String>>> cycleMap = eventsByCycle(inputCal);
    Map<String, Map<Integer, Map<String, String>>> returnMap = getStartDates(cycleMap);
    return convertReturnMapToJson(returnMap);
  }
}
