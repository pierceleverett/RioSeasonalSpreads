package Colonial;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CsvToMap {

  public static Map<String, Map<String, Float>> createSortedTransitTimeMap(String csvFilePath) throws IOException {
    // Use TreeMap with custom comparator to sort cycles numerically
    Map<String, Map<String, Float>> transitMap = new TreeMap<>(new Comparator<String>() {
      @Override
      public int compare(String cycle1, String cycle2) {
        return Integer.compare(Integer.parseInt(cycle1), Integer.parseInt(cycle2));
      }
    });

    Path path = Paths.get(csvFilePath);
    if (!Files.exists(path)) {
      System.out.println("CSV file not found: " + csvFilePath);
      return transitMap;
    }

    List<String> lines = Files.readAllLines(path);
    if (lines.isEmpty()) {
      return transitMap;
    }

    // Parse header to get cycle positions
    String[] header = lines.get(0).split(",");
    Map<Integer, String> cyclePositions = new HashMap<>();
    for (int i = 1; i < header.length; i++) {
      cyclePositions.put(i, header[i].trim());
    }

    // Process each data row
    for (int i = 1; i < lines.size(); i++) {
      String[] row = lines.get(i).split(",", -1);
      if (row.length < 2) continue;

      String date = row[0].trim();
      if (date.isEmpty()) continue;

      for (Map.Entry<Integer, String> entry : cyclePositions.entrySet()) {
        int pos = entry.getKey();
        String cycle = entry.getValue();

        if (pos < row.length && !row[pos].trim().isEmpty()) {
          try {
            float transitTime = Float.parseFloat(row[pos].trim());

            // Inner map uses LinkedHashMap to maintain date insertion order
            transitMap.computeIfAbsent(cycle, k -> new LinkedHashMap<>())
                .put(date, transitTime);
          } catch (NumberFormatException e) {
            System.err.println("Error parsing transit time for cycle " + cycle +
                " on " + date + ": " + row[pos]);
          }
        }
      }
    }

    // Sort dates chronologically in each inner map
    transitMap.forEach((cycle, dateMap) -> {
      Map<String, Float> sortedDateMap = new LinkedHashMap<>();
      dateMap.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEachOrdered(entry -> sortedDateMap.put(entry.getKey(), entry.getValue()));
      transitMap.put(cycle, sortedDateMap);
    });

    return transitMap;
  }

  public static void main(String[] args) throws IOException {
    System.out.println(createSortedTransitTimeMap("data/Colonial/Transit/HTNGBJ-GAS2025.csv"));
  }

}
