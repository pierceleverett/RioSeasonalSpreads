package Handlers;

import static Colonial.ColonialActual.calculateTransitTimes;
import static Colonial.ColonialFungible.fetchFungibleEmails;
import static Colonial.ColonialFungible.processNewMessages;
import static Colonial.ColonialOrigin.processNewOriginStartsEmails;
import static Colonial.ColonialTransitTime.processTransitTimes;
import static Colonial.CsvToMap.createSortedTransitTimeMap;
import static Outlook.ExplorerParser.getAccessToken;

import Colonial.ColonialTransitUpdater;
import com.microsoft.graph.models.Message;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import spark.Request;
import spark.Response;
import spark.Route;

public class ColonialTransitUpdaterHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
    response.type("application/json");
    ColonialTransitUpdater.updateMissingTransitData();
    String accessToken = getAccessToken();
    List<Message> messages = fetchFungibleEmails(accessToken, "automatedreports@rioenergy.com");
    processNewMessages(messages);
    String originFile = "data/Colonial/Origin/HTNOrigin.csv";
    String gbjDeliveryFile = "data/Colonial/Fungible/GBJ.csv";
    String lnjDeliveryFile = "data/Colonial/Fungible/LNJ.csv";
    String gbjOutputFile = "data/Colonial/Actual/GBJactual.csv";
    String lnjOutputFile = "data/Colonial/Actual/LNJactual.csv";
    calculateTransitTimes(originFile, gbjDeliveryFile, lnjDeliveryFile, gbjOutputFile, lnjOutputFile);
    processNewOriginStartsEmails(accessToken, "automatedreports@rioenergy.com");
    return "{\"status\":\"success\"}";
  }

}