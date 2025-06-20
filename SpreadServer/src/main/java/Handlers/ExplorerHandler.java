package Handlers;
import static Outlook.OutlookEmailReader.explorerTransit;
import static Outlook.OutlookEmailReader.getAccessToken;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import spark.Request;
import spark.Response;
import spark.Route;
import Outlook.OutlookEmailReader.*;

public class ExplorerHandler implements Route {
  private static final String CSV_FILE = "data/explorer_transit_times.csv";
  private static final List<String> ROUTES = Arrays.asList(
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
  );

  public Object handle(Request request, Response response) throws Exception {
    // 1. Get the requested route parameter
    String route = request.queryParams("route");

    // 2. Validate the route parameter
    if (route == null || !ROUTES.contains(route)) {
      response.status(400);
      return new HashMap<String, String>() {{
        put("error", "Invalid route parameter");
        put("valid_routes", ROUTES.toString());
      }};
    }

    // 3. Update the data (call your existing method)
    try {
      String token = getAccessToken();
      explorerTransit(token);
    } catch (Exception e) {
      response.status(500);
      return new HashMap<String, String>() {{
        put("error", "Failed to update data");
        put("details", e.getMessage());
      }};
    }

    // 4. Read the CSV file and extract data for the requested route
    LinkedHashMap<String, String> routeData = new LinkedHashMap();
    try (BufferedReader br = new BufferedReader(new FileReader(CSV_FILE))) {
      // Read header to find column index
      String header = br.readLine();
      String[] headers = header.split(",");
      int routeIndex = -1;
      for (int i = 0; i < headers.length; i++) {
        if (headers[i].equals(route)) {
          routeIndex = i;
          break;
        }
      }

      if (routeIndex == -1) {
        response.status(500);
        return new HashMap<String, String>() {{
          put("error", "Route not found in data file");
        }};
      }

      // Read data lines
      String line;
      while ((line = br.readLine()) != null) {
        String[] values = line.split(",");
        if (values.length > routeIndex) {
          routeData.put(values[0], values[routeIndex]);

        }
      }
    } catch (Exception e) {
      response.status(500);
      return new HashMap<String, String>() {{
        put("error", "Failed to read data file");
        put("details", e.getMessage());
      }};
    }

    // 5. Return the response
    response.type("application/json");
    return new Gson().toJson(routeData);
    }};


