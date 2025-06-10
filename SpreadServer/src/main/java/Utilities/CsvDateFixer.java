package Utilities;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CsvDateFixer {

  private static final DateTimeFormatter INPUT_FORMAT1 = DateTimeFormatter.ofPattern("M/d/yy");
  private static final DateTimeFormatter INPUT_FORMAT2 = DateTimeFormatter.ofPattern("M/d/yyyy");
  private static final DateTimeFormatter OUTPUT_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy");

  public static void main(String[] args) throws IOException {
    String directory = "data/"; // Your CSV directory
    fixAllCsvDates(directory);
  }

  public static void fixAllCsvDates(String directory) throws IOException {
    Files.list(Paths.get(directory))
        .filter(path -> path.toString().endsWith(".csv"))
        .forEach(path -> {
          try {
            fixCsvDates(path);
          } catch (IOException e) {
            System.err.println("Error processing " + path + ": " + e.getMessage());
          }
        });
  }

  public static void fixCsvDates(Path csvPath) throws IOException {
    List<String> lines = Files.readAllLines(csvPath);
    List<String> fixedLines = new ArrayList<>();

    for (String line : lines) {
      if (line.startsWith("Date")) {
        fixedLines.add(line); // Keep header unchanged
        continue;
      }

      String[] parts = line.split(",");
      if (parts.length > 0) {
        try {
          String fixedDate = fixDate(parts[0]);
          parts[0] = fixedDate;
          fixedLines.add(String.join(",", parts));
        } catch (Exception e) {
          System.err.println("Skipping malformed line: " + line);
          fixedLines.add(line); // Keep original if can't parse
        }
      }
    }

    // Write backup first
    Files.copy(csvPath, csvPath.resolveSibling(csvPath.getFileName() + ".bak"));
    // Write fixed file
    Files.write(csvPath, fixedLines);
  }

  private static String fixDate(String dateStr) {
    try {
      // Try parsing with 4-digit year first
      LocalDate date = LocalDate.parse(dateStr, INPUT_FORMAT2);
      return date.format(OUTPUT_FORMAT);
    } catch (Exception e1) {
      try {
        // Fall back to 2-digit year
        LocalDate date = LocalDate.parse(dateStr, INPUT_FORMAT1);
        return date.format(OUTPUT_FORMAT);
      } catch (Exception e2) {
        throw new IllegalArgumentException("Invalid date format: " + dateStr);
      }
    }
  }
}