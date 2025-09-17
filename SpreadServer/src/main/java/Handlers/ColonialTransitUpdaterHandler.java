package Handlers;

import static Colonial.ColonialActual.calculateTransitTimes;
import static Colonial.FungibleUpdater.getLastProcessedDate;
import static Colonial.FungibleUpdater.processNewBulletins;
import static Outlook.ExplorerParser.getAccessToken;
import Colonial.ColonialOrigin;
import Colonial.ColonialTransitUpdater;
import java.io.IOException;
import java.time.LocalDate;
import spark.Request;
import spark.Response;
import spark.Route;

public class ColonialTransitUpdaterHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
  //public static void main(String[] args) throws IOException {
    //response.type("application/json");
    String accessToken = getAccessToken();
    String curentYear = java.time.Year.now().toString();
    System.out.println(curentYear);
    String originFile = "data/Colonial/Origin/HTNOrigin" + curentYear+ ".csv";
    String gbjDeliveryFile = "data/Colonial/Fungible/GBJ" + curentYear + ".csv";
    String lnjDeliveryFile = "data/Colonial/Fungible/LNJ" + curentYear + ".csv";
    String gbjOutputFile = "data/Colonial/Actual/GBJactual" + curentYear + ".csv";
    String lnjOutputFile = "data/Colonial/Actual/LNJactual" + curentYear + ".csv";

    //Update the estimated transit data
    System.out.println("Starting to update missing transit data");
    ColonialTransitUpdater.updateMissingTransitData();
    System.out.println("Finished transit data, now going to fungible");

    //Update the fungible data
    LocalDate lastProcessedDate = getLastProcessedDate();
    System.out.println("last processed date: "+ lastProcessedDate);
    System.out.println("going to process missing fungi (if any)");
    processNewBulletins(lastProcessedDate);
    System.out.println("done processing, now moving to origin starts");

    //Update the origin starts if needed
    //ColonialOrigin.processNewOriginStartsEmails(accessToken, "automatedreports@rioenergy.com");

    //Update the actual transit times
    System.out.println("recalculating actual transit times");
    calculateTransitTimes(originFile, gbjDeliveryFile, lnjDeliveryFile, gbjOutputFile, lnjOutputFile);
    //return;
    return "{\"status\":\"success\"}";
  }

}