package Noms;

import Colonial.MostRecentFungible;
import Colonial.MostRecentFungible.FungibleData;
import Noms.MainLine.ClerkHolidayService;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.microsoft.graph.models.Message;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONObject;

import static Noms.MainLine.subtractBusinessDays;
import static Outlook.ExplorerParser.getAccessToken;

public class StubLineNoms {
  private static final String USER_PRINCIPAL_NAME = "automatedreports@rioenergy.com";
  private static LocalDate FungibleReportDate;
  private static LocalDate DateInfoReportDate;

  public static class NomsData {
    LocalDate FungibleReportDate;
    LocalDate DateInfoReportDate;
    Map<String, Map<String, String>> data;

  }
  public static class ClerkHolidayService {
    private static final String CLERK_API_BASE = "https://api.clerk.dev/v1";
    private final String apiKey;
    private final HttpClient httpClient;

    public ClerkHolidayService(String apiKey) {
      this.apiKey = apiKey;
      this.httpClient = HttpClient.newHttpClient();
    }

    public Set<LocalDate> getUserHolidays(String userId) throws Exception {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(CLERK_API_BASE + "/users/" + userId))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .GET()
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new RuntimeException("Failed to fetch user data: " + response.body());
      }

      JSONObject userData = new JSONObject(response.body());
      JSONObject unsafeMetadata = userData.optJSONObject("unsafe_metadata");

      if (unsafeMetadata == null || !unsafeMetadata.has("holidays")) {
        return new HashSet<>();
      }

      JSONArray holidaysArray = unsafeMetadata.getJSONArray("holidays");
      Set<LocalDate> holidays = new HashSet<>();

      for (int i = 0; i < holidaysArray.length(); i++) {
        JSONObject holiday = holidaysArray.getJSONObject(i);
        holidays.add(LocalDate.parse(holiday.getString("date")));
      }

      return holidays;
    }
  }

  public static void main(String[] args) throws Exception {
    MainLine.ClerkHolidayService service = new MainLine.ClerkHolidayService("sk_test_1qlrksRhhWCq5JoqgdF5oCOMdl3paX4vn6D4EAGhkf");
    Set<LocalDate> holidays = service.getUserHolidays("user_2yN2W6lvSdZjV746FQ7NEexyhVu");
    MainLine.HOLIDAYS = holidays;
    System.out.println(calculateStubNoms());
  }

  public static NomsData packageData() throws IOException {
    NomsData returnObj = new NomsData();
    Map<String, Map<String, String>> stubNoms = calculateStubNoms();
    returnObj.data = stubNoms;
    returnObj.FungibleReportDate = FungibleReportDate;
    returnObj.DateInfoReportDate = DateInfoReportDate;
    return returnObj;
  }

  public static Map<String, Map<String, String>> calculateStubNoms() throws IOException {
    Map<String, Map<String, String>> result = new TreeMap<>();
    String accessToken = getAccessToken();

    // 1. Process DateInfo data for 17/19/20/29 nominations
    System.out.println("\n[1/2] Processing Main Line data for 17/19/20/29...");
    Message mainLineMessage = MainLine.fetchMostRecentMainLineEmail(accessToken, USER_PRINCIPAL_NAME);
    MainLine.MainLineData mainLineData = MainLine.parseMainLineEmail(mainLineMessage);
    DateInfoReportDate = mainLineData.reportDate;

    groupGradesByCycle(mainLineData.data.keySet()).forEach((cycle, grades) -> {
      calculate172029Nomination(grades, mainLineData.data).ifPresent(date -> {
        result.computeIfAbsent(cycle, k -> new HashMap<>())
            .put("Stub_172029_Nomination", date);
      });
    });

    // 2. Process ALL Fungible data for 32 nominations in one operation
    System.out.println("\n[2/2] Processing ALL Fungible data for 32 nominations...");
    FungibleData fungibleData = MostRecentFungible.extractLatestFungibleData();
    Set<String> cycles = fungibleData.data.keySet();
    System.out.println("Fungible report date: " + fungibleData.reportDate);
    FungibleReportDate = fungibleData.reportDate;

    if (fungibleData.data != null) {
      // Process all cycles at once
      Map<String, String> all32Noms = calculate32SelectedNominations(cycles);
      Map<String, String> my32Noms = alternateCalculate32SelectedNominations(cycles);

      // Merge into results
      all32Noms.forEach((cycle, date) -> {
        result.computeIfAbsent(cycle, k -> new HashMap<>())
            .put("Min_Stub_32_Nomination", date);
      });

      my32Noms.forEach((cycle, date) -> {
        result.computeIfAbsent(cycle, k -> new HashMap<>())
            .put("My_Stub_32_Nomination", date);
      });
    }

    return result;
  }

  private static Map<String, String> calculate32SelectedNominations(Set<String> selectedCycles) {
    Map<String, String> adjustedNominations = new TreeMap<>();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd");
    int currentYear = LocalDate.now().getYear();
    LocalDate today = LocalDate.now();

    try (BufferedReader reader = new BufferedReader(new FileReader("data/Colonial/Fungible/GBJall.csv"))) {
      String headerLine = reader.readLine();
      if (headerLine == null) return adjustedNominations;

      String[] headers = headerLine.split(",");
      Map<String, Integer> cycleIndexMap = new HashMap<>();
      for (int i = 0; i < headers.length; i++) {
        if (selectedCycles.contains(headers[i])) {
          cycleIndexMap.put(headers[i], i);
          System.out.println("header: " + headers[i] + " index: " + i);
        }
      }

      // Initialize date lists for each cycle
      Map<String, List<LocalDate>> cycleDates = new HashMap<>();
      for (String cycle : selectedCycles) {
        cycleDates.put(cycle, new ArrayList<>());
      }

      String line;
      int lineNumber = 1;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        String[] values = line.split(",", -1);
        for (Map.Entry<String, Integer> entry : cycleIndexMap.entrySet()) {
          String cycle = entry.getKey();
          int index = entry.getValue();
          if (index < values.length && !values[index].isEmpty()) {
            String[] dateStrings = values[index].split(";");
            for (String dateStr : dateStrings) {
              try {
                String[] parts = dateStr.trim().split("/");
                if (parts.length != 2) {
                  System.err.println("Invalid date format '" + dateStr + "' for cycle " + cycle + " on line " + lineNumber);
                  continue;
                }

                int month = Integer.parseInt(parts[0]);
                int day = Integer.parseInt(parts[1]);
                LocalDate date = LocalDate.of(currentYear, month, day);

                // Adjust year if date is too far in the past
                if (date.isBefore(today.minusDays(200))) {
                  date = date.plusYears(1);
                }

                cycleDates.get(cycle).add(date);
              } catch (Exception e) {
                System.err.println("Failed to parse date '" + dateStr + "' for cycle " + cycle + " on line " + lineNumber);
              }
            }
          }
        }
      }

      // Find minimum date for each cycle separately
      for (Map.Entry<String, List<LocalDate>> entry : cycleDates.entrySet()) {
        String cycle = entry.getKey();
        List<LocalDate> dates = entry.getValue();
        System.out.println("Dates for cycle: " + cycle + ": " + dates);

        if (!dates.isEmpty()) {
          LocalDate minDate = Collections.min(dates);
          //List<LocalDate> top3 = getLastThreeAfterDroppingEight(dates);
          //LocalDate modeDate = findModeDate(top3);
          LocalDate adjustedDate = MainLine.subtractBusinessDays(minDate, 4);
          adjustedNominations.put(cycle, adjustedDate.format(formatter));

          // Debug output to verify dates
          System.out.println("Cycle: " + cycle +
              " | Mode date: " + minDate.format(formatter) +
              " | Adjusted date: " + adjustedDate.format(formatter));
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("Error reading file", e);
    }

    return adjustedNominations;
  }

  private static Map<String, String> alternateCalculate32SelectedNominations(Set<String> selectedCycles) {
    Map<String, String> adjustedNominations = new TreeMap<>();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd");
    int currentYear = LocalDate.now().getYear();
    LocalDate today = LocalDate.now();

    try (BufferedReader reader = new BufferedReader(new FileReader("data/Colonial/Fungible/GBJall.csv"))) {
      String headerLine = reader.readLine();
      if (headerLine == null) return adjustedNominations;

      String[] headers = headerLine.split(",");
      Map<String, Integer> cycleIndexMap = new HashMap<>();
      for (int i = 0; i < headers.length; i++) {
        if (selectedCycles.contains(headers[i])) {
          cycleIndexMap.put(headers[i], i);
          System.out.println("header: " + headers[i] + " index: " + i);
        }
      }

      // Initialize date lists for each cycle
      Map<String, List<LocalDate>> cycleDates = new HashMap<>();
      for (String cycle : selectedCycles) {
        cycleDates.put(cycle, new ArrayList<>());
      }

      String line;
      int lineNumber = 1;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (lineNumber == 10) {
          System.out.println(line);
          String[] values = line.split(",", -1);
          for (Map.Entry<String, Integer> entry : cycleIndexMap.entrySet()) {
            String cycle = entry.getKey();
            int index = entry.getValue();
            if (index < values.length && !values[index].isEmpty()) {
              String[] dateStrings = values[index].split(";");
              for (String dateStr : dateStrings) {
                try {
                  String[] parts = dateStr.trim().split("/");
                  if (parts.length != 2) {
                    System.err.println(
                        "Invalid date format '" + dateStr + "' for cycle " + cycle + " on line "
                            + lineNumber);
                    continue;
                  }

                  int month = Integer.parseInt(parts[0]);
                  int day = Integer.parseInt(parts[1]);
                  LocalDate date = LocalDate.of(currentYear, month, day);

                  // Adjust year if date is too far in the past
                  if (date.isBefore(today.minusDays(200))) {
                    date = date.plusYears(1);
                  }

                  cycleDates.get(cycle).add(date);
                } catch (Exception e) {
                  System.err.println(
                      "Failed to parse date '" + dateStr + "' for cycle " + cycle + " on line "
                          + lineNumber);
                }
              }
            }
          }
        }
      }

      // Find minimum date for each cycle separately
      for (Map.Entry<String, List<LocalDate>> entry : cycleDates.entrySet()) {
        String cycle = entry.getKey();
        List<LocalDate> dates = entry.getValue();
        System.out.println("Dates for cycle: " + cycle + ": " + dates);
        System.out.println(cycle + " has " + dates.size() + " dates");

        if (!dates.isEmpty()) {
          List<LocalDate> top3 = getLastThreeAfterDroppingEight(dates);
          LocalDate modeDate = findModeDate(top3);
          if (modeDate != null ) {
            LocalDate adjustedDate = MainLine.subtractBusinessDays(modeDate, 4);
            adjustedNominations.put(cycle, adjustedDate.format(formatter));
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException("Error reading file", e);
    }

    return adjustedNominations;
  }





  private static Map<String, List<String>> groupGradesByCycle(Set<String> grades) {
    Map<String, List<String>> gradesByCycle = new TreeMap<>();
    for (String grade : grades) {
      if (grade.length() < 3) continue;
      String cycle = grade.substring(grade.length() - 2);
      gradesByCycle.computeIfAbsent(cycle, k -> new ArrayList<>()).add(grade);
    }
    return gradesByCycle;
  }

  private static Optional<String> calculate172029Nomination(List<String> grades,
      Map<String, Map<String, String>> mainLineData) {

    // Find 51 and 54 grades
    List<String> distillateGrades = grades.stream()
        .filter(g -> g.startsWith("51-") || g.startsWith("54-"))
        .collect(Collectors.toList());

    if (distillateGrades.isEmpty()) {
      System.out.println("    No 51/54 grades found for cycle");
      return Optional.empty();
    }

    // Find minimum HTN date
    Optional<String> minDate = distillateGrades.stream()
        .flatMap(g -> {
          Map<String, String> gradeData = mainLineData.get(g);
          if (gradeData == null) return Stream.empty();
          return gradeData.entrySet().stream()
              .filter(e -> e.getKey().contains("HTN"))
              .map(Map.Entry::getValue);
        })
        .filter(Objects::nonNull)
        .min(Comparator.naturalOrder());

    // Subtract 3 business days
    return minDate.flatMap(d -> MainLine.adjustSchedulingDate(d, 3));
  }

  private static LocalDate findModeDate(List<LocalDate> dates) {
    if (dates == null || dates.isEmpty()) {
      return null;
    }

    // Count frequency of each date
    Map<LocalDate, Integer> frequencyMap = new HashMap<>();
    for (LocalDate date : dates) {
      frequencyMap.put(date, frequencyMap.getOrDefault(date, 0) + 1);
    }

    // Find date with highest frequency
    LocalDate modeDate = null;
    int maxFrequency = 0;

    for (Map.Entry<LocalDate, Integer> entry : frequencyMap.entrySet()) {
      if (entry.getValue() > maxFrequency) {
        maxFrequency = entry.getValue();
        modeDate = entry.getKey();
      }
    }

    return modeDate;
  }

  public static List<LocalDate> getLastThreeAfterDroppingEight(List<LocalDate> dates) {
    if (dates == null || dates.size() <= 3) {
      return Collections.emptyList();
    }



    // Create a new list to avoid modifying the original
    List<LocalDate> result = new ArrayList<>(dates);

    if (dates.size() >= 24) {
      result = result.subList(23, 25);
    }

    else {
      result = result.subList(dates.size() - 3, dates.size() - 1);
    }

    // Drop the last 8 entries
    //int newSize = result.size() - 7;

    // Get the last 3 from the remaining list
    int startIndex = Math.max(0, result.size() - 3);
    return new ArrayList<>(result.subList(startIndex, result.size()));
  }
}

