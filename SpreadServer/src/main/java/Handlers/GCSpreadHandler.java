package Handlers;

import static GCSpreads.GCSpreadCalc.computeDifference;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.lang.reflect.Type;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;
import GCSpreads.GCSpreadCalc;

public class GCSpreadHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
    String code1 = request.queryParams("code1");
    String code2 = request.queryParams("code2");
    Map<String, Map<String, Float>> returnmap = computeDifference(code1, code2);

    Moshi moshi = new Moshi.Builder().build();
    Type innerMapType = Types.newParameterizedType(Map.class, String.class, Float.class);
    Type outerMapType = Types.newParameterizedType(Map.class, String.class, innerMapType);
    JsonAdapter<Map<String, Map<String, Float>>> adapter = moshi.adapter(outerMapType);

    return adapter.toJson(returnmap);
  }
}
