package Parser;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @param <T>
 */
public class Parser<T> {
  /** TODO is this defensive enough? Feel free to edit any variable declarations */
  private final Reader reader;

  private final List<T> parsedContent;
  private final CreatorFromRow<T> creator;
  private final boolean skipHeader;

  /**
   * TODO feel free to modify the header and body of this constructor however you wish
   *
   * @param filePath - path to a csv file to be parsed - creator - a factory that creates a new row
   *     object from a list of strings - skipHeader - whether to skip the first line of the csv file
   *     - throws IOException when buffer reader fails to read-in a line
   */
  public Parser(String filePath, CreatorFromRow<T> creator, boolean skipHeader) throws IOException {
    this.reader = new FileReader(filePath);
    this.parsedContent = new ArrayList<>();
    this.creator = creator;
    this.skipHeader = skipHeader;
  }

  /**
   * @param reader
   * @param creator
   * @param skipHeader
   */
  public Parser(Reader reader, CreatorFromRow<T> creator, boolean skipHeader) {
    this.reader = reader;
    this.parsedContent = new ArrayList<>();
    this.creator = creator;
    this.skipHeader = skipHeader;
  }

  /**
   * TODO feel free to modify this method to incorporate your design choices
   *
   * @throws IOException when buffer reader fails to read-in a line
   * @throws IOException when file is empty
   * @throws IOException when factory fails to create a new row
   */
  public void parse() throws IOException {
    String line;
    Pattern regexSplitCSVRow = Pattern.compile(",(?=([^\\\"]*\\\"[^\\\"]*\\\")*(?![^\\\"]*\\\"))");

    try (BufferedReader readInBuffer = new BufferedReader(reader)) {
      ; // wraps around readers to improve efficiency when reading
      boolean isEmpty = true;
      boolean firstLine = true;

      while ((line = readInBuffer.readLine()) != null) {
        if (line.isEmpty()) continue;
        if (firstLine && skipHeader) {
          firstLine = false;
          continue;
        }
        firstLine = false;

        isEmpty = false;
        String[] result = regexSplitCSVRow.split(line);
        try {
          List<String> lineToArr = Arrays.stream(result).toList();
          T newRow = creator.create(lineToArr);
          parsedContent.add(newRow);
        } catch (FactoryFailureException e) {
          throw new IOException("Factory failed to create a new row");
        }
      }
      if (isEmpty) {
        throw new IOException("File is empty");
      }
    } catch (IOException e) {
      throw new IOException("Buffer reader failed to read-in a line");
    }
  }

  /**
   * @return parsedContent
   */
  public List<T> getParsedContent() {
    return parsedContent;
  }

  /**
   * @param b
   */
  public void setSkipFirstLine(boolean b) {}
}
