package Handlers;

import static Noms.MainLine.processMainLineDates;

import Noms.MainLine;
import Noms.MainLine.ClerkHolidayService;
import Noms.StubLineNoms;
import Noms.StubLineNoms.NomsData;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import spark.Request;
import spark.Response;
import spark.Route;

public class StubNomHandler implements Route {
  public Object handle(Request request, Response response) throws Exception {
    String userID = request.queryParams("userid");
    if (userID == null) {
      throw new IOException("You must input a userID");
    }

    ClerkHolidayService service = new ClerkHolidayService("sk_test_1qlrksRhhWCq5JoqgdF5oCOMdl3paX4vn6D4EAGhkf");
    Set<LocalDate> holidays = service.getUserHolidays(userID);
    MainLine.HOLIDAYS = holidays;
    NomsData output = StubLineNoms.packageData();

    Moshi moshi = new Moshi.Builder()
        .add(LocalDate.class, new LocalDateAdapter())
        .build();

    JsonAdapter<NomsData> adapter = moshi.adapter(NomsData.class);
    return adapter.toJson(output);
  }

  // Adapter for LocalDate serialization
  private static class LocalDateAdapter extends com.squareup.moshi.JsonAdapter<LocalDate> {
    private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public LocalDate fromJson(com.squareup.moshi.JsonReader reader) throws java.io.IOException {
      return LocalDate.parse(reader.nextString(), formatter);
    }

    @Override
    public void toJson(com.squareup.moshi.JsonWriter writer, LocalDate value) throws java.io.IOException {
      if (value == null) {
        writer.nullValue();
      } else {
        writer.value(formatter.format(value));
      }
    }
  }
}