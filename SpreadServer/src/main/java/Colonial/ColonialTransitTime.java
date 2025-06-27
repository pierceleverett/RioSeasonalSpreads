package Colonial;

import static Outlook.ExplorerParser.getAccessToken;

import com.microsoft.graph.models.Message;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.MessageCollectionPage;
import java.net.ProtocolException;
import java.net.URL;
import java.time.OffsetDateTime;
import javax.net.ssl.HttpsURLConnection;
import okhttp3.Request;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransitTimeParser {
  private static final SimpleDateFormat EMAIL_DATE_FORMAT = new SimpleDateFormat("MM/dd/yy HH:mm");
  private static final SimpleDateFormat CSV_DATE_FORMAT = new SimpleDateFormat("MM/dd/yy");
  private static final Pattern TRANSIT_PATTERN = Pattern.compile(
      "\\|\\s*(\\w+)\\s*\\|\\s*(\\w+)\\s*\\|\\s*(\\d+)\\s*\\|\\s*(\\d+)\\s*\\|\\s*(\\d+)\\s*\\|\\s*(\\d+)\\s*\\|\\s*(\\d+)\\s*\\|");

  public static void main(String[] args) throws IOException {
    String accessToken = getAccessToken();
    processTransitTimeEmails(accessToken);
  }

  public static void processTransitTimeEmails(String accessToken) throws IOException {
    String userId = "automatedreports@rioenergy.com";
    URL url = new URL("https://graph.microsoft.com/v1.0/users/" + userId + "/mailFolders/inbox/messages?$top=50&$select=subject,body,receivedDateTime");
    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
    conn.setRequestProperty("Accept", "application/json");

    try {
      // Search for emails with subject containing "Colonial - TRANSIT TIMES"
      MessageCollectionPage messages = conn.me().messages()
          .buildRequest()
          .filter("contains(subject, 'Colonial - TRANSIT TIMES')")
          .select("subject,receivedDateTime,body")
          .top(100) // Adjust based on expected volume
          .get();

      while (messages != null) {
        for (Message message : messages.getCurrentPage()) {
          try {
            processEmail(message);
          } catch (Exception e) {
            System.err.println("Error processing email: " + e.getMessage());
          }
        }

        if (messages.getNextPage() == null) {
          break;
        }
        messages = messages.getNextPage().buildRequest().get();
      }
    } catch (Exception e) {
      System.err.println("Error fetching emails: " + e.getMessage());
    }
  }

  private static void processEmail(Message message) throws Exception {
    if (message.body == null || message.body.content == null) {
      return;
    }

    String emailContent = message.body.content;
    OffsetDateTime emailDate = message.receivedDateTime;

    // Parse transit times from email content
    List<TransitTime> transitTimes = parseTransitTimes(emailContent);

    // Group by route and product type
    Map<String, List<TransitTime>> groupedTimes = new HashMap<>();
    for (TransitTime tt : transitTimes) {
      String key = tt.from + tt.to + "-" + (tt.isGas ? "GAS" : "DISTILLATES");
      groupedTimes.computeIfAbsent(key, k -> new ArrayList<>()).add(tt);
    }

    // Process the specific routes we're interested in
    processRoute(groupedTimes, "HTNGBJ-GAS", emailDate);
    processRoute(groupedTimes, "HTNGBJ-DISTILLATES", emailDate);
    processRoute(groupedTimes, "GBJLNL-GAS", emailDate);
    processRoute(groupedTimes, "GBJLNL-DISTILLATES", emailDate);
  }

  private static List<TransitTime> parseTransitTimes(String emailContent) {
    List<TransitTime> transitTimes = new ArrayList<>();
    Matcher matcher = TRANSIT_PATTERN.matcher(emailContent);

    while (matcher.find()) {
      try {
        String from = matcher.group(1);
        String to = matcher.group(2);
        int cycle = Integer.parseInt(matcher.group(3));

        // Gas data
        int gasDays = Integer.parseInt(matcher.group(4));
        int gasHours = Integer.parseInt(matcher.group(5));
        transitTimes.add(new TransitTime(from, to, cycle, true, gasDays, gasHours));

        // Distillates data
        int distDays = Integer.parseInt(matcher.group(6));
        int distHours = Integer.parseInt(matcher.group(7));
        transitTimes.add(new TransitTime(from, to, cycle, false, distDays, distHours));
      } catch (Exception e) {
        System.err.println("Error parsing transit time: " + e.getMessage());
      }
    }

    return transitTimes;
  }

  private static void processRoute(Map<String, List<TransitTime>> groupedTimes, String routeKey, OffsetDateTime emailDate) {
    List<TransitTime> times = groupedTimes.get(routeKey);
    if (times == null || times.isEmpty()) {
      return;
    }

    String csvFileName = routeKey + ".csv";
    String dateStr = CSV_DATE_FORMAT.format(emailDate);

    try {
      // Read existing CSV or create new
      Map<Integer, String> cycleValues = new TreeMap<>();
      boolean fileExists = Files.exists(Paths.get(csvFileName));
      int maxCycle = 72; // Default maximum cycle number

      if (fileExists) {
        // Read existing values
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFileName))) {
          String line;
          boolean firstLine = true;
          List<String> lines = new ArrayList<>();

          while ((line = reader.readLine()) != null) {
            lines.add(line);
            if (firstLine) {
              firstLine = false;
              continue; // Skip header
            }

            String[] values = line.split(",");
            if (values[0].equals(dateStr)) {
              // Found existing entry for this date
              for (int i = 1; i < values.length; i++) {
                if (!values[i].isEmpty()) {
                  cycleValues.put(i, values[i]);
                }
              }
            }
          }
        }
      }

      // Update with new values
      for (TransitTime tt : times) {
        double decimalHours = tt.days + (tt.hours / 24.0);
        cycleValues.put(tt.cycle, String.format("%.2f", decimalHours));
        if (tt.cycle > maxCycle) {
          maxCycle = tt.cycle;
        }
      }

      // Write back to CSV
      if (fileExists) {
        // For existing files, we need to rewrite the entire file
        rewriteCSVFile(csvFileName, dateStr, cycleValues, maxCycle);
      } else {
        // For new files, just append the new row
        appendToCSVFile(csvFileName, dateStr, cycleValues, maxCycle);
      }
    } catch (Exception e) {
      System.err.println("Error processing route " + routeKey + ": " + e.getMessage());
    }
  }

  private static void rewriteCSVFile(String filename, String newDate, Map<Integer, String> newValues, int maxCycle) throws IOException {
    // Read all existing records
    List<String> lines = new ArrayList<>();
    boolean dateExists = false;

    try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
      String line;
      boolean firstLine = true;

      while ((line = reader.readLine()) != null) {
        if (firstLine) {
          // Process header line
          lines.add(updateHeaderLine(line, maxCycle));
          firstLine = false;
          continue;
        }

        String[] values = line.split(",");
        if (values[0].equals(newDate)) {
          // Replace this line with updated values
          lines.add(createCSVLine(newDate, newValues, maxCycle));
          dateExists = true;
        } else {
          lines.add(line);
        }
      }
    }

    // If date didn't exist, add new line
    if (!dateExists) {
      lines.add(createCSVLine(newDate, newValues, maxCycle));
    }

    // Sort lines by date (except header)
    if (lines.size() > 1) {
      sortLinesByDate(lines);
    }

    // Write all lines back to file
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename))) {
      for (String line : lines) {
        writer.write(line);
        writer.newLine();
      }
    }
  }

  private static void appendToCSVFile(String filename, String date, Map<Integer, String> values, int maxCycle) throws IOException {
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true))) {
      if (Files.size(Paths.get(filename)) == 0) {
        // Write header for new file
        writer.write(createHeaderLine(maxCycle));
        writer.newLine();
      }

      // Write data line
      writer.write(createCSVLine(date, values, maxCycle));
      writer.newLine();
    }
  }

  private static String updateHeaderLine(String existingHeader, int maxCycle) {
    String[] headerParts = existingHeader.split(",");
    if (headerParts.length - 1 >= maxCycle) {
      return existingHeader; // No update needed
    }

    // Extend header with additional cycle numbers
    StringBuilder sb = new StringBuilder(existingHeader);
    for (int i = headerParts.length; i <= maxCycle; i++) {
      sb.append(",").append(i);
    }
    return sb.toString();
  }

  private static String createHeaderLine(int maxCycle) {
    StringBuilder sb = new StringBuilder("Date");
    for (int i = 1; i <= maxCycle; i++) {
      sb.append(",").append(i);
    }
    return sb.toString();
  }

  private static String createCSVLine(String date, Map<Integer, String> values, int maxCycle) {
    StringBuilder sb = new StringBuilder(date);
    for (int i = 1; i <= maxCycle; i++) {
      sb.append(",").append(values.getOrDefault(i, ""));
    }
    return sb.toString();
  }

  private static void sortLinesByDate(List<String> lines) {
    // Skip header line
    if (lines.size() <= 1) return;

    String header = lines.get(0);
    List<String> dataLines = lines.subList(1, lines.size());

    dataLines.sort((line1, line2) -> {
      String date1 = line1.split(",")[0];
      String date2 = line2.split(",")[0];
      try {
        Date d1 = CSV_DATE_FORMAT.parse(date1);
        Date d2 = CSV_DATE_FORMAT.parse(date2);
        return d1.compareTo(d2);
      } catch (ParseException e) {
        return date1.compareTo(date2);
      }
    });

    // Rebuild lines list
    lines.clear();
    lines.add(header);
    lines.addAll(dataLines);
  }

  private static class TransitTime {
    String from;
    String to;
    int cycle;
    boolean isGas;
    int days;
    int hours;

    public TransitTime(String from, String to, int cycle, boolean isGas, int days, int hours) {
      this.from = from;
      this.to = to;
      this.cycle = cycle;
      this.isGas = isGas;
      this.days = days;
      this.hours = hours;
    }
  }
}