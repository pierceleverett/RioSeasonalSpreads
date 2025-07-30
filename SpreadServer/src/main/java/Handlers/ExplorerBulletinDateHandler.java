package Handlers;

import static Outlook.ExplorerParser.getAccessToken;
import static Outlook.ExplorerSchedulingCalendar.SchedulingCalendar.fetchMostRecentSchedulingCalendarEmail;
import static Outlook.ExplorerSchedulingCalendar.SchedulingCalendar.fetchSecondMostRecentCalendarEmail;

import Outlook.ExplorerSchedulingCalendar;
import com.microsoft.graph.models.Message;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class ExplorerBulletinDateHandler implements Route {

  String mapToJson(Map<String, String> map) throws IOException {
    Moshi moshi = new Moshi.Builder().build();
    Type type = Types.newParameterizedType(Map.class, String.class, String.class);
    JsonAdapter<Map<String, String>> adapter = moshi.adapter(type);
    return adapter.toJson(map);
  }
  public Object handle(Request request, Response response) throws Exception {
    String accessToken = getAccessToken();
    String userPrincipalName = "automatedreports@rioenergy.com";
    Message email1 = fetchMostRecentSchedulingCalendarEmail(accessToken, userPrincipalName);
    Message email2 = fetchSecondMostRecentCalendarEmail(accessToken, userPrincipalName);
    String email1date = email1.receivedDateTime.toLocalDate().toString();
    String email2date = email2.receivedDateTime.toLocalDate().toString();
    Map<String, String> returnMap = new HashMap<>();
    returnMap.put("Recent Bulletin Date", email1date);
    returnMap.put("Old Bulletin Date", email2date);
    return mapToJson(returnMap);

  }

}
