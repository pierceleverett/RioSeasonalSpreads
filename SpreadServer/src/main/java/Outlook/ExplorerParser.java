package Outlook;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.time.Year;
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
  private static final int PAGE_SIZE = 50; // Increased page size to reduce API calls

  public static String getAccessToken() throws IOException {
    System.out.println("[DEBUG] Starting to get access token...");
    long startTime = System.currentTimeMillis();

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

      long duration = System.currentTimeMillis() - startTime;
      System.out.println("[DEBUG] Access token retrieved in " + duration + "ms");

      return json.split("\"access_token\":\"")[1].split("\"")[0];
    }
  }

  public static void explorerTransit(String accessToken) throws IOException {
    System.out.println("[DEBUG] Starting explorerTransit processing...");
    long totalStartTime = System.currentTimeMillis();

    String userId = "automatedreports@rioenergy.com";

    // Format date properly for Graph API
    SimpleDateFormat graphDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    String thirtyDaysAgo = graphDateFormat.format(new Date(System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)));

    try {
      // URL encode the filter parameter and add subject filter
      String filterParam = URLEncoder.encode(
          "receivedDateTime ge " + thirtyDaysAgo +
              " and contains(subject,'Explorer Transit Times')",
          "UTF-8");

      String endpoint = "https://graph.microsoft.com/v1.0/users/" + userId +
          "/mailFolders/inbox/messages?$select=subject,body,receivedDateTime" +
          "&$filter=" + filterParam +
          "&$top=" + PAGE_SIZE;

      System.out.println("[DEBUG] Fetching emails from: " + endpoint);

      JSONArray messages = new JSONArray();
      int pageCount = 0;
      int totalMessages = 0;

      while (endpoint != null && pageCount < 100) { // Safety limit
        pageCount++;
        long pageStartTime = System.currentTimeMillis();
        System.out.println("[DEBUG] Processing page " + pageCount);

        URL url = new URL(endpoint);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        int responseCode = conn.getResponseCode();
        System.out.println("[DEBUG] Response code: " + responseCode);

        if (responseCode != 200) {
          System.err.println("[ERROR] Failed to fetch messages. Response code: " + responseCode);
          try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
              System.err.println("[ERROR] " + errorLine);
            }
          }
          throw new IOException("Failed to fetch messages. HTTP " + responseCode);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
          String line;
          while ((line = br.readLine()) != null) {
            response.append(line);
          }
        }

        JSONObject json = new JSONObject(response.toString());
        JSONArray batch = json.getJSONArray("value");
        totalMessages += batch.length();
        System.out.println("[DEBUG] Page " + pageCount + " contains " + batch.length() + " messages");

        for (int i = 0; i < batch.length(); i++) {
          messages.put(batch.getJSONObject(i));
        }

        endpoint = json.has("@odata.nextLink") ? json.getString("@odata.nextLink") : null;

        long pageDuration = System.currentTimeMillis() - pageStartTime;
        System.out.println("[DEBUG] Page " + pageCount + " processed in " + pageDuration + "ms");

        // Early exit if we're not getting full pages
        if (batch.length() < PAGE_SIZE) break;
      }

      System.out.println("[DEBUG] Total messages fetched: " + totalMessages);
      System.out.println("[DEBUG] Total pages processed: " + pageCount);

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
      String currYear = java.time.Year.now().toString();
      File csvFile = new File("data/Explorer/explorer_transit_times" + currYear + ".csv");
      boolean fileExists = csvFile.exists();
      System.out.println("[DEBUG] CSV file exists: " + fileExists);

      File tempFile = new File("data/Explorer/explorer_transit_times" + currYear + "_temp.csv");

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

      // If existing file has data, read it (excluding header)
      if (fileExists) {
        System.out.println("[DEBUG] Reading existing CSV file...");
        int existingRecords = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
          // Skip the header line
          reader.readLine();

          String line;
          while ((line = reader.readLine()) != null) {
            String[] parts = line.split(",", 2);
            if (parts.length >= 1) {
              dataLines.put(parts[0], line);
              existingRecords++;
            }
          }
        }
        System.out.println("[DEBUG] Loaded " + existingRecords + " existing records");
      }

      System.out.println("[DEBUG] Processing " + messages.length() + " messages...");
      int newRecordsAdded = 0;

      for (int i = 0; i < messages.length(); i++) {
        JSONObject message = messages.getJSONObject(i);
        String subject = message.optString("subject", "");
        String dateStr;

        try {
          dateStr = subject.replaceAll(".*Explorer Transit Times (\\d{1,2}/\\d{1,2}/\\d{2}).*", "$1");
          SimpleDateFormat subjectFormat = new SimpleDateFormat("MM/dd/yy");
          SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd");
          Date date = subjectFormat.parse(dateStr);
          dateStr = outputFormat.format(date);
        } catch (Exception e) {
          dateStr = message.optString("receivedDateTime", "").substring(0, 10);
          System.out.println("[DEBUG] Using receivedDateTime for message with subject: " + subject);
        }

        if (!dataLines.containsKey(dateStr)) {
          newRecordsAdded++;
          JSONObject body = message.optJSONObject("body");
          String bodyContent = body != null ? body.optString("content", "") : "";

          Document doc = Jsoup.parse(bodyContent);
          String textContent = doc.text();
          Map<String, String> transitTimes = new LinkedHashMap<>();

          for (String route : routes) {
            transitTimes.put(route, "");
          }

          for (String route : routes) {
            String patternStr = Pattern.quote(route) + "\\s+([\\d.]+)";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr);
            java.util.regex.Matcher matcher = pattern.matcher(textContent);

            if (matcher.find()) {
              transitTimes.put(route, matcher.group(1));
            }
          }

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

          StringBuilder csvLine = new StringBuilder(dateStr);
          for (String route : routes) {
            csvLine.append(",").append(transitTimes.get(route));
          }

          dataLines.put(dateStr, csvLine.toString());
          System.out.println("[INFO] Added transit times for " + dateStr);
        }
      }

      System.out.println("[DEBUG] Added " + newRecordsAdded + " new records");

      System.out.println("[DEBUG] Writing to temporary file...");
      try (FileWriter writer = new FileWriter(tempFile)) {
        // Write header
        writer.append("Date");
        for (String route : routes) {
          writer.append(",").append(route);
        }
        writer.append("\n");

        // Write sorted data
        int recordsWritten = 0;
        for (String line : dataLines.values()) {
          writer.append(line).append("\n");
          recordsWritten++;
        }
        System.out.println("[DEBUG] Wrote " + recordsWritten + " records to temp file");
      }

      // Replace the old file with the new one
      if (tempFile.exists()) {
        System.out.println("[DEBUG] Replacing old file with new file...");
        if (csvFile.exists()) {
          boolean deleted = csvFile.delete();
          System.out.println("[DEBUG] Old file deleted: " + deleted);
        }
        boolean renamed = tempFile.renameTo(csvFile);
        System.out.println("[DEBUG] Temp file renamed: " + renamed);
      }

    } catch (Exception e) {
      System.err.println("[ERROR] Exception during processing:");
      e.printStackTrace();
      throw new IOException("Failed to process emails", e);
    }

    long totalDuration = System.currentTimeMillis() - totalStartTime;
    System.out.println("[DEBUG] Total processing time: " + totalDuration + "ms");
  }

  public static void main(String[] args) {
    System.out.println("[INFO] Starting ExplorerParser...");
    try {
      String token = getAccessToken();
      System.out.println("[INFO] Access Token acquired successfully");
      explorerTransit(token);
      System.out.println("[INFO] Processing completed successfully");
    } catch (IOException e) {
      System.err.println("[ERROR] Exception in main:");
      e.printStackTrace();
    }
  }
}