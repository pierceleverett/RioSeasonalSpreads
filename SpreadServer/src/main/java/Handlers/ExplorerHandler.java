package Handlers;
import static Outlook.ExplorerParser.explorerTransit;
import static Outlook.ExplorerParser.getAccessToken;

import com.google.gson.Gson;
import java.io.FileReader;
import java.io.BufferedReader;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;
import spark.Request;
import spark.Response;
import spark.Route;

public class ExplorerHandler implements Route {
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
//    try {
//      String token = getAccessToken();
//      explorerTransit(token);
//    } catch (Exception e) {
//      response.status(500);
//      return new HashMap<String, String>() {{
//        put("error", "Failed to update data");
//        put("details", e.getMessage());
//      }};
//    }

    // 4. Read the CSV file and extract data for the requested route
    LinkedList<Year> yearList = new LinkedList<>();
    Year currYear = java.time.Year.now().plusYears(1);
    System.out.println("current year: " + currYear);
    Year prev1Year = currYear.minusYears(1);
    Year prev2Year = currYear.minusYears(2);
    Year prev3Year = currYear.minusYears(3);
    Year prev4Year = currYear.minusYears(4);
    Year prev5Year = currYear.minusYears(5);
    yearList.add(currYear);
    yearList.add(prev1Year);
    yearList.add(prev2Year);
    yearList.add(prev3Year);
    yearList.add(prev4Year);
    yearList.add(prev5Year);

    LinkedHashMap<String, LinkedHashMap<String, String>> allRouteData = new LinkedHashMap<>();

    for (Year year : yearList) {
      System.out.println("processing data for: " + year);
      String csvPath = "data/Explorer/explorer_transit_times" + year.toString() + ".csv";
      System.out.println("opening file: " + csvPath);
      LinkedHashMap<String, String> routeData = new LinkedHashMap();
      try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
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
        if (routeData.isEmpty()) {
          return null;
        }
        else {
          allRouteData.put(year.toString(), routeData);
        }
      } catch (Exception e) {
        response.status(500);
        System.out.println("Failed to open file: " + csvPath);;
      }
    }


    // 5. Return the response
    response.type("application/json");
    return new Gson().toJson(allRouteData);
    }




};


