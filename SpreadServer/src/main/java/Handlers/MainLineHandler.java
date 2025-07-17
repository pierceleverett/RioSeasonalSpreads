package Handlers;

import static GCSpreads.GCSpreadCalc.computeDifference;
import static Noms.MainLine.processMainLineDates;

import Noms.MainLine;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.lang.reflect.Type;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class MainLineHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
    MainLine.MainLineData mainLineData = MainLine.extractLatestMainLineData();
    Map<String, Map<String, String>> processedDates = processMainLineDates(mainLineData);

    Moshi moshi = new Moshi.Builder().build();
    Type innerMapType = Types.newParameterizedType(Map.class, String.class, String.class);
    Type outerMapType = Types.newParameterizedType(Map.class, String.class, innerMapType);
    JsonAdapter<Map<String, Map<String, String>>> adapter = moshi.adapter(outerMapType);

    return adapter.toJson(processedDates);
  }
}
