package Colonial;

import com.microsoft.graph.models.Message;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static Colonial.ColonialTransitTime.*;

public class ColonialTransitUpdater {

  public static String currYear = java.time.Year.now().toString();
  public static String lastYear = java.time.Year.now().minusYears(1).toString();

  private static String[] CSV_PATHS = {
      "data/Colonial/Transit/HTNGBJ-GAS" + currYear + ".csv",
      "data/Colonial/Transit/HTNGBJ-GAS" + lastYear + ".csv",
  };

  public static void updateMissingTransitData() {
    try {
      LocalDate latestDate = getLatestDateAcrossCSVs();
      LocalDate today = LocalDate.now();
      List<LocalDate> missingDates = getMissingDates(latestDate, today);

      if (missingDates.isEmpty()) {
        System.out.println("No missing dates found. CSVs are up to date.");
        return;
      }

      System.out.println("Missing dates: " + missingDates);

      String accessToken = Outlook.ExplorerParser.getAccessToken();
      String userPrincipalName = "automatedreports@rioenergy.com";
      List<Message> allMessages = ColonialTransitTime.fetchTransitTimeEmails(accessToken, userPrincipalName, missingDates);


      List<Message> filteredMessages = allMessages.stream()
          .filter(msg -> msg.receivedDateTime != null &&
              missingDates.contains(msg.receivedDateTime.toLocalDate()))
          .collect(Collectors.toList());

      System.out.println("Found " + filteredMessages.size() + " messages for missing dates.");
      ColonialTransitTime.processMessages(filteredMessages);

    } catch (Exception e) {
      System.err.println("Error updating missing transit data: " + e.getMessage());
      e.printStackTrace();
    }
  }

  private static LocalDate getLatestDateAcrossCSVs() {
    LocalDate latest = LocalDate.MIN;
    for (String path : CSV_PATHS) {
      try {
        List<String> lines = Files.readAllLines(Paths.get(path));
        if (lines.size() <= 1) {
          continue;
        }
        for (int i = 1; i < lines.size(); i++) {
          String[] row = lines.get(i).split(",", -1);
          try {
            LocalDate date = LocalDate.parse(row[0]);
            if (date.isAfter(latest)) {
              latest = date;
            }
          } catch (Exception ignored) {}
        }
      } catch (IOException e) {
        System.err.println("Could not read file: " + path + " - " + e.getMessage());
      }
    }
    return latest;
  }

  private static List<LocalDate> getMissingDates(LocalDate start, LocalDate end) {
    List<LocalDate> dates = new ArrayList<>();
    LocalDate current = start.plusDays(1);
    while (!current.isAfter(end)) {
      dates.add(current);
      current = current.plusDays(1);
    }
    return dates;
  }

  public static void main(String[] args) {
    updateMissingTransitData();
  }
}