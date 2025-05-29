package Parser;

import java.util.List;

/**
 * @param <T> The type of object to create from a row of strings.
 * @throws FactoryFailureException when the factory fails to create a new row
 * @return a new object of type T created from the row of strings
 */
public interface CreatorFromRow<T> {
  T create(List<String> row) throws FactoryFailureException;
}
