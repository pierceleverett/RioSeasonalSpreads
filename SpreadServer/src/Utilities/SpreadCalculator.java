package Utlities;

import static java.lang.Integer.parseInt;

import java.util.Map;
import java.util.Objects;

public class SpreadCalculator {

  public Map<String, Float> CalculateSpread(String year, String firstMonth, String secondMonth) {
    String startDate = null;
    String endDate = null;


    if (Objects.equals(firstMonth, "F")) {
      startDate = "01/01/" + (Integer.parseInt(year) - 1);
      endDate = "12/31" + (Integer.parseInt(year) -1);
    }

    if (Objects.equals(firstMonth, "G")) {
      startDate = "02/01/" + (Integer.parseInt(year) - 1);
      endDate = "01/31/" + year;
    }

    if (Objects.equals(firstMonth, "H")) {
      startDate = "03/01/" + (Integer.parseInt(year) - 1);
      endDate = "02/29/" + year;
    }

    if (Objects.equals(firstMonth, "J")) {
      startDate = "04/01/" + (Integer.parseInt(year) - 1);
      endDate = "03/31/" + year;
    }

    if (Objects.equals(firstMonth, "K")) {
      startDate = "05/01/" + (Integer.parseInt(year) - 1);
      endDate = "04/30/" + year;
    }

    if (Objects.equals(firstMonth, "M")) {
      startDate = "05/01/" + (Integer.parseInt(year) - 1);
      endDate = "04/30/" + year;
    }







  }
}
