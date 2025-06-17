package Handlers;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import spark.Request;
import spark.Response;
import spark.Route;

import java.time.MonthDay;
import java.time.format.DateTimeFormatter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.*;

public class BetweenFuelSpreadHandler implements Route {

  @Override
  public Object handle(Request request, Response response) throws Exception {
    String type = request.queryParams("type");
    System.out.println(type);
    if (type == null || (!type.equals("AtoNap") && !type.equals("DtoA"))) {
      response.status(400);
      return "{\"error\":\"Missing or invalid 'type' parameter. Use 'AtoNap' or 'DtoA'.\"}";
    }

    String filePath = "data/spreads/" + type + ".csv";
    System.out.println(filePath);
    Map<String, Map<String, Float>> yearMap = new LinkedHashMap<>();
    Map<String, List<Float>> avgCollector = new HashMap<>();

    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
      String[] headers = reader.readLine().split(",");
      List<String> years = Arrays.asList(headers).subList(1, headers.length);

      for (String year : years) {
        yearMap.put(year, new LinkedHashMap<>());
      }

      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        String date = parts[0];

        for (int i = 1; i < parts.length; i++) {
          String year = headers[i];
          String valueStr = parts[i].trim();
          if (!valueStr.isEmpty()) {
            try {
              float value = Float.parseFloat(valueStr);
              yearMap.get(year).put(date, value);

              if (year.compareTo("2020") >= 0 && year.compareTo("2024") <= 0) {
                avgCollector.computeIfAbsent(date, k -> new ArrayList<>()).add(value);
              }
            } catch (NumberFormatException ignored) {}
          }
        }
      }

// Compute 5YEARAVG in chronological order
      Map<MonthDay, Float> sortedAvgMap = new TreeMap<>();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d");

      for (Map.Entry<String, List<Float>> entry : avgCollector.entrySet()) {
        List<Float> values = entry.getValue();
        if (!values.isEmpty()) {
          float sum = 0;
          for (float v : values) sum += v;
          MonthDay md = MonthDay.parse(entry.getKey(), formatter);
          sortedAvgMap.put(md, sum / values.size());
        }
      }

// Convert back to LinkedHashMap with original date strings
      Map<String, Float> avgMap = new LinkedHashMap<>();
      for (Map.Entry<MonthDay, Float> entry : sortedAvgMap.entrySet()) {
        avgMap.put(entry.getKey().format(formatter), entry.getValue());
      }
      yearMap.put("5YEARAVG", avgMap);


      // Serialize to JSON
      Moshi moshi = new Moshi.Builder().build();
      Type innerMapType = Types.newParameterizedType(Map.class, String.class, Float.class);
      Type outerMapType = Types.newParameterizedType(Map.class, String.class, innerMapType);
      JsonAdapter<Map<String, Map<String, Float>>> adapter = moshi.adapter(outerMapType);

      response.type("application/json");
      return adapter.toJson(yearMap);

    } catch (Exception e) {
      response.status(500);
      return "{\"error\":\"" + e.getMessage() + "\"}";
    }
  }
}
