package Handlers;

import static Colonial.ColonialTransitTime.processTransitTimes;
import static Colonial.CsvToMap.createSortedTransitTimeMap;

import Colonial.ColonialTransitUpdater;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class ColonialTransitUpdaterHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
    response.type("application/json");
    ColonialTransitUpdater.updateMissingTransitData();
    return "{\"status\":\"success\"}";
  }

}