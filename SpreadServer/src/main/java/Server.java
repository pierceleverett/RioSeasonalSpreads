
import static spark.Spark.after;
import static spark.Spark.before;
import static spark.Spark.options;
import static spark.Spark.post;

import Handlers.ActualTransitHandler;
import Handlers.BetweenFuelSpreadHandler;
import Handlers.ColonialTransitHandler;
import Handlers.ColonialTransitUpdaterHandler;
import Handlers.ExplorerHandler;
import Handlers.ExplorerSchedulingHandler;
import Handlers.GCSpreadHandler;
import Handlers.GCUpdateHandler;
import Handlers.LatestUploadHandler;
import Handlers.MagellanGraphHandler;
import Handlers.MainLineHandler;
import Handlers.OriginStartsHandler;
import Handlers.RecentDateInfoHandler;
import Handlers.RecentFungibleHandler;
import Handlers.SchedulingCalendarHandler;
import Handlers.SpreadHandler;
import Handlers.SpreadsUpdaterHandler;
import Handlers.StubNomHandler;
import javax.servlet.MultipartConfigElement;
import spark.Spark;

import Handlers.InventoryUploadHandler;
import Handlers.InventoryDownloadHandler;


/**
 * The Main class of our project. This is where execution begins. Note: For this first sprint, you
 * will not be running the parser through main(), but rather interacting with the parser through
 * extensive testing!
 */
public final class Server {

  /**
   * The main method is the entry point of the application.
   *
   * @param args command-line arguments passed to the program
   */
  public static void main(String[] args) {
    try {
      int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
      Spark.port(port);

      before((req, res) -> {
        req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));
      });

      // Updated CORS handling
// Updated CORS handling
      after((request, response) -> {
        // Only add CORS headers if this isn't an OPTIONS request
        if (!request.requestMethod().equals("OPTIONS")) {
          String origin = request.headers("Origin");
          // Allow both production and local development origins
          if ("https://riodashboard.up.railway.app".equals(origin) ||
              "http://localhost:5173".equals(origin)) {
            response.header("Access-Control-Allow-Origin", origin);
          }
          response.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
          response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
          response.header("Access-Control-Allow-Credentials", "true");
        }
      });

// Handle preflight requests
      options("/*", (request, response) -> {
        String origin = request.headers("Origin");
        if ("https://riodashboard.up.railway.app".equals(origin) ||
            "http://localhost:5173".equals(origin)) {
          response.header("Access-Control-Allow-Origin", origin);
        }
        response.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.header("Access-Control-Allow-Credentials", "true");
        return "OK";
      });

      // Register routes
      System.out.println("Registering routes...");
      Spark.get("/getSpread", new SpreadHandler());
      Spark.post("/upload-inventory", new InventoryUploadHandler());
      Spark.get("/get-inventory-sheet", new InventoryDownloadHandler());
      Spark.get("/getLatestDate", new LatestUploadHandler());
      Spark.get("/getMagellanData", new MagellanGraphHandler());
      Spark.get("/getBetweenSpreads", new BetweenFuelSpreadHandler());
      Spark.get("/getExplorerData", new ExplorerHandler());
      Spark.post("/updateSpreads", new SpreadsUpdaterHandler());
      Spark.get("/getGCSpreads", new GCSpreadHandler());
      Spark.get("/getColonialTransit", new ColonialTransitHandler());
      Spark.post("/updateColonialTransit", new ColonialTransitUpdaterHandler());
      Spark.get("/getRealTransit", new ActualTransitHandler());
      Spark.get("/getRecentFungible", new RecentFungibleHandler());
      Spark.post("/updateGC", new GCUpdateHandler());
      Spark.get("/getMainLine", new MainLineHandler());
      Spark.get("/getOriginStart", new OriginStartsHandler());
      Spark.get("/getStubNoms", new StubNomHandler());
      Spark.get("/getDateInfo", new RecentDateInfoHandler());
      Spark.get("/getExplorerStarts", new ExplorerSchedulingHandler());
      Spark.init();
      Spark.awaitInitialization();
      System.out.println("✅ Server started at http://localhost:" + port);
    } catch (Exception e) {
      System.err.println("❌ Server failed to start: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
