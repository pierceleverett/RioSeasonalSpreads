package Outlook;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class ExplorerParser {

  private static final String TENANT_ID = "56df527c-e3c6-4938-80ac-ef9713c68950";
  private static final String CLIENT_ID = "e8342b20-5bd8-4a67-acba-71b8c5bd361f";
  private static final String CLIENT_SECRET = "rwT8Q~MeynLgJWyN7oiBYRvi4MS-zErQsLpEvco4";
  private static final String TOKEN_ENDPOINT = "https://login.microsoftonline.com/" + TENANT_ID + "/oauth2/v2.0/token";
  private static final String GRAPH_ENDPOINT = "https://graph.microsoft.com/v1.0/users/automatedreports@rioenergy.com/mailFolders/inbox/messages";

  public static String getAccessToken() throws IOException {
    URL url = new URL(TOKEN_ENDPOINT);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

    String formParams = "client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") +
        "&scope=" + URLEncoder.encode("https://graph.microsoft.com/.default", "UTF-8") +
        "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, "UTF-8") +
        "&grant_type=client_credentials";

    try (OutputStream os = conn.getOutputStream()) {
      os.write(formParams.getBytes("UTF-8"));
    }

    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
      StringBuilder response = new StringBuilder();
      String line;
      while ((line = br.readLine()) != null) {
        response.append(line);
      }
      String json = response.toString();
      return json.split("\"access_token\":\"")[1].split("\"")[0];
    }
  }



  public static void explorerTransit(String accessToken) throws IOException {
    String userId = "automatedreports@rioenergy.com";
    URL url = new URL("https://graph.microsoft.com/v1.0/users/" + userId + "/mailFolders/inbox/messages?$top=50&$select=subject,body,receivedDateTime");
    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
    conn.setRequestProperty("Accept", "application/json");

    int responseCode = conn.getResponseCode();
    InputStream is = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();

    StringBuilder response = new StringBuilder();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
      String line;
      while ((line = br.readLine()) != null) {
        response.append(line);
      }
    }

    JSONObject json = new JSONObject(response.toString());
    JSONArray messages = json.getJSONArray("value");

    // Define all possible routes in the desired order
    String[] routes = {
        "Port Arthur-Houston",
        "Port Arthur-Greenville",
        "Pasadena-Greenville",
        "Greenville-Glenpool",
        "Port Arthur-Glenpool",
        "Pasadena-Glenpool",
        "Port Arthur-Wood River",
        "Glenpool-Wood River",
        "Wood River-Hammond",
        "Glenpool-Hammond Area",
        "HOUSTON - HMD TRANSIT",
        "STORAGE",
        "DAYS"
    };

    // Prepare CSV file
    File csvFile = new File("data/explorer_transit_times.csv");
    boolean fileExists = csvFile.exists();

    // We'll use a temporary file to ensure we always have headers
    File tempFile = new File("data/explorer_transit_times_temp.csv");

    // Store all data lines for sorting
    Map<String, String> dataLines = new TreeMap<>(new Comparator<String>() {
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

      @Override
      public int compare(String date1, String date2) {
        try {
          return dateFormat.parse(date1).compareTo(dateFormat.parse(date2));
        } catch (Exception e) {
          return date1.compareTo(date2);
        }
      }
    });

    try {
      // If existing file has data, read it (excluding header)
      if (fileExists) {
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
          // Skip the header line
          reader.readLine();

          // Read all existing data
          String line;
          while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",", 2);
            if (parts.length >= 1) {
              dataLines.put(parts[0], line);
            }
          }
        }
      }

      // Process new messages
      for (int i = 0; i < messages.length(); i++) {
        JSONObject message = messages.getJSONObject(i);
        String subject = message.optString("subject", "");

        if (subject.contains("Explorer Transit Times")) {
          // Extract date from subject
          String dateStr;
          try {
            dateStr = subject.replaceAll(".*Explorer Transit Times (\\d{1,2}/\\d{1,2}/\\d{2}).*", "$1");
            SimpleDateFormat subjectFormat = new SimpleDateFormat("MM/dd/yy");
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date date = subjectFormat.parse(dateStr);
            dateStr = outputFormat.format(date);
          } catch (Exception e) {
            dateStr = message.optString("receivedDateTime", "").substring(0, 10);
          }

          // Only process if date doesn't exist or we want to update
          if (!dataLines.containsKey(dateStr)) {
            // Get email body content
            JSONObject body = message.optJSONObject("body");
            String bodyContent = body != null ? body.optString("content", "") : "";

            // Parse HTML content
            Document doc = Jsoup.parse(bodyContent);
            String textContent = doc.text();
            Map<String, String> transitTimes = new LinkedHashMap<>();

            // Initialize all routes with empty values
            for (String route : routes) {
              transitTimes.put(route, "");
            }

            // Parse each route's transit time
            for (String route : routes) {
              String patternStr = Pattern.quote(route) + "\\s+([\\d.]+)";
              java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr);
              java.util.regex.Matcher matcher = pattern.matcher(textContent);

              if (matcher.find()) {
                transitTimes.put(route, matcher.group(1));
              }
            }

            // Special handling for STORAGE and DAYS
            java.util.regex.Pattern storagePattern = java.util.regex.Pattern.compile("STORAGE\\s+([\\d]+)");
            java.util.regex.Matcher storageMatcher = storagePattern.matcher(textContent);
            if (storageMatcher.find()) {
              transitTimes.put("STORAGE", storageMatcher.group(1));
            }

            java.util.regex.Pattern daysPattern = java.util.regex.Pattern.compile("DAYS\\s+([\\d.]+)");
            java.util.regex.Matcher daysMatcher = daysPattern.matcher(textContent);
            if (daysMatcher.find()) {
              transitTimes.put("DAYS", daysMatcher.group(1));
            }

            // Build CSV line
            StringBuilder csvLine = new StringBuilder(dateStr);
            for (String route : routes) {
              csvLine.append(",").append(transitTimes.get(route));
            }

            // Add to data lines (will be automatically sorted by TreeMap)
            dataLines.put(dateStr, csvLine.toString());
            System.out.println("Added transit times for " + dateStr);
          }
        }
      }

      // Write all data to temp file (sorted by date)
      try (FileWriter writer = new FileWriter(tempFile)) {
        // Write header
        writer.append("Date");
        for (String route : routes) {
          writer.append(",").append(route);
        }
        writer.append("\n");

        // Write sorted data
        for (String line : dataLines.values()) {
          writer.append(line).append("\n");
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    // Replace the old file with the new one
    if (tempFile.exists()) {
      if (csvFile.exists()) {
        csvFile.delete();
      }
      tempFile.renameTo(csvFile);
    }
  }





  public static void main(String[] args) {
    try {
      String token = getAccessToken();
      System.out.println("Access Token acquired.");
      explorerTransit(token);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
