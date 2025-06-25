package GCSpreads;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class GCSpreadCalc {

  public static void main(String[] args) {
    try {
      Map<String, Map<String, Float>> spreadDifference = computeDifference("A", "D");

      // Print the results
      for (String year : spreadDifference.keySet()) {
        System.out.println("Year: " + year);
        Map<String, Float> dailySpreads = spreadDifference.get(year);
        for (Map.Entry<String, Float> entry : dailySpreads.entrySet()) {
          System.out.printf("  %s: %.2f%n", entry.getKey(), entry.getValue());
        }
        System.out.println();
      }

    } catch (IOException e) {
      System.err.println("Error reading files: " + e.getMessage());
    }
  }

  public static Map<String, Map<String, Float>> computeDifference(String code1, String code2) throws IOException {
    // Load data from both CSV files
    Map<String, Map<String, Float>> data1 = loadDataFromCSV(code1);
    Map<String, Map<String, Float>> data2 = loadDataFromCSV(code2);

    // Calculate daily spreads for each year
    Map<String, Map<String, Float>> spreadDifferences = new LinkedHashMap<>();

    // Process each year from 2020-2025
    for (String year : Arrays.asList("2020", "2021", "2022", "2023", "2024", "2025")) {
      if (data1.containsKey(year) && data2.containsKey(year)) {
        Map<String, Float> yearDiff = new LinkedHashMap<>();
        Map<String, Float> yearData1 = data1.get(year);
        Map<String, Float> yearData2 = data2.get(year);

        // Calculate difference for each date
        for (String date : yearData1.keySet()) {
          if (yearData2.containsKey(date)) {
            float val1 = yearData1.get(date);
            float val2 = yearData2.get(date);
            yearDiff.put(date, val1 - val2);
          }
        }
        spreadDifferences.put(year, yearDiff);
      }
    }

    // Calculate 5-year average (2020-2024)
    if (spreadDifferences.size() >= 5) {
      Map<String, Float> avgMap = new LinkedHashMap<>();
      Set<String> commonDates = new HashSet<>(data1.get("2020").keySet());
      commonDates.retainAll(data2.get("2020").keySet());

      for (String date : commonDates) {
        float sum = 0f;
        int count = 0;

        for (String year : Arrays.asList("2020", "2021", "2022", "2023", "2024")) {
          if (spreadDifferences.get(year) != null &&
              spreadDifferences.get(year).get(date) != null) {
            sum += spreadDifferences.get(year).get(date);
            count++;
          }
        }

        if (count > 0) {
          avgMap.put(date, sum / count);
        }
      }

      SimpleDateFormat sdf = new SimpleDateFormat("M/d");
      List<Map.Entry<String, Float>> entries = new ArrayList<>(avgMap.entrySet());

      entries.sort((e1, e2) -> {
        try {
          Date d1 = sdf.parse(e1.getKey());
          Date d2 = sdf.parse(e2.getKey());
          return d1.compareTo(d2);
        } catch (ParseException e) {
          throw new RuntimeException("Invalid date format in keys", e);
        }
      });

      Map<String, Float> sortedMap = new LinkedHashMap<>();
      for (Map.Entry<String, Float> entry : entries) {
        sortedMap.put(entry.getKey(), entry.getValue());
      }

      spreadDifferences.put("5YEARAVG", sortedMap);
      return spreadDifferences;
    }
    return null;
  }

  private static Map<String, Map<String, Float>> loadDataFromCSV(String code) throws IOException {
    String filepath = "data/spreads/GulfCoast/" + code + ".csv";
    File file = new File(filepath);

    Map<String, Map<String, Float>> data = new LinkedHashMap<>();
    BufferedReader reader = new BufferedReader(new FileReader(file));

    // Read header to get years
    String headerLine = reader.readLine();
    String[] headers = headerLine.split(",");

    // Initialize maps for each year
    for (int i = 1; i < headers.length; i++) {
      data.put(headers[i].trim(), new LinkedHashMap<>());
    }

    // Read data rows
    String line;
    while ((line = reader.readLine()) != null) {
      String[] tokens = line.split(",");
      if (tokens.length < 2) continue;

      String date = tokens[0].trim();

      for (int i = 1; i < tokens.length && i < headers.length; i++) {
        if (!tokens[i].trim().isEmpty()) {
          try {
            float value = Float.parseFloat(tokens[i].trim());
            data.get(headers[i].trim()).put(date, value);
          } catch (NumberFormatException e) {
            System.err.println("Skipping invalid number at " + headers[i] + " " + date);
          }
        }
      }
    }

    reader.close();
    return data;
  }
}