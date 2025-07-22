package Noms;

import Colonial.MostRecentFungible;
import Colonial.MostRecentFungible.FungibleData;
import com.microsoft.graph.models.Message;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

  public static void main(String[] args) {
    try {
      System.out.println("=== Starting Stub Noms Calculation ===");
      Map<String, Map<String, String>> stubNoms = calculateStubNoms();
      System.out.println("\n=== Final Stub Nomination Results ===");
      stubNoms.forEach((cycle, data) -> {
        System.out.println("Cycle " + cycle + ":");
        data.forEach((type, date) -> {
          System.out.printf("  %-20s: %s%n", type, date);
        });
      });
    } catch (IOException e) {
      System.err.println("Failed to calculate stub noms:");
      e.printStackTrace();
    }
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

    // 1. Process Main Line data for 17/19/20/29 nominations
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
    System.out.println("Fungible report date: " + fungibleData.reportDate);
    FungibleReportDate = fungibleData.reportDate;

    if (fungibleData.data != null) {
      // Process all cycles at once
      Map<String, String> all32Noms = calculateAll32Nominations(fungibleData.data);

      // Merge into results
      all32Noms.forEach((cycle, date) -> {
        result.computeIfAbsent(cycle, k -> new HashMap<>())
            .put("Stub_32_Nomination", date);
      });
    }

    return result;
  }

  private static Map<String, String> calculateAll32Nominations(
      Map<String, Map<String, Map<String, String>>> fungibleData) {

    Map<String, String> all32Nominations = new TreeMap<>();

    fungibleData.forEach((cycle, productsMap) -> {
      Map<String, LocalDate> gbjData = new HashMap<>();

      for (String product : productsMap.keySet()) {
        String gbjDate = productsMap.get(product).get("Greensboro");
        if (gbjDate != null) {
          String[] parts = gbjDate.split("/");
          int month = Integer.parseInt(parts[0]);
          int day = Integer.parseInt(parts[1]);
          int year = LocalDate.now().getYear();

          if (month == 1 && LocalDate.now().getMonthValue() == 12) {
            year++;
          }

        LocalDate dateToAdd = LocalDate.of(year, month, day);
          System.out.println(dateToAdd + ": " + dateToAdd.getDayOfWeek());
          if (dateToAdd.getDayOfWeek() == DayOfWeek.SATURDAY) {
            System.out.println("saturday detected, subtracting 1");
            dateToAdd = dateToAdd.minusDays(1);
          }

          if (dateToAdd.getDayOfWeek() == DayOfWeek.SUNDAY) {
            System.out.println("sunday detected, subtracting 2");
            dateToAdd = dateToAdd.minusDays(2);
          }

        System.out.println("Adding date " + dateToAdd + " to gbjData for cycle " + cycle + ", product " + product);
        gbjData.put(product, dateToAdd);
        System.out.println("gbjData size for cycle " + cycle + ": " + gbjData.size());
      }
      }

      if (gbjData.size() > 1) {  // Changed from size() > 1 to handle single dates too
        Optional<LocalDate> minDate = gbjData.values().stream()
            .filter(Objects::nonNull)
            .min(Comparator.naturalOrder());

        System.out.println("Minimum date for " + cycle + ": " + minDate);

        // Calculate and store nomination if found
        minDate.ifPresent(d -> {
          // Convert LocalDate to MM/dd format
          String mmddDate = d.format(DateTimeFormatter.ofPattern("MM/dd"));
          System.out.println("Converted to MM/dd: " + mmddDate);

          // Calculate adjusted date
          Optional<String> adjustedDate = MainLine.adjustSchedulingDate(mmddDate, 4);

          adjustedDate.ifPresent(date -> {
            all32Nominations.put(cycle, date);
            System.out.println("  Cycle " + cycle + " - 32: " + date);
          });
        });
      }

    });

    return all32Nominations;
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

}