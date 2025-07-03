package Handlers;

import static Colonial.ColonialTransitTime.processTransitTimes;
import static Colonial.CsvToMap.createSortedTransitTimeMap;

import Colonial.ColonialTransitUpdater;
import Colonial.MostRecentFungible;
import Colonial.MostRecentFungible.FungibleComparisonResult;
import Colonial.MostRecentFungible.FungibleData;
import Utilities.FungibleMoshiAdapter;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class RecentFungibleHandler implements Route {

  public Object handle(Request request, Response response) throws Exception {
    try {
      // Get data
      FungibleComparisonResult data = MostRecentFungible.extractAndCompareFungibleData();

      // Serialize
      String json = FungibleMoshiAdapter.toJson(data);
      return json;

    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }
}



