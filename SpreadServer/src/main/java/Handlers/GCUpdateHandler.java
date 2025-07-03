package Handlers;

import static GCSpreads.GCSpreadUpdater.getLastUpdatedDateFromCSV;
import static GCSpreads.GCSpreadUpdater.getLatestPricingData;

import GCSpreads.GCcsvupdater;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class GCUpdateHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
    try {
      LocalDate lastDataDate = getLastUpdatedDateFromCSV("data/spreads/GulfCoast/A.csv");
      LocalDate searchStartDate = lastDataDate.plusDays(2); // Email received + 1 day

      System.out.println("Searching for emails starting from: " + searchStartDate);

      OffsetDateTime since = searchStartDate.atStartOfDay().atOffset(ZoneOffset.UTC);
      Map<String, Double> pricingData = getLatestPricingData(since);

      if (!pricingData.isEmpty()) {
        System.out.println("Pricing data retrieved. Updating CSVs...");
        GCcsvupdater.updateSpreadCSVs(pricingData, searchStartDate);
      } else {
        System.out.println("No new pricing data found.");
      }

    } catch (Exception e) {
      System.err.println("Error occurred:");
      e.printStackTrace();
    }
    return null;
  }

}