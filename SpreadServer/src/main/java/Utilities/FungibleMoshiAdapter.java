package Utilities;

import Colonial.MostRecentFungible;
import com.squareup.moshi.*;
import java.io.IOException;
import java.time.LocalDate;

public class FungibleMoshiAdapter {

  private static final Moshi moshi = new Moshi.Builder()
      .add(new LocalDateAdapter())
      .build();

  public static String toJson(MostRecentFungible.FungibleComparisonResult result) throws IOException {
    JsonAdapter<MostRecentFungible.FungibleComparisonResult> adapter =
        moshi.adapter(MostRecentFungible.FungibleComparisonResult.class);
    return adapter.toJson(result);
  }

  public static MostRecentFungible.FungibleComparisonResult fromJson(String json) throws IOException {
    JsonAdapter<MostRecentFungible.FungibleComparisonResult> adapter =
        moshi.adapter(MostRecentFungible.FungibleComparisonResult.class);
    return adapter.fromJson(json);
  }

  // Adapter for LocalDate
  public static class LocalDateAdapter {
    @ToJson
    String toJson(LocalDate date) {
      return date.toString();
    }

    @FromJson
    LocalDate fromJson(String date) {
      return LocalDate.parse(date);
    }
  }
}
