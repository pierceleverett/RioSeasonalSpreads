
import static spark.Spark.after;
import static spark.Spark.before;
import static spark.Spark.options;
import static spark.Spark.post;

import Handlers.SpreadHandler;
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
    int port = 3232;
    Spark.port(port);

    before((req, res) -> {
      req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));
    });


    after((request, response) -> {
      response.header("Access-Control-Allow-Origin", "*");
      response.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
      response.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
    });

    // Handle OPTIONS requests
    options("/*", (request, response) -> {
      return "OK";
    });


    // csv endpoints
    Spark.get("/getSpread", new SpreadHandler());
    Spark.post("/upload-inventory", new InventoryUploadHandler());
    Spark.get("/get-inventory-sheet", new InventoryDownloadHandler());
    Spark.init();
    Spark.awaitInitialization();
    System.out.println("Server started at http://localhost:" + port);
  }
}

