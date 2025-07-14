package Colonial;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ColonialActual {
  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy");
  private static final int CURRENT_YEAR = 2025;
  private static final int MAX_CYCLES = 72;
  // All fuel types present in your GBJ.csv
  private static final String[] ALL_FUEL_TYPES = {
      "A2", "A3", "A4", "A5",
      "D2", "D3", "D4",
      "F1", "F3", "F4", "F5",
      "62"
  };

  public static void main(String[] args) {
    String originFile = "data/Colonial/Origin/HTNOrigin.csv";
    String gbjDeliveryFile = "data/Colonial/Fungible/GBJ.csv";
    String lnjDeliveryFile = "data/Colonial/Fungible/LNJ.csv";
    String gbjOutputFile = "data/Colonial/Actual/GBJactual.csv";
    String lnjOutputFile = "data/Colonial/Actual/LNJactual.csv";

    calculateTransitTimes(originFile, gbjDeliveryFile, lnjDeliveryFile, gbjOutputFile, lnjOutputFile);
  }

  private static String getBaseFuelType(String fuelType) {
    // Special case for "62" - it maps to itself
    if (fuelType.equals("62")) {
      return "62";
    }
    // For others (A2, D3, F1, etc.), take first character
    return fuelType.substring(0, 1);
  }

  public static void calculateTransitTimes(String originFile, String gbjDeliveryFile, String lnjDeliveryFile,
      String gbjOutputFile, String lnjOutputFile) {
    try {
      // Read origin data
      Map<String, List<Date>> originData = readOriginData(originFile);

      // Process GBJ deliveries
      Map<String, List<List<Long>>> gbjTransitTimes = calculateDestinationTransitTimes(originData, gbjDeliveryFile);
      writeTransitTimesToCSV(gbjTransitTimes, gbjOutputFile);

      // Process LNJ deliveries
      Map<String, List<List<Long>>> lnjTransitTimes = calculateDestinationTransitTimes(originData, lnjDeliveryFile);
      writeTransitTimesToCSV(lnjTransitTimes, lnjOutputFile);

    } catch (IOException | ParseException e) {
      e.printStackTrace();
    }
  }

  private static Map<String, List<List<Long>>> calculateDestinationTransitTimes(
      Map<String, List<Date>> originData, String deliveryFile) throws IOException, ParseException {

    Map<String, List<List<Long>>> transitTimes = new LinkedHashMap<>();
    Map<String, List<List<Date>>> deliveryData = readDeliveryData(deliveryFile);

    // Initialize all fuel types with empty lists
    for (String fuelType : ALL_FUEL_TYPES) {
      List<List<Long>> cycles = new ArrayList<>();
      for (int i = 0; i < MAX_CYCLES; i++) {
        cycles.add(new ArrayList<>());
      }
      transitTimes.put(fuelType, cycles);
    }

    // Process each fuel type from delivery data
    for (Map.Entry<String, List<List<Date>>> entry : deliveryData.entrySet()) {
      String key = entry.getKey();
      String baseFuelType = getBaseFuelType(key);
      List<Date> originDates = originData.get(baseFuelType);

      if (originDates == null) {
        System.out.println("Warning: No origin dates found for base fuel type: " + baseFuelType);
        continue;
      }

      List<List<Date>> deliveryDatesList = entry.getValue();

      for (int i = 0; i < Math.min(originDates.size(), deliveryDatesList.size()); i++) {
        List<Date> deliveryDates = deliveryDatesList.get(i);
        if (deliveryDates == null || deliveryDates.isEmpty()) continue;

        Date originDate = originDates.get(i);
        List<Long> cycleTransitTimes = new ArrayList<>();

        for (Date deliveryDate : deliveryDates) {
          long diffInMillis = deliveryDate.getTime() - originDate.getTime();
          long diffInDays = TimeUnit.DAYS.convert(diffInMillis, TimeUnit.MILLISECONDS);
          cycleTransitTimes.add(diffInDays);
        }

        if (i < MAX_CYCLES) {
          List<List<Long>> fuelTransitTimes = transitTimes.get(key);
          if (fuelTransitTimes != null) {
            fuelTransitTimes.set(i, cycleTransitTimes);
          } else {
            System.out.println("Warning: No transit times list found for fuel type: " + key);
          }
        }
      }
    }

    return transitTimes;
  }

  private static Map<String, List<Date>> readOriginData(String filename) throws IOException, ParseException {
    Map<String, List<Date>> originData = new LinkedHashMap<>();

    try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
      String line;
      reader.readLine(); // Skip header

      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        if (parts.length < 2) continue;

        String fuelType = parts[0];
        List<Date> dates = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
          if (!parts[i].isEmpty()) {
            String dateStr = parts[i] + "/" + CURRENT_YEAR;
            dates.add(DATE_FORMAT.parse(dateStr));
          }
        }

        originData.put(fuelType, dates);
      }
    }

    return originData;
  }

  private static Map<String, List<List<Date>>> readDeliveryData(String filename) throws IOException, ParseException {
    Map<String, List<List<Date>>> deliveryData = new LinkedHashMap<>();

    try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
      String line;
      reader.readLine(); // Skip header

      while ((line = reader.readLine()) != null) {
        String[] parts = line.split(",");
        if (parts.length < 2) continue;

        String key = parts[0];
        List<List<Date>> cycleDates = new ArrayList<>();

        // Initialize all cycles (up to MAX_CYCLES)
        for (int i = 0; i < MAX_CYCLES; i++) {
          cycleDates.add(new ArrayList<>());
        }

        for (int i = 1; i < Math.min(parts.length, MAX_CYCLES + 1); i++) {
          if (!parts[i].isEmpty()) {
            String[] dateStrings = parts[i].split(";");
            for (String dateStr : dateStrings) {
              cycleDates.get(i-1).add(DATE_FORMAT.parse(dateStr + "/" + CURRENT_YEAR));
            }
          }
        }

        deliveryData.put(key, cycleDates);
      }
    }

    return deliveryData;
  }

  private static void writeTransitTimesToCSV(Map<String, List<List<Long>>> transitTimes, String outputFile)
      throws IOException {
    // Ensure output directory exists
    new File(outputFile).getParentFile().mkdirs();

    try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
      // Write header
      writer.print("FuelType");
      for (int i = 1; i <= MAX_CYCLES; i++) {
        writer.print("," + i);
      }
      writer.println();

      // Write data in the correct order
      for (String fuelType : ALL_FUEL_TYPES) {
        writer.print(fuelType);

        List<List<Long>> cycles = transitTimes.get(fuelType);
        for (int i = 0; i < MAX_CYCLES; i++) {
          writer.print(",");
          if (cycles != null && i < cycles.size() && !cycles.get(i).isEmpty()) {
            writer.print(String.join(";", cycles.get(i).stream()
                .map(Object::toString)
                .toArray(String[]::new)));
          }
        }
        writer.println();
      }
    }
  }
}