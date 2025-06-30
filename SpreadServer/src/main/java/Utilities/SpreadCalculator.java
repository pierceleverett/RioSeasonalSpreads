package Utilities;
import Utilities.Parser.TrivialCreator;
import java.io.IOException;
import java.time.Year;
import java.util.*;
import Utilities.Parser.Parser;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SpreadCalculator {

  // Month configuration: {startMonth, startDay, endMonth, endDay, startYearAdjustment, endYearAdjustment}
  private static final Map<String, String[]> MONTH_CONFIG = new HashMap<>();
  static {
    MONTH_CONFIG.put("F", new String[]{"01", "01", "12", "31", "-1", "-1"}); // Jan-Dec (prev year)
    MONTH_CONFIG.put("G", new String[]{"02", "01", "01", "31", "-1", "0"});  // Feb (prev year) - Jan (current)
    MONTH_CONFIG.put("H", new String[]{"03", "01", "02", "28", "-1", "0"});  // Mar (prev year) - Feb (current)
    MONTH_CONFIG.put("J", new String[]{"04", "01", "03", "31", "-1", "0"});  // Apr (prev year) - Mar (current)
    MONTH_CONFIG.put("K", new String[]{"05", "01", "04", "30", "-1", "0"});  // May (prev year) - Apr (current)
    MONTH_CONFIG.put("M", new String[]{"06", "01", "05", "31", "-1", "0"});  // Jun (prev year) - May (current)
    MONTH_CONFIG.put("N", new String[]{"07", "01", "06", "30", "-1", "0"});  // Jul (prev year) - Jun (current)
    MONTH_CONFIG.put("Q", new String[]{"08", "01", "07", "31", "-1", "0"});  // Aug (prev year) - Jul (current)
    MONTH_CONFIG.put("U", new String[]{"09", "01", "08", "31", "-1", "0"});  // Sep (prev year) - Aug (current)
    MONTH_CONFIG.put("V", new String[]{"10", "01", "09", "30", "-1", "0"});  // Oct (prev year) - Sep (current)
    MONTH_CONFIG.put("X", new String[]{"11", "01", "10", "31", "-1", "0"});  // Nov (prev year) - Oct (current)
    MONTH_CONFIG.put("Z", new String[]{"12", "01", "11", "30", "-1", "0"});  // Dec (prev year) - Nov (current)
  }
public static void main(String[] args) throws IOException {
    Map<String, Float> map = spreadCalculator("HO", "G", "U", "2022");
  System.out.println(map);
}
  public static Map<String, String> getFuturesDates(String year, String monthCode) {
    if (!MONTH_CONFIG.containsKey(monthCode)) {
      throw new IllegalArgumentException("Invalid month code: " + monthCode);
    }

    String[] config = MONTH_CONFIG.get(monthCode);
    int yearInt = Integer.parseInt(year);

    // Handle February leap years
    String endDay = config[3];
    if (monthCode.equals("H") && Year.of(yearInt).isLeap()) {
      endDay = "29";
    }

    Map<String, String> result = new HashMap<>();
    result.put("startDate", String.format("%s/%s/%d",
        config[0], config[1],
        yearInt + Integer.parseInt(config[4])));

    result.put("endDate", String.format("%s/%s/%d",
        config[2], endDay,
        yearInt + Integer.parseInt(config[5])));

    return result;
  }

  public static Map<String, Float> spreadCalculator(String commodity, String startMonth, String endMonth, String year)
      throws IOException {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");
    LocalDate startDate = LocalDate.parse(getFuturesDates(year, startMonth).get("startDate"), formatter);
    LocalDate endDate = LocalDate.parse(getFuturesDates(year, startMonth).get("endDate"), formatter);
    String csvFilename = "data/spreads/" + commodity + year +".csv";
    System.out.println(csvFilename);
    Parser csvParser = new Parser(csvFilename, new TrivialCreator(), false);
    csvParser.parse();
    List<List<String>> sheet = csvParser.getParsedContent();
    System.out.println("successfully parsed");

    HashMap<String, Integer> headerMap = new LinkedHashMap<String, Integer>();
    HashMap<String, Float> firstMonthValues = new LinkedHashMap<String, Float>();
    HashMap<String, Float> secondMonthValues = new LinkedHashMap<String, Float>();

    List<String> headers = (List<String>) sheet.get(0);
    for (int i = 0; i < headers.size(); i++) {
      String column = headers.get(i);
      if (i > 0) {
        headerMap.put(column, i);

      }
    }
    System.out.println("headers found");


    Integer firstIndex = headerMap.get(startMonth);
    System.out.println(firstIndex);
    Integer secondIndex = headerMap.get(endMonth);
    System.out.println(secondIndex);
    sheet.remove(0);

    for (List<String> row : sheet) {
      LocalDate date = LocalDate.parse(row.get(0), formatter);
      System.out.println("processing date:" + date);
      if (row.get(0) != "Date") {
        for (int i = 1; i < row.size(); i++){
          if (i == firstIndex && (!date.isBefore(startDate) && !date.isAfter(endDate))) {
            System.out.println("first value: " + Float.parseFloat(row.get(i)));
            firstMonthValues.put(date.toString().substring(5), Float.parseFloat(row.get(i)));
          }
          if (i == secondIndex && (!date.isBefore(startDate) && !date.isAfter(endDate))) {
            secondMonthValues.put(date.toString().substring(5), Float.parseFloat(row.get(i)));
            System.out.println("second value: " + Float.parseFloat(row.get(i)));
          }
        }
      }
    }

    HashMap<String, Float> spreadMap = new LinkedHashMap<>();

    System.out.println("calculating differences");

    for (String d : firstMonthValues.keySet()) {
      Float firstValue = firstMonthValues.get(d);
      Float secondValue = secondMonthValues.get(d);
      Float difference = firstValue - secondValue;
      spreadMap.put(d, difference);
    }


    return spreadMap;





  }

}
