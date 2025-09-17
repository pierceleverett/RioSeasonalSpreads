package Handlers;

import static GCSpreads.GCSpreadUpdater.getAllPricingData;
import static GCSpreads.GCSpreadUpdater.getLastUpdatedDateFromCSV;

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
   // public static void main(String[] args) {
    try {
      LocalDate lastDataDate = getLastUpdatedDateFromCSV("data/spreads/GulfCoast/A.csv");
      LocalDate today = LocalDate.now();

      System.out.println("Searching for emails from " + lastDataDate + " to " + today);

      // Get all pricing data since the last data date (inclusive)
      Map<LocalDate, Map<String, Double>> allPricingData = getAllPricingData(lastDataDate, today);

      if (!allPricingData.isEmpty()) {
        System.out.println("Updating CSVs with all missing data...");
        for (Map.Entry<LocalDate, Map<String, Double>> entry : allPricingData.entrySet()) {
          GCcsvupdater.updateSpreadCSVs(entry.getValue(), entry.getKey());
        }
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