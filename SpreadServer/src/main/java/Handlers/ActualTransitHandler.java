package Handlers;
import com.squareup.moshi.*;
import spark.Request;
import spark.Response;
import spark.Route;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class ActualTransitHandler implements Route {

  private final Moshi moshi;
  private final JsonAdapter<Map<Integer, List<Integer>>> jsonAdapter;

  public ActualTransitHandler() {
    this.moshi = new Moshi.Builder()
        .add(new IntegerKeyAdapter())  // Custom adapter for integer keys
        .build();

    // Create proper parameterized type
    Type type = Types.newParameterizedType(
        Map.class,
        Integer.class,
        Types.newParameterizedType(List.class, Integer.class)
    );

    this.jsonAdapter = moshi.adapter(type);
  }

  // Custom adapter to handle Integer keys
  static class IntegerKeyAdapter {
    @ToJson String toJson(Integer key) {
      return key.toString();
    }

    @FromJson Integer fromJson(String key) {
      return Integer.parseInt(key);
    }
  }

  @Override
  public Object handle(Request request, Response response) throws Exception {
    String fuel = request.queryParams("fuel");
    String route = request.queryParams("route");
    String year = request.queryParams("year");
    if (year == null) {
      year = java.time.Year.now().toString();
    }


    try {
      Map<Integer, List<Integer>> result;

      if ("GBJLNJ".equals(route)) {
        result = calculateTransitDifferences(fuel, year);
      } else {
        result = getHtnToGbjTimes(fuel, year);
      }

      response.type("application/json");
      return jsonAdapter.toJson(result);

    } catch (IOException e) {
      response.status(500);
      Map<String, String> error = Collections.singletonMap("error", "Failed to process transit data");
      return new Moshi.Builder().build().adapter(Map.class).toJson(error);
    }
  }

  private Map<Integer, List<Integer>> getHtnToGbjTimes(String fuelGrade, String year) throws IOException {
    Map<Integer, List<Integer>> result = new LinkedHashMap<>();
    Map<String, List<List<Integer>>> fileData = readTransitFile("data/Colonial/Actual/GBJactual" + year + ".csv");

    List<List<Integer>> fuelTimes = fileData.get(fuelGrade);
    if (fuelTimes == null) {
      return result;
    }

    for (int cycle = 0; cycle < fuelTimes.size(); cycle++) {
      List<Integer> times = fuelTimes.get(cycle);
      if (!times.isEmpty()) {
        result.put(cycle + 1, times);
      }
    }

    return result;
  }

  private Map<Integer, List<Integer>> calculateTransitDifferences(String fuelGrade, String year) throws IOException {
    Map<Integer, List<Integer>> result = new LinkedHashMap<>();

    Map<String, List<List<Integer>>> gbjData = readTransitFile("data/Colonial/Actual/GBJactual" + year + ".csv");
    Map<String, List<List<Integer>>> lnjData = readTransitFile("data/Colonial/Actual/LNJactual" + year + ".csv");

    List<List<Integer>> gbjTimes = gbjData.get(fuelGrade);
    List<List<Integer>> lnjTimes = lnjData.get(fuelGrade);

    if (gbjTimes == null || lnjTimes == null) {
      return result;
    }

    for (int cycle = 0; cycle < Math.min(gbjTimes.size(), lnjTimes.size()); cycle++) {
      List<Integer> gbjCycle = gbjTimes.get(cycle);
      List<Integer> lnjCycle = lnjTimes.get(cycle);
      List<Integer> diffCycle = new ArrayList<>();

      if (gbjCycle != null && lnjCycle != null) {
        int minSize = Math.min(gbjCycle.size(), lnjCycle.size());
        for (int i = 0; i < minSize; i++) {
          diffCycle.add(lnjCycle.get(i) - gbjCycle.get(i));
        }
      }

      if (!diffCycle.isEmpty()) {
        result.put(cycle + 1, diffCycle);
      }
    }

    return result;
  }

  private Map<String, List<List<Integer>>> readTransitFile(String filename) throws IOException {
    Map<String, List<List<Integer>>> data = new LinkedHashMap<>();

    try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
      String line;
      reader.readLine(); // Skip header

      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        if (parts.length < 2) continue;

        String fuelType = parts[0];
        List<List<Integer>> cycles = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
          List<Integer> times = new ArrayList<>();
          if (!parts[i].isEmpty()) {
            String[] values = parts[i].split(";");
            for (String value : values) {
              try {
                times.add(Integer.parseInt(value.trim()));
              } catch (NumberFormatException e) {
                // Skip invalid numbers
              }
            }
          }
          cycles.add(times);
        }

        data.put(fuelType, cycles);
      }
    }

    return data;
  }
}