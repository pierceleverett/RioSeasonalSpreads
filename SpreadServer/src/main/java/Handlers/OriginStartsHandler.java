package Handlers;

import static Colonial.OriginUpdater.updateFromMostRecentOriginEmail;
import static Noms.MainLine.processMainLineDates;
import static Outlook.ExplorerParser.getAccessToken;

import Noms.MainLine;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OriginStartsHandler implements Route {

  public Object handle(Request request, Response response) throws Exception {
        String accessToken = getAccessToken();
    System.out.println("updating data");
        Map<String, List<String>> outputMap = updateFromMostRecentOriginEmail(accessToken);
        String bulletinDate = outputMap.get("Bulletin Date").get(0);
        List<String> currentCycles = outputMap.get("Processed Cycles");
    System.out.println("update done");
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        Map<String, String> dateMap = new HashMap<>();
        dateMap.put("Date", bulletinDate);
        result.put("Bulletin Date", dateMap);
        List<String> cycleNumbers = new ArrayList<>();
        Map<String, List<String>> productDates = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader("data/Colonial/Origin/HTNOrigin.csv"))) {
          String line;
          boolean firstLine = true;

          // Read header line to get cycle numbers
          if ((line = br.readLine()) != null) {
            String[] headers = line.split(",");
            for (int i = 1; i < headers.length; i++) {
              cycleNumbers.add(headers[i]);
            }
          }


          // Read product lines
          while ((line = br.readLine()) != null) {
            String[] values = line.split(",");
            if (values.length > 0) {
              String product = values[0];
              List<String> dates = new ArrayList<>();
              for (int i = 1; i < values.length; i++) {
                dates.add(values[i].trim());
              }
              productDates.put(product, dates);
            }
          }
        }

        // Determine the last 10 cycles (assuming cycles are numbered 1-72)
        String lastCycle = currentCycles.get(currentCycles.size() - 1);
        int lastCyc = Integer.parseInt(lastCycle);
        System.out.println("last cycle: " + lastCyc);
        String startCycle = currentCycles.get(0);
        int firstCyc = Integer.parseInt(startCycle);

    if (lastCyc < firstCyc) {
      // Handle the wrap-around case (start > end)

      // First part: from firstCyc to 72
      for (int i = firstCyc - 1; i < 72; i++) {
        String cycle = cycleNumbers.get(i);
        Map<String, String> productDateMap = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : productDates.entrySet()) {
          String product = entry.getKey();
          List<String> dates = entry.getValue();
          if (i < dates.size()) {
            productDateMap.put(product, dates.get(i));
          }
        }

        result.put(cycle, productDateMap);
      }

      // Second part: from 1 to lastCyc
      for (int i = 0; i < lastCyc; i++) {
        String cycle = cycleNumbers.get(i);
        Map<String, String> productDateMap = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : productDates.entrySet()) {
          String product = entry.getKey();
          List<String> dates = entry.getValue();
          if (i < dates.size()) {
            productDateMap.put(product, dates.get(i));
          }
        }

        result.put(cycle, productDateMap);
      }
    }

    else {



        // Build the result map for last 10 cycles
        for (int i = firstCyc - 1; i < lastCyc; i++) {
          String cycle = cycleNumbers.get(i);
          Map<String, String> productDateMap = new HashMap<>();

          for (Map.Entry<String, List<String>> entry : productDates.entrySet()) {
            String product = entry.getKey();
            List<String> dates = entry.getValue();
            if (i < dates.size()) {
              productDateMap.put(product, dates.get(i));
            }
          }

          result.put(cycle, productDateMap);
        }
    }

    Moshi moshi = new Moshi.Builder().build();
    Type innerMapType = Types.newParameterizedType(Map.class, String.class, String.class);
    Type outerMapType = Types.newParameterizedType(Map.class, String.class, innerMapType);
    JsonAdapter<Map<String, Map<String, String>>> adapter = moshi.adapter(outerMapType);

    return adapter.toJson(result);
  }

}
