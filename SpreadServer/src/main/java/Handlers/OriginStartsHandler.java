package Handlers;

import static Colonial.MostRecentOrigin.toJson;

import Colonial.MostRecentOrigin;
import Colonial.MostRecentOrigin.OriginComparisonResult;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import spark.Request;
import spark.Response;
import spark.Route;
import java.io.IOException;


public class OriginStartsHandler implements Route {


  public Object handle(Request request, Response response) throws Exception {
    try {
      // Get data
      OriginComparisonResult data = MostRecentOrigin.extractAndCompareOriginData();
      return toJson(data);

    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

}
