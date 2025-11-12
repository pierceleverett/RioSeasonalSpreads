package Utilities;
import Utilities.Parser.TrivialCreator;
import java.io.IOException;
import java.time.Year;
import java.util.*;
import Utilities.Parser.Parser;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SpreadCalculator {

  // Month configuration: {contractMonth, contractDay, yearAdjustment}
  private static final Map<String, String[]> CONTRACT_CONFIG = new HashMap<>();
  static {
    // Format: {expirationMonth, expirationDay, yearAdjustment}
    // Using the last day of the previous month as approximation for "last business day"
    CONTRACT_CONFIG.put("F", new String[]{"12", "31", "-1"});  // January - expires last biz day of Dec
    CONTRACT_CONFIG.put("G", new String[]{"01", "31", "0"});   // February - expires last biz day of Jan
    CONTRACT_CONFIG.put("H", new String[]{"02", "28", "0"});   // March - expires last biz day of Feb
    CONTRACT_CONFIG.put("J", new String[]{"03", "31", "0"});   // April - expires last biz day of Mar
    CONTRACT_CONFIG.put("K", new String[]{"04", "30", "0"});   // May - expires last biz day of Apr
    CONTRACT_CONFIG.put("M", new String[]{"05", "31", "0"});   // June - expires last biz day of May
    CONTRACT_CONFIG.put("N", new String[]{"06", "30", "0"});   // July - expires last biz day of Jun
    CONTRACT_CONFIG.put("Q", new String[]{"07", "31", "0"});   // August - expires last biz day of Jul
    CONTRACT_CONFIG.put("U", new String[]{"08", "31", "0"});   // September - expires last biz day of Aug
    CONTRACT_CONFIG.put("V", new String[]{"09", "30", "0"});   // October - expires last biz day of Sep
    CONTRACT_CONFIG.put("X", new String[]{"10", "31", "0"});   // November - expires last biz day of Oct
    CONTRACT_CONFIG.put("Z", new String[]{"11", "30", "0"});   // December - expires last biz day of Nov
  }

  public static void main(String[] args) throws IOException {
    Map<String, Float> map = spreadCalculator("RBOB", "N", "Q", "2025");
    System.out.println(map);
  }

  public static Map<String, String> getContractDates(String baseYear, String monthCode) {
    if (!CONTRACT_CONFIG.containsKey(monthCode)) {
      throw new IllegalArgumentException("Invalid month code: " + monthCode);
    }

    String[] config = CONTRACT_CONFIG.get(monthCode);
    int yearInt = Integer.parseInt(baseYear);
    int yearAdjustment = Integer.parseInt(config[2]);

    // Handle February leap years
    String day = config[1];
    if (monthCode.equals("G") && Year.of(yearInt + yearAdjustment).isLeap()) {
      day = "29";
    }

    Map<String, String> result = new HashMap<>();
    result.put("contractDate", String.format("%s/%s/%d", config[0], day, yearInt + yearAdjustment));
    return result;
  }

  public static Map<String, Float> spreadCalculator(String commodity, String startMonth, String endMonth, String baseYear)
      throws IOException {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");

    // Determine the correct years for each contract
    String startYear = baseYear;
    String endYear = baseYear;

    // Get contract months as integers for comparison
    Map<String, Integer> monthToInt = Map.ofEntries(
        Map.entry("F", 1), Map.entry("G", 2), Map.entry("H", 3), Map.entry("J", 4),
        Map.entry("K", 5), Map.entry("M", 6), Map.entry("N", 7), Map.entry("Q", 8),
        Map.entry("U", 9), Map.entry("V", 10), Map.entry("X", 11), Map.entry("Z", 12)
    );

    int startMonthInt = monthToInt.get(startMonth);
    int endMonthInt = monthToInt.get(endMonth);

    // If start month is after end month in calendar order, end contract is from next year
    boolean isRollingSpread = (startMonthInt > endMonthInt);
    if (isRollingSpread) {
      endYear = String.valueOf(Integer.parseInt(baseYear) + 1);
    }

    // Get contract expiration dates
    Map<String, String> startContract = getContractDates(startYear, startMonth);
    Map<String, String> endContract = getContractDates(endYear, endMonth);

    LocalDate startContractDate = LocalDate.parse(startContract.get("contractDate"), formatter);
    LocalDate endContractDate = LocalDate.parse(endContract.get("contractDate"), formatter);

    // Calculate the correct calculation period
    LocalDate calculationStart;
    LocalDate calculationEnd;

    if (isRollingSpread) {
      // Rolling spread (e.g., Dec-Jan): Use Dec 1 of previous year to Nov 30 of current year
      calculationStart = LocalDate.of(Integer.parseInt(startYear) - 1, 12, 1);
      calculationEnd = LocalDate.of(Integer.parseInt(startYear), 11, 30);
      System.out.println("Rolling spread detected - using period: " + calculationStart + " to " + calculationEnd);
    } else {
      // Normal spread (e.g., Aug-Sep): Use Dec 1 of previous year to expiration of start contract
      calculationStart = LocalDate.of(Integer.parseInt(startYear) - 1, 12, 1);
      calculationEnd = startContractDate;
      System.out.println("Normal spread - using period: " + calculationStart + " to " + calculationEnd);
    }

    Map<String, Float> firstMonthValues = new LinkedHashMap<>();
    Map<String, Float> secondMonthValues = new LinkedHashMap<>();

    if (isRollingSpread) {
      // Rolling spread: Read from TWO files
      String startCsvFilename = "data/spreads/" + commodity + startYear + ".csv";
      String endCsvFilename = "data/spreads/" + commodity + endYear + ".csv";

      System.out.println("Reading start month from: " + startCsvFilename);
      System.out.println("Reading end month from: " + endCsvFilename);

      // Parse start month data (from current year file)
      Parser startCsvParser = new Parser(startCsvFilename, new TrivialCreator(), false);
      startCsvParser.parse();
      List<List<String>> startSheet = startCsvParser.getParsedContent();
      firstMonthValues = extractMonthData(startSheet, startMonth, calculationStart, calculationEnd, formatter);

      // Parse end month data (from next year file)
      Parser endCsvParser = new Parser(endCsvFilename, new TrivialCreator(), false);
      endCsvParser.parse();
      List<List<String>> endSheet = endCsvParser.getParsedContent();
      secondMonthValues = extractMonthData(endSheet, endMonth, calculationStart, calculationEnd, formatter);

    } else {
      // Normal spread: Read from SINGLE file
      String csvFilename = "data/spreads/" + commodity + baseYear + ".csv";
      System.out.println("Using single file: " + csvFilename);

      Parser csvParser = new Parser(csvFilename, new TrivialCreator(), false);
      csvParser.parse();
      List<List<String>> sheet = csvParser.getParsedContent();

      HashMap<String, Integer> headerMap = new LinkedHashMap<>();
      List<String> headers = sheet.get(0);
      for (int i = 0; i < headers.size(); i++) {
        String column = headers.get(i);
        if (i > 0) {
          headerMap.put(column, i);
        }
      }

      Integer firstIndex = headerMap.get(startMonth);
      Integer secondIndex = headerMap.get(endMonth);

      sheet.remove(0);
      for (List<String> row : sheet) {
        if (row.get(0).equals("Date") || row.get(0).isEmpty()) continue;

        try {
          LocalDate date = LocalDate.parse(row.get(0), formatter);

          if (!date.isBefore(calculationStart) && !date.isAfter(calculationEnd)) {
            // Get first month value
            if (firstIndex < row.size() && !row.get(firstIndex).isEmpty()) {
              Float firstValue = Float.parseFloat(row.get(firstIndex));
              firstMonthValues.put(date.toString().substring(5), firstValue);
            }

            // Get second month value
            if (secondIndex < row.size() && !row.get(secondIndex).isEmpty()) {
              Float secondValue = Float.parseFloat(row.get(secondIndex));
              secondMonthValues.put(date.toString().substring(5), secondValue);
            }
          }
        } catch (Exception e) {
          System.err.println("Error processing row: " + row);
        }
      }
    }

    System.out.println("First month values found: " + firstMonthValues.size());
    System.out.println("Second month values found: " + secondMonthValues.size());

    HashMap<String, Float> spreadMap = new LinkedHashMap<>();
    System.out.println("calculating differences");

    for (String d : firstMonthValues.keySet()) {
      if (secondMonthValues.containsKey(d)) {
        Float firstValue = firstMonthValues.get(d);
        Float secondValue = secondMonthValues.get(d);
        Float difference = firstValue - secondValue;
        spreadMap.put(d, difference);
        System.out.println("Spread for " + d + ": " + difference);
      }
    }

    System.out.println("Final spread map size: " + spreadMap.size());
    return spreadMap;
  }

  private static Map<String, Float> extractMonthData(List<List<String>> sheet, String monthCode,
      LocalDate calculationStart, LocalDate calculationEnd,
      DateTimeFormatter formatter) {
    Map<String, Float> result = new LinkedHashMap<>();

    HashMap<String, Integer> headerMap = new LinkedHashMap<>();
    List<String> headers = sheet.get(0);
    for (int i = 0; i < headers.size(); i++) {
      String column = headers.get(i);
      if (i > 0) {
        headerMap.put(column, i);
      }
    }

    Integer monthIndex = headerMap.get(monthCode);
    if (monthIndex == null) {
      throw new RuntimeException("Month " + monthCode + " not found in CSV headers");
    }

    sheet.remove(0);
    for (List<String> row : sheet) {
      if (row.get(0).equals("Date") || row.get(0).isEmpty()) continue;

      try {
        LocalDate date = LocalDate.parse(row.get(0), formatter);

        if (!date.isBefore(calculationStart) && !date.isAfter(calculationEnd)) {
          if (monthIndex < row.size() && !row.get(monthIndex).isEmpty()) {
            Float value = Float.parseFloat(row.get(monthIndex));
            result.put(date.toString().substring(5), value);
          }
        }
      } catch (Exception e) {
        System.err.println("Error processing row: " + row);
      }
    }

    return result;
  }
}