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
    int currentYear = LocalDate.now().getYear();
    String yearColumn = Integer.toString(currentYear);

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

    boolean dateFound = false;
    boolean dateInserted = false;
    LocalDate newDate = parseDate(dateKey);

    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i);
      String[] parts = line.split(",", -1);
      LocalDate currentDate = parseDate(parts[0]);

      if (parts[0].equals(dateKey)) {
        // Update existing date entry
        parts[yearIndex] = String.format("%.2f", value);
        updatedLines.add(String.join(",", parts));
        dateFound = true;
      } else if (!dateInserted && newDate.isBefore(currentDate)) {
        // Insert new date in correct position
        String[] newRow = new String[headers.length];
        Arrays.fill(newRow, ""); // Initialize empty values
        newRow[0] = dateKey;
        newRow[yearIndex] = String.format("%.2f", value);
        updatedLines.add(String.join(",", newRow));
        updatedLines.add(line); // Add the current line after new row
        dateInserted = true;
        dateFound = true;
      } else {
        updatedLines.add(line);
      }
    }

    // If new date is after all existing dates, add it at the end
    if (!dateFound) {
      String[] newRow = new String[headers.length];
      Arrays.fill(newRow, ""); // Initialize empty values
      newRow[0] = dateKey;
      newRow[yearIndex] = String.format("%.2f", value);
      updatedLines.add(String.join(",", newRow));
    }

    Files.write(Paths.get(filePath), updatedLines);
  }

  private static LocalDate parseDate(String dateStr) {
    String[] parts = dateStr.split("/");
    int month = Integer.parseInt(parts[0]);
    int day = Integer.parseInt(parts[1]);
    return LocalDate.of(2000, month, day); // Using 2000 as a base year for comparison
  }
}