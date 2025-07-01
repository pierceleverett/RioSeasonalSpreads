package Utilities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FiveYearAvgCalc {
  private static final Logger logger = LoggerFactory.getLogger(FiveYearAvgCalc.class);
  private static final int REQUIRED_YEARS = 5;

  public static Map<String, Float> AvgCalc(Map<String, Map<String, Float>> allYears, ArrayList<String> yearList) {
    Objects.requireNonNull(allYears, "allYears map cannot be null");
    Objects.requireNonNull(yearList, "yearList cannot be null");

    if (yearList.size() < REQUIRED_YEARS) {
      throw new IllegalArgumentException("yearList must contain exactly " + REQUIRED_YEARS + " years");
    }

    // Verify all required years exist in allYears map
    for (int i = 0; i < REQUIRED_YEARS; i++) {
      String year = yearList.get(i);
      if (!allYears.containsKey(year)) {
        throw new IllegalArgumentException("Missing data for year: " + year);
      }
    }

    Map<String, Float> year1 = allYears.get(yearList.get(0));
    Map<String, Float> year2 = allYears.get(yearList.get(1));
    Map<String, Float> year3 = allYears.get(yearList.get(2));
    Map<String, Float> year4 = allYears.get(yearList.get(3));
    Map<String, Float> year5 = allYears.get(yearList.get(4));

    Map<String, Float> avgSpread = new LinkedHashMap<>();

    // Use the first year's dates as reference
    for (String date : year1.keySet()) {
      try {
        // Get values from all years, with null checks
        Float val1 = year1.get(date);
        Float val2 = year2.get(date);
        Float val3 = year3.get(date);
        Float val4 = year4.get(date);
        Float val5 = year5.get(date);

        // Verify all values exist for this date
        if (val1 == null || val2 == null || val3 == null || val4 == null || val5 == null) {
          logger.warn("Missing data for date {} in one or more years. Skipping.", date);
          continue;
        }

        // Calculate average
        float sum = val1 + val2 + val3 + val4 + val5;
        float avg = sum / REQUIRED_YEARS;
        avgSpread.put(date, avg);

      } catch (Exception e) {
        logger.error("Error calculating average for date {}: {}", date, e.getMessage());
      }
    }

    if (avgSpread.isEmpty()) {
      logger.warn("No valid averages calculated - check input data");
    } else {
      logger.info("Successfully calculated averages for {} dates", avgSpread.size());
    }

    return avgSpread;
  }
}