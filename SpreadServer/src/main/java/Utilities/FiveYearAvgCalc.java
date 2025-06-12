package Utilities;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class FiveYearAvgCalc {


  public static Map<String, Float> AvgCalc
      (Map<String, Map<String, Float>> allYears, ArrayList<String> yearList) {

    Map<String, Float> year1 = allYears.get(yearList.get(0));
    Map<String, Float> year2 = allYears.get(yearList.get(1));
    Map<String, Float> year3 = allYears.get(yearList.get(2));
    Map<String, Float> year4 = allYears.get(yearList.get(3));
    Map<String, Float> year5 = allYears.get(yearList.get(4));
    Map<String, Float> avgspread = new LinkedHashMap<>();




    for (String date : year1.keySet()) {

      Float sum = year1.get(date) + year2.get(date) + year3.get(date) + year4.get(date) + year5.get(date);
      Float avg = sum / 5;
      avgspread.put(date, avg);
    }

    return avgspread;
  }

}
