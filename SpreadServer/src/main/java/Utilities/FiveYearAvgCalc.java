package Utilities;

import java.util.LinkedHashMap;
import java.util.Map;

public class FiveYearAvgCalc {


  public static Map<String, Float> AvgCalc(Map<String, Map<String, Float>> allYears) {
    Map<String, Float> data2020 = allYears.get("2020");
    Map<String, Float> data2021 = allYears.get("2021");
    Map<String, Float> data2022 = allYears.get("2022");
    Map<String, Float> data2023 = allYears.get("2023");
    Map<String, Float> data2024 = allYears.get("2024");
    Map<String, Float> avgspread = new LinkedHashMap<>();




    for (String date : data2020.keySet()) {

      Float sum = data2020.get(date) + data2021.get(date) + data2022.get(date) + data2023.get(date) + data2024.get(date);
      Float avg = sum / 5;
      avgspread.put(date, avg);
      System.out.println(date + " average: " + avg);
    }

    return avgspread;
  }

}
