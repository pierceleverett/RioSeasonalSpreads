package Utilities;
import Colonial.MostRecentFungible.FungibleData;
import com.squareup.moshi.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FungibleDataJsonAdapter {
  // Static initialization avoids the circular dependency
  private static final Moshi MOSHI = new Moshi.Builder()
      .add(new FungibleDataAdapter())
      .build();

  public static JsonAdapter<FungibleData> adapter() {
    return MOSHI.adapter(FungibleData.class);
  }

  static class FungibleDataAdapter {
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("MM/dd/yyyy");

    @ToJson
    public Map<String, Object> toJson(FungibleData data) {
      Map<String, Object> jsonMap = new LinkedHashMap<>();
      jsonMap.put("report_date", data.reportDate.format(DATE_FORMATTER));
      jsonMap.put("data", data.data);
      return jsonMap;
    }

    @FromJson
    public FungibleData fromJson(Map<String, Object> jsonMap) {
      FungibleData data = new FungibleData();
      data.reportDate = LocalDate.parse(
          (String) jsonMap.get("report_date"),
          DATE_FORMATTER
      );

      @SuppressWarnings("unchecked")
      Map<String, Map<String, Map<String, String>>> nestedData =
          (Map<String, Map<String, Map<String, String>>>) jsonMap.get("data");
      data.data = nestedData;

      return data;
    }
  }
}