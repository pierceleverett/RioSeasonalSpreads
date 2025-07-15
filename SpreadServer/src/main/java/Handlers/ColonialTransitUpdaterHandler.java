package Handlers;

import static Colonial.ColonialActual.calculateTransitTimes;
import static Colonial.FungibleUpdater.getLastProcessedDate;
import static Colonial.FungibleUpdater.processNewBulletins;
import static Colonial.OriginUpdater.shouldUpdateOriginData;
import static Outlook.ExplorerParser.getAccessToken;
import Colonial.ColonialOrigin;
import Colonial.ColonialTransitUpdater;
import java.time.LocalDate;
import spark.Request;
import spark.Response;
import spark.Route;

public class ColonialTransitUpdaterHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
    response.type("application/json");
    String accessToken = getAccessToken();
    String originFile = "data/Colonial/Origin/HTNOrigin.csv";
    String gbjDeliveryFile = "data/Colonial/Fungible/GBJ.csv";
    String lnjDeliveryFile = "data/Colonial/Fungible/LNJ.csv";
    String gbjOutputFile = "data/Colonial/Actual/GBJactual.csv";
    String lnjOutputFile = "data/Colonial/Actual/LNJactual.csv";

    //Update the estimated transit data
    ColonialTransitUpdater.updateMissingTransitData();

    //Update the fungible data
    LocalDate lastProcessedDate = getLastProcessedDate();
    processNewBulletins(lastProcessedDate);

    //Update the origin starts if needed
    if (shouldUpdateOriginData()) {
      System.out.println("Last cycle date is approaching, checking for new origin emails...");
      ColonialOrigin.processNewOriginStartsEmails(accessToken, "automatedreports@rioenergy.com");
    } else {
      System.out.println("No update needed");
    }

    //Update the actual transit times
    calculateTransitTimes(originFile, gbjDeliveryFile, lnjDeliveryFile, gbjOutputFile, lnjOutputFile);

    return "{\"status\":\"success\"}";
  }

}