package Parser;

import java.util.List;

/**
 * A trivial creator that creates a list of strings from a list of strings.
 *
 * @return the list of strings
 */
public class TrivialCreator implements CreatorFromRow<List<String>> {
  @Override
  public List<String> create(List<String> row) {
    return row;
  }
}
