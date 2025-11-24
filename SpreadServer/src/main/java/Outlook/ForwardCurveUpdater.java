package Outlook;

import Outlook.FusionCurveParser.ForwardCurveData;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

public class ForwardCurveUpdater {

  private static final Map<String, String> MONTH_CODES = Map.ofEntries(
      Map.entry("Jan", "F"), Map.entry("Feb", "G"), Map.entry("Mar", "H"),
      Map.entry("Apr", "J"), Map.entry("May", "K"), Map.entry("Jun", "M"),
      Map.entry("Jul", "N"), Map.entry("Aug", "Q"), Map.entry("Sep", "U"),
      Map.entry("Oct", "V"), Map.entry("Nov", "X"), Map.entry("Dec", "Z")
  );

  public static void updateForwardCurveFiles(LocalDate date, ForwardCurveData data) {
    // Check if we need to fill forward from Friday
    LocalDate effectiveDate = getEffectiveBusinessDate(date);

    if (!date.equals(effectiveDate)) {
      System.out.println("Weekend detected (" + date.getDayOfWeek() + "), using data from Friday: " + effectiveDate);
    }

    int year = effectiveDate.getYear();
    int nextYear = year + 1;

    List<String> filesToUpdate = List.of(
        "data/spreads/RBOB" + year + ".csv", "data/spreads/RBOB" + nextYear + ".csv",
        "data/spreads/HO" + year + ".csv", "data/spreads/HO" + nextYear + ".csv"
    );

    for (String fileName : filesToUpdate) {
      Path path = Paths.get(fileName);
      if (Files.exists(path)) {
        try {
          Map<String, Double> curve = fileName.contains("RBOB") ? data.rbobNyh : data.hoNyh;
          System.out.println("Processing file: " + fileName);
          System.out.println(curve);
          updateCsvRowForDate(path, effectiveDate, curve, date);
        } catch (IOException e) {
          System.err.println("Error updating " + fileName + ": " + e.getMessage());
        }
      } else {
        System.out.println("File not found: " + fileName);
      }
    }
  }

  /**
   * Returns the effective business date for data retrieval.
   * For Saturday and Sunday, returns the previous Friday.
   * For weekdays, returns the date itself.
   */
  private static LocalDate getEffectiveBusinessDate(LocalDate date) {
    DayOfWeek dayOfWeek = date.getDayOfWeek();

    if (dayOfWeek == DayOfWeek.SATURDAY) {
      return date.minusDays(1); // Friday
    } else if (dayOfWeek == DayOfWeek.SUNDAY) {
      return date.minusDays(2); // Friday
    } else {
      return date; // Monday-Friday use the actual date
    }
  }

  private static void updateCsvRowForDate(Path filePath, LocalDate effectiveDate, Map<String, Double> curveData, LocalDate originalDate) throws IOException {
    List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
    if (lines.isEmpty()) {
      System.out.println("File is empty: " + filePath);
      return;
    }

    // Handle BOM in header if present
    String headerLine = lines.get(0);
    if (headerLine.startsWith("\uFEFF")) {
      headerLine = headerLine.substring(1);
      lines.set(0, headerLine);
    }

    String[] headers = headerLine.split(",");
    Map<String, Integer> monthIndex = new HashMap<>();
    for (int i = 1; i < headers.length; i++) {
      monthIndex.put(headers[i].trim(), i);
    }

    // Extract the year from the filename
    String fileName = filePath.getFileName().toString();
    int fileYear = Integer.parseInt(fileName.replaceAll("\\D+", ""));

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");
    String targetDate = originalDate.format(formatter); // Use original date for the row
    String effectiveDateStr = effectiveDate.format(formatter); // The date we're getting data from

    // Check if this date already exists in the file
    for (int i = 1; i < lines.size(); i++) {
      String existingLine = lines.get(i);
      if (!existingLine.trim().isEmpty()) {
        String existingDate = existingLine.split(",", 2)[0];
        if (existingDate.equals(targetDate)) {
          System.out.println("✅ Date already exists in file: " + targetDate);
          return; // Skip if date already exists
        }
      }
    }

    // Look for Friday's data to fill forward
    String[] fridayRow = findExistingRowData(lines, effectiveDateStr, headers.length, formatter);

    // Create new row
    String[] newRow = new String[headers.length];
    newRow[0] = targetDate;

    if (fridayRow != null) {
      // Use Friday's data for weekend fill-forward
      System.out.println("Using fill-forward data from " + effectiveDateStr + " for " + targetDate);
      for (int j = 1; j < headers.length; j++) {
        newRow[j] = fridayRow[j];
      }
    } else {
      // No Friday data found, use current curve data (fallback)
      System.out.println("No existing data found for " + effectiveDateStr + ", using current curve data");
      for (int j = 1; j < headers.length; j++) {
        String monthCode = headers[j].trim();
        String monthName = MONTH_CODES.entrySet().stream()
            .filter(e -> e.getValue().equals(monthCode))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);

        if (monthName != null) {
          String key = monthName + "/" + fileYear;
          newRow[j] = curveData.containsKey(key) ? String.valueOf(curveData.get(key)) : "";
        } else {
          newRow[j] = "";
        }
      }
    }

    String newLine = String.join(",", newRow);

    // Find the correct position to insert the new line
    int insertPosition = 1; // Start after header
    while (insertPosition < lines.size()) {
      String line = lines.get(insertPosition);
      if (line.trim().isEmpty()) {
        insertPosition++;
        continue;
      }

      String existingDateStr = line.split(",", 2)[0];
      try {
        LocalDate existingDate = LocalDate.parse(existingDateStr, formatter);
        if (originalDate.isBefore(existingDate)) {
          break; // Found the position to insert
        }
      } catch (DateTimeParseException e) {
        System.err.println("Skipping malformed date: " + existingDateStr);
      }
      insertPosition++;
    }

    // Insert the new line at the correct position
    lines.add(insertPosition, newLine);

    // Write the file back with the new line inserted
    Files.write(filePath, lines, StandardCharsets.UTF_8);
    System.out.println("✅ Inserted new line to " + fileName + " at position " + insertPosition + ": " + newLine);
  }

  /**
   * Finds existing row data for a specific date in the CSV file
   */
  private static String[] findExistingRowData(List<String> lines, String targetDate, int expectedColumns, DateTimeFormatter formatter) {
    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i);
      if (!line.trim().isEmpty()) {
        String[] columns = line.split(",", -1); // -1 to preserve trailing empty fields
        if (columns.length > 0 && columns[0].equals(targetDate)) {
          // Ensure the row has the expected number of columns
          String[] result = new String[expectedColumns];
          System.arraycopy(columns, 0, result, 0, Math.min(columns.length, expectedColumns));

          // Fill any missing columns with empty strings
          for (int j = columns.length; j < expectedColumns; j++) {
            result[j] = "";
          }
          return result;
        }
      }
    }
    return null;
  }
}