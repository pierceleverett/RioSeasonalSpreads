package Parser;

import java.util.ArrayList;
import java.util.List;

/**
 * FactoryFailureException is a custom exception that is thrown when the factory fails to create an
 * object. It contains the row that caused the failure.
 */
public class FactoryFailureException extends Exception {
  final List<String> row;

  /**
   * Constructor for FactoryFailureException.
   *
   * @param message
   * @param row
   */
  public FactoryFailureException(String message, List<String> row) {
    super(message);
    this.row = new ArrayList<>(row);
  }

  /**
   * Getter for the row that caused the failure.
   *
   * @return The row that caused the failure.
   */
  public List<String> getRow() {
    return row;
  }
}
