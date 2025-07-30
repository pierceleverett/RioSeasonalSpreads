package Outlook;

import static Outlook.ExplorerParser.getAccessToken;

import Outlook.ExplorerSchedulingCalendar.SchedulingCalendar;
import com.microsoft.graph.models.Message;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ExplorerSchedulingPackager {

  public static void main(String[] args) throws IOException {
    String accessToken = getAccessToken();
    Message inputMessage = SchedulingCalendar.fetchMostRecentSchedulingCalendarEmail(accessToken, "automatedreports@rioenergy.com");
    SchedulingCalendar inputCal = SchedulingCalendar.parseCalendarFromEmail(inputMessage);
    System.out.println(eventsByCycle(inputCal));
  }

  public static final Map<String, Integer> MONTH_MAP = Map.ofEntries(
      Map.entry("January", 1),
      Map.entry("February", 2),
      Map.entry("March", 3),
      Map.entry("April", 4),
      Map.entry("May", 5),
      Map.entry("June", 6),
      Map.entry("July", 7),
      Map.entry("August", 8),
      Map.entry("September", 9),
      Map.entry("October", 10),
      Map.entry("November", 11),
      Map.entry("December", 12)
  );



  public static Map<Integer, Map<String, List<String>>> eventsByCycle(
      SchedulingCalendar schedCalendar) {
    Map<String, Map<Integer, List<String>>> data = schedCalendar.data;
    Map<Integer, Map<String, List<String>>> cycleMap = new HashMap<>();
    for (String month : data.keySet()) {
      System.out.println("processing month: " + month);
      for (Integer day : data.get(month).keySet()) {
        System.out.println("processing month: " + month + " day: " + day);
        List<String> eventList = data.get(month).get(day);
        String monthNumber = MONTH_MAP.get(month).toString();
        String dayAsString = day.toString();
        String date = monthNumber + "/" + dayAsString;
        System.out.println("date : " + date);
        for (String event : eventList) {
          System.out.println("Processing event: " + event);
          System.out.println("Finding cycles in event");
          List<Integer> cycles = extractCyclesFromEvent(event);
          System.out.println("Cycles: " + cycles);
          System.out.println("Extracting description");
          String description = extractDescription(event, cycles);
          System.out.println("Description: " + description);
          for (Integer cycle : cycles) {
            System.out.println("Adding data for cycle " + cycle);

            //Cycle map does not contain cycle
            if (!cycleMap.containsKey(cycle)) {
              Map<String, List<String>> innerMap = new HashMap<>();
              List<String> descList = new ArrayList<>();
              descList.add(description);
              innerMap.put(date, descList);
              cycleMap.put(cycle, innerMap);
            }

            //Cycle map already contains cycle and date
            else if (cycleMap.get(cycle).containsKey(date)){
              cycleMap.get(cycle).get(date).add(description);
            }

            //Cycle map already contains cycle but not date
            else {
              List<String> descList = new ArrayList<>();
              descList.add(description);
              cycleMap.get(cycle).put(date, descList);
            }
          }
        }
      }

    }
    return cycleMap;

  }


  public static List<Integer> extractCyclesFromEvent(String event) {
    System.out.println("Finding dates for event: " + event);

    if (event.contains("&")) {
      System.out.println("Processing event with &");
      List<Integer> returnList = new LinkedList<>();
      String[] allWords = event.split(" ");
      for (String word : allWords) {
        if (isInteger(word)) {
          System.out.println("Found an integer, word: " + word);
          Integer wordAsInt = Integer.parseInt(word);
          System.out.println("Adding integer: " + wordAsInt);
          returnList.add(wordAsInt);
        }
      }
      return returnList;
    }

    if (event.contains("-")) {
      System.out.println("Processing event with -");
      String[] allWords = event.split(" ");
      for (String word : allWords) {
        if (word.contains("-")) {
          String[] twoParts = word.split("-");
          Integer firstCycle = Integer.parseInt(twoParts[0].replace("(C", ""));
          Integer secondCycle = Integer.parseInt(twoParts[1].replace(")", ""));
          return findCycleRange(firstCycle, secondCycle);
        }
      }
    }

    else {
      System.out.println("Processing normal entry");
      List<Integer> returnList = new LinkedList<>();
      String[] words = event.split(" ");
      for (String word : words) {
        System.out.println("checking word: " + word);
        String newWord = word.replace(":", "");
        if (isInteger(newWord)) {
          System.out.println("found integer in word: " + word);
          Integer cycle = Integer.parseInt(newWord);
          System.out.println("Adding integer to list: " + cycle);
          returnList.add(cycle);
        }
      }
      return returnList;
    }
    return null;
  }


  public static boolean isInteger(String str) {
    if (str == null) {
      return false;
    }
    try {
      Integer.parseInt(str);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public static List<Integer> findCycleRange(Integer integer1, Integer integer2) {
    List<Integer> returnList = new LinkedList<>();
    if (integer1 > integer2) {
      returnList.add(integer1);
      Integer diff72 = 72 - integer1;
      Integer current = integer1;
      for (int i = 0; i < diff72; i++) {
        current++;  // Increment the number
        returnList.add(current);  // Add to the list
      }

      Integer curr = 0;
      for (int i = 0; i < integer2; i++) {
        curr++;  // Increment the number
        returnList.add(curr);  // Add to the list
      }

      return returnList;

    }

    else {
      Integer cycleDiff = integer2 - integer1;
      returnList.add(integer1);
      Integer current = integer1;
      for (int i = 0; i < cycleDiff; i++) {
        current++;  // Increment the number
        returnList.add(current);  // Add to the list
      }
    }

    return returnList;
  }

  public static String extractDescription(String event, List<Integer> cycles) {
    if (event.contains("&")) {
      String secondCycle = cycles.get(1).toString();
      String[] descriptionParts = event.split(secondCycle + " ");
      return descriptionParts[1];
    }

    if (event.contains("-")) {
      int cycleSize = cycles.size();
      String lastCycle = cycles.get(cycleSize - 1).toString();
      String splitBy = lastCycle + "\\)" + " ";
      String[] descriptionParts = event.split(splitBy);
      return descriptionParts[1];
    }

    else {
      String[] parts = event.split(": ");
      return parts[1];
    }
  }

  //Product --> Cycle -->Location --> Date
  public static Map<String, Map<Integer, Map<String, String>>> getStartDates(Map<Integer, Map<String, List<String>>> inputMap) {
    Map<String, Map<Integer, Map<String, String>>> bothFuelMap = new HashMap<>();
    Map<Integer, Map<String, String>> gasStartsMap = new HashMap<>();
    Map<Integer, Map<String, String>> oilStartsMap = new HashMap<>();
    Map<Integer, Map<String, String>> gasSchedMap = new HashMap<>();
    Map<Integer, Map<String, String>> oilSchedMap = new HashMap<>();

    for (Integer cycle : inputMap.keySet()) {
      for (String date : inputMap.get(cycle).keySet()) {
        for (String event : inputMap.get(cycle).get(date)) {

          if (event.contains("Gas Starts")) {
            String location = event.split(" ")[0];

            //Map doesn't currently contain cycle
            if (!gasStartsMap.containsKey(cycle)) {
              Map<String, String> innerMap = new HashMap<>();
              innerMap.put(location, date);
              gasStartsMap.put(cycle, innerMap);
            }

            //Map contains cycle, but not location
            else {
              gasStartsMap.get(cycle).put(location, date);
            }
          }

          if (event.contains("Oil Starts")) {
            String location = event.split(" ")[0];

            //Map doesn't currently contain cycle
            if (!oilStartsMap.containsKey(cycle)) {
              Map<String, String> innerMap = new HashMap<>();
              innerMap.put(location, date);
              oilStartsMap.put(cycle, innerMap);
            }

            //Map contains cycle, but not location
            else {
              oilStartsMap.get(cycle).put(location, date);
            }
          }

          if (event.contains("Gas Change closed")) {
            String location = event.split(" ")[0];

            //Map doesn't currently contain cycle
            if (!gasSchedMap.containsKey(cycle)) {
              Map<String, String> innerMap = new HashMap<>();
              innerMap.put(location, date);
              gasSchedMap.put(cycle, innerMap);
            }

            //Map contains cycle, but not location
            else {
              gasSchedMap.get(cycle).put(location, date);
            }
          }

          if (event.contains("Oil Change closed")) {
            String location = event.split(" ")[0];

            //Map doesn't currently contain cycle
            if (!oilSchedMap.containsKey(cycle)) {
              Map<String, String> innerMap = new HashMap<>();
              innerMap.put(location, date);
              oilSchedMap.put(cycle, innerMap);
            }

            //Map contains cycle, but not location
            else {
              oilSchedMap.get(cycle).put(location, date);
            }
          }
        }
      }
    }
    bothFuelMap.put("Oil Starts", oilStartsMap);
    bothFuelMap.put("Gas Starts", gasStartsMap);
    bothFuelMap.put("Oil Scheduling End", oilSchedMap);
    bothFuelMap.put("Gas Scheduling End", gasSchedMap);

    return bothFuelMap;
  }

}
