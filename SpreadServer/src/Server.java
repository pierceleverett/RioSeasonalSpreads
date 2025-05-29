
import static spark.Spark.after;
import static spark.Spark.options;

import Handlers.SpreadHandler;
import spark.Spark;


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

    after((request, response) -> {
      response.header("Access-Control-Allow-Origin", "*");
      response.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
      response.header("Access-Control-Allow-Headers", "Content-Type");
    });

    // Handle OPTIONS requests
    options("/*", (request, response) -> {
      return "OK";
    });


    // csv endpoints
    Spark.get("/getSpread", new SpreadHandler());
    Spark.init();
    Spark.awaitInitialization();
    System.out.println("Server started at http://localhost:" + port);
  }
}

