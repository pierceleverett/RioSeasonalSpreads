package Utilities;

import Noms.MainLine.MainLineData;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MainLineDataJsonAdapter extends JsonAdapter<Map<String, MainLineData>> {
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd");
  private final JsonAdapter<Map<String, Map<String, Map<String, String>>>> nestedMapAdapter;
  private final JsonAdapter<String> stringAdapter;

  public MainLineDataJsonAdapter(Moshi moshi) {
    Type nestedMapType = Types.newParameterizedType(
        Map.class,
        String.class,
        Types.newParameterizedType(
            Map.class,
            String.class,
            Types.newParameterizedType(Map.class, String.class, String.class)
        )
    );
    this.nestedMapAdapter = moshi.adapter(nestedMapType);
    this.stringAdapter = moshi.adapter(String.class);
  }

  @Override
  public Map<String, MainLineData> fromJson(JsonReader reader) throws IOException {
    throw new UnsupportedOperationException("Deserialization not implemented");
  }

  @Override
  public void toJson(JsonWriter writer, Map<String, MainLineData> value) throws IOException {
    if (value == null) {
      writer.nullValue();
      return;
    }

    writer.beginObject();

    for (Map.Entry<String, MainLineData> entry : value.entrySet()) {
      writer.name(entry.getKey());
      writeMainLineData(writer, entry.getValue());
    }

    writer.endObject();
  }

  private void writeMainLineData(JsonWriter writer, MainLineData value) throws IOException {
    if (value == null) {
      writer.nullValue();
      return;
    }

    writer.beginObject();

    // Write report date
    if (value.reportDate != null) {
      writer.name("reportDate");
      stringAdapter.toJson(writer, DATE_FORMATTER.format(value.reportDate));
    }

    // Transform and write the data structure
    Map<String, Map<String, Map<String, String>>> transformedData = transformDataStructure(value.data);

    for (Map.Entry<String, Map<String, Map<String, String>>> cycleEntry : transformedData.entrySet()) {
      writer.name(cycleEntry.getKey());
      writer.beginObject();

      for (Map.Entry<String, Map<String, String>> productEntry : cycleEntry.getValue().entrySet()) {
        writer.name(productEntry.getKey());
        writer.beginObject();

        for (Map.Entry<String, String> destinationEntry : productEntry.getValue().entrySet()) {
          writer.name(destinationEntry.getKey());
          writer.value(destinationEntry.getValue());
        }

        writer.endObject();
      }

      writer.endObject();
    }

    writer.endObject();
  }

  private Map<String, Map<String, Map<String, String>>> transformDataStructure(
      Map<String, Map<String, String>> originalData) {

    Map<String, Map<String, Map<String, String>>> transformed = new HashMap<>();

    for (Map.Entry<String, Map<String, String>> entry : originalData.entrySet()) {
      String combinedKey = entry.getKey();
      String[] parts = combinedKey.split("-");

      if (parts.length != 2) {
        continue; // Skip malformed keys
      }

      String cycle = parts[1];
      String product = parts[0];
      Map<String, String> destinations = entry.getValue();

      transformed.computeIfAbsent(cycle, k -> new HashMap<>())
          .put(product, destinations);
    }

    return transformed;
  }

  public static class Factory implements JsonAdapter.Factory {
    @Override
    public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
      if (type.getTypeName().equals(Types.newParameterizedType(
          Map.class, String.class, MainLineData.class).getTypeName())) {
        return new MainLineDataJsonAdapter(moshi);
      }
      return null;
    }
  }
}