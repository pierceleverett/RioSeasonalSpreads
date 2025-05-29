package Utilities;

import static Utilities.SpreadCalculator.spreadCalculator;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class FiveYearAvgCalc {

  public static void main(String[] args) throws IOException {
    Map<String, Map<String, Float>> testmap = new LinkedHashMap<>();
    Map<String, Float> map2020 = spreadCalculator("G", "K", "2020");
    Map<String, Float> map2021 = spreadCalculator("G", "K", "2021");
    Map<String, Float> map2022 = spreadCalculator("G", "K", "2022");
    Map<String, Float> map2023 = spreadCalculator("G", "K", "2023");
    Map<String, Float> map2024 = spreadCalculator("G", "K", "2024");
    testmap.put("2020", map2020);
    testmap.put("2021", map2021);
    testmap.put("2022", map2022);
    testmap.put("2023", map2023);
    testmap.put("2024", map2024);
    AvgCalc(testmap);
  }

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
