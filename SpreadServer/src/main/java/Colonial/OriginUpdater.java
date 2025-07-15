package Colonial;

import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class OriginUpdater {
  private static final String ORIGIN_CSV_PATH = "data/Colonial/Origin/HTNOrigin.csv";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd");
  private static final int WARNING_THRESHOLD_DAYS = 30;

  public static void main(String[] args) {
    try {
      System.out.println("=== Colonial Origin Updater ===");
      String accessToken = Outlook.ExplorerParser.getAccessToken();

      // Check if we need to update
      if (shouldUpdateOriginData()) {
        System.out.println("Last cycle date is approaching, checking for new origin emails...");
        ColonialOrigin.processNewOriginStartsEmails(accessToken, "automatedreports@rioenergy.com");
      } else {
        System.out.println("No update needed - last cycle date is still more than " +
            WARNING_THRESHOLD_DAYS + " days away");
      }

      System.out.println("=== Update check completed ===");
    } catch (Exception e) {
      System.err.println("Error in OriginUpdater: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public static boolean shouldUpdateOriginData() throws IOException {
    // Read the CSV file
    List<String> lines = Files.readAllLines(Paths.get(ORIGIN_CSV_PATH));
    if (lines.size() < 2) return false; // No data rows

    // Find the last non-empty cycle date (excluding cycle 72)
    for (String line : lines.subList(1, lines.size())) { // Skip header
      String[] parts = line.split(",");
      String fuelType = parts[0];

      // Skip if not A, D, F or 62
      if (!fuelType.matches("A|D|F|62")) continue;

      // Check cycles from 71 down to 1
      for (int i = 71; i >= 1; i--) {
        if (i >= parts.length) continue; // Skip if cycle column doesn't exist

        String dateStr = parts[i].trim();
        if (!dateStr.isEmpty()) {
          try {
            LocalDate date = LocalDate.parse(dateStr + "/" + Year.now().getValue(),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"));

            long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), date);
            System.out.printf("Last date for %s cycle %d: %s (%d days remaining)%n",
                fuelType, i, dateStr, daysRemaining);

            return daysRemaining <= WARNING_THRESHOLD_DAYS;
          } catch (Exception e) {
            System.err.println("Error parsing date '" + dateStr + "' for " + fuelType + " cycle " + i);
          }
        }
      }
    }

    return false;
  }
}