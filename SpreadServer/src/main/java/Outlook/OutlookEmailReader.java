package Outlook;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;
public class OutlookEmailReader {

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


  public static void readInboxEmails(String accessToken) throws IOException {
    String userId = "automatedreports@rioenergy.com"; // Replace with your actual user email
    URL url = new URL("https://graph.microsoft.com/v1.0/users/" + userId + "/mailFolders/inbox/messages?$top=10");
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

    for (int i = 0; i < messages.length(); i++) {
      JSONObject message = messages.getJSONObject(i);
      String subject = message.optString("subject", "(No Subject)");
      String bodyPreview = message.optString("bodyPreview", "(No Preview)");
      JSONObject from = message.optJSONObject("from");
      String sender = from != null ? from.getJSONObject("emailAddress").optString("name") + " <" +
          from.getJSONObject("emailAddress").optString("address") + ">" : "(Unknown Sender)";

      System.out.println("From: " + sender);
      System.out.println("Subject: " + subject);
      System.out.println("Body Preview: " + bodyPreview);
      System.out.println("--------------------------------------------------");
    }
  }



  public static void listUsers(String accessToken) throws IOException {
    URL url = new URL("https://graph.microsoft.com/v1.0/users");
    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Authorization", "Bearer " + accessToken);
    conn.setRequestProperty("Accept", "application/json");

    int responseCode = conn.getResponseCode();
    InputStream is = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();

    try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
      String line;
      while ((line = br.readLine()) != null) {
        System.out.println(line);
      }
    }
  }


  public static void main(String[] args) {
    try {
      String token = getAccessToken();
      System.out.println("Access Token acquired.");
      readInboxEmails(token);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
