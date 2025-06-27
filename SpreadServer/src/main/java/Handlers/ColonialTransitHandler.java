package Handlers;

import static Colonial.CsvToMap.createSortedTransitTimeMap;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class ColonialTransitHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
    String route = request.queryParams("route");
    String filepath = "data/Colonial/Transit/" + route + ".csv";
    try {
      Map<String, Map<String,Float>> map = createSortedTransitTimeMap(filepath);
      Moshi moshi = new Moshi.Builder().build();
      Type innerMapType = Types.newParameterizedType(Map.class, String.class, Float.class);
      Type outerMapType = Types.newParameterizedType(Map.class, String.class, innerMapType);
      JsonAdapter<Map<String, Map<String, Float>>> adapter = moshi.adapter(outerMapType);

      return adapter.toJson(map);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
