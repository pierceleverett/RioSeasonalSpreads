package Colonial;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class TransitDifferenceCalculator {

  // Cache the data to avoid reading files multiple times
  private static Map<String, List<List<Integer>>> gbjDataCache;
  private static Map<String, List<List<Integer>>> lnjDataCache;

  static {
    try {
      gbjDataCache = readTransitFile("data/Colonial/Actual/GBJactual.csv");
      lnjDataCache = readTransitFile("data/Colonial/Actual/LNJactual.csv");
    } catch (IOException e) {
      e.printStackTrace();
      gbjDataCache = new HashMap<>();
      lnjDataCache = new HashMap<>();
    }
  }

  /**
   * Calculates transit time differences between GBJ and LNJ for a specific fuel grade
   * @param fuelGrade The fuel grade (e.g., "A2", "D3", "62")
   * @return Map of cycle numbers (1-72) to difference arrays (LNJ - GBJ)
   */
  public static Map<Integer, List<Integer>> calculateTransitDifferences(String fuelGrade) {
    Map<Integer, List<Integer>> result = new LinkedHashMap<>();

    List<List<Integer>> gbjTimes = gbjDataCache.get(fuelGrade);
    List<List<Integer>> lnjTimes = lnjDataCache.get(fuelGrade);

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

      // Cycles are 1-indexed in the output
      result.put(cycle + 1, diffCycle);
    }

    return result;
  }

  private static Map<String, List<List<Integer>>> readTransitFile(String filename) throws IOException {
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

  // Example usage
  public static void main(String[] args) {
    // Example: Get differences for A2 fuel grade
    Map<Integer, List<Integer>> a2Differences = calculateTransitDifferences("A2");

    // Print the results
    a2Differences.forEach((cycle, diffs) -> {
      if (!diffs.isEmpty()) {
        System.out.println("Cycle " + cycle + ": " + diffs);
      }
    });
  }
}