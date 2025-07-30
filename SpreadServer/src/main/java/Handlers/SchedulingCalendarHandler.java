package Handlers;

import static Outlook.ExplorerCalendarList.extractBulletinDate;
import static Outlook.ExplorerCalendarList.parseCalendarList;
import static Outlook.ExplorerParser.getAccessToken;

import Outlook.ExplorerCalendarList;
import Outlook.ExplorerCalendarList.CalendarList;
import Outlook.ExplorerSchedulingCalendar;
import Outlook.ExplorerSchedulingCalendar.SchedulingCalendar;
import com.microsoft.graph.models.Message;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class SchedulingCalendarHandler implements Route {

  public Object handle(Request request, Response response) throws Exception {
    String accessToken = getAccessToken();

    Message recentEmail = ExplorerCalendarList.fetchMostRecentCalendarEmail(accessToken, "automatedreports@rioenergy.com");
    String bulletinDate = ExplorerCalendarList.extractBulletinDate(recentEmail).toString();
    Map<String, Map<String, Map<String, String>>> output = parseCalendarList(recentEmail);
    CalendarList bulletin = new CalendarList(bulletinDate, output);

    Moshi moshi = new Moshi.Builder().build();
    JsonAdapter<CalendarList> adapter = moshi.adapter(CalendarList.class);

// serialize
    return adapter.toJson(bulletin);
  }



}
