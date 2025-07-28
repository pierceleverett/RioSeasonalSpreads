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

public class MainLineDataJsonAdapter extends JsonAdapter<MainLineData> {
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd");
  private final JsonAdapter<Map<String, Map<String, String>>> mapAdapter;

  public MainLineDataJsonAdapter(Moshi moshi) {
    Type mapType = Types.newParameterizedType(
        Map.class,
        String.class,
        Types.newParameterizedType(Map.class, String.class, String.class)
    );
    this.mapAdapter = moshi.adapter(mapType);
  }

  @Override
  public MainLineData fromJson(JsonReader reader) throws IOException {
    // Not needed for your use case since you're only serializing to JSON
    throw new UnsupportedOperationException("Deserialization not implemented");
  }

  @Override
  public void toJson(JsonWriter writer, MainLineData value) throws IOException {
    if (value == null) {
      writer.nullValue();
      return;
    }

    // Transform the data structure
    Map<String, Map<String, Map<String, String>>> transformedData = transformDataStructure(value.data);

    writer.beginObject();

    // Write the transformed data structure
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
      if (type.getTypeName().equals(MainLineData.class.getName())) {
        return new MainLineDataJsonAdapter(moshi);
      }
      return null;
    }
  }
}