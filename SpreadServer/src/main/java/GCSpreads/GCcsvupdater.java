package GCSpreads;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class GCcsvupdater {

  private static final String BASE_PATH = "data/spreads/";

  public static void updateSpreadCSVs(Map<String, Double> pricingData, LocalDate emailReceivedDate) throws IOException {
    LocalDate targetDate = emailReceivedDate.minusDays(1);
    String dateKey = targetDate.format(DateTimeFormatter.ofPattern("M/d"));
    String yearColumn = "2025";

    Map<String, String> fileMap = Map.of(
        "A", "GulfCoast/A.csv",
        "D", "GulfCoast/D.csv",
        "F", "GulfCoast/F.csv",
        "H", "GulfCoast/H.csv",
        "M", "GulfCoast/M.csv",
        "Chi91-GC93", "91Chi.csv",
        "ChiRBOB-ChiCBOB", "ChiCBOB.csv",
        "Nap", "GulfCoast/Nap.csv"
    );

    // Compute derived values
    double chi91 = pricingData.getOrDefault("Chi91", 0.0);
    double gc93 = pricingData.getOrDefault("GC93", 0.0);
    double chiRBOB = pricingData.getOrDefault("ChiRBOB", 0.0);
    double chiCBOB = pricingData.getOrDefault("ChiCBOB", 0.0);

    pricingData.put("Chi91-GC93", chi91 - gc93 - 24.319);
    pricingData.put("ChiRBOB-ChiCBOB", chiRBOB - chiCBOB);

    for (Map.Entry<String, String> entry : fileMap.entrySet()) {
      String key = entry.getKey();
      String filePath = BASE_PATH + entry.getValue();
      if (pricingData.containsKey(key)) {
        updateCSV(filePath, dateKey, yearColumn, pricingData.get(key));
      }
    }
  }

  private static void updateCSV(String filePath, String dateKey, String yearColumn, double value) throws IOException {
    List<String> lines = Files.readAllLines(Paths.get(filePath));
    List<String> updatedLines = new ArrayList<>();
    String[] headers = lines.get(0).split(",");
    int yearIndex = Arrays.asList(headers).indexOf(yearColumn);

    if (yearIndex == -1) {
      throw new IOException("Year column " + yearColumn + " not found in " + filePath);
    }

    updatedLines.add(lines.get(0)); // header

    for (int i = 1; i < lines.size(); i++) {
      String[] parts = lines.get(i).split(",", -1);
      if (parts[0].equals(dateKey)) {
        parts[yearIndex] = String.format("%.2f", value);
      }
      updatedLines.add(String.join(",", parts));
    }

    Files.write(Paths.get(filePath), updatedLines);
  }
}
