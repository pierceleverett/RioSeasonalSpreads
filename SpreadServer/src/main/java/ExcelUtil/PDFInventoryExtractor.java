package ExcelUtil;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class PDFInventoryExtractor {

  // List of expected product grades in order
  private static final String[] PRODUCT_GRADES = {
      "A PREMIUM UNLEADED GASOLINE",
      "E DENATURED FUEL ETHANOL",
      "Q COMMERCIAL JET FUEL",
      "V SUB OCTANE UNL GASOLINE",
      "X #2 ULSD",
      "Y #1 ULSD FUEL OIL"
  };

  public static void main(String[] args) {
    String pdfDirectory = "src/data/pdf/";
    String outputFile = "src/data/inventory_report.csv";

    Map<String, Map<String, String>> inventoryData = new LinkedHashMap<>();

    File dir = new File(pdfDirectory);
    File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));

    if (files != null) {
      for (File pdfFile : files) {
        try {
          System.out.println("Processing: " + pdfFile.getName());
          processPDF(pdfFile, inventoryData);
        } catch (IOException e) {
          System.err.println("Error processing: " + pdfFile.getName());
          e.printStackTrace();
        }
      }

      writeToCSV(outputFile, inventoryData);
      System.out.println("CSV generated: " + outputFile);
    } else {
      System.out.println("No PDFs found in: " + pdfDirectory);
    }
  }

  private static void processPDF(File pdfFile, Map<String, Map<String, String>> inventoryData) throws IOException {
    try (PDDocument document = PDDocument.load(pdfFile)) {
      PDFTextStripper stripper = new PDFTextStripper();
      String text = stripper.getText(document);

      String date = extractDate(text);
      if (date == null) {
        System.err.println("Date not found in: " + pdfFile.getName());
        return;
      }

      Map<String, String> productInventory = extractInventoryData(text);
      if (!productInventory.isEmpty()) {
        inventoryData.put(date, productInventory);
      } else {
        System.err.println("No inventory data extracted from: " + pdfFile.getName());
      }
    }
  }

  private static String extractDate(String text) {
    // Match "AS OF: MM/DD/YYYY" pattern
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("AS OF:\\s*(\\d{2}/\\d{2}/\\d{4})");
    java.util.regex.Matcher matcher = pattern.matcher(text);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static Map<String, String> extractInventoryData(String text) {
    Map<String, String> inventory = new LinkedHashMap<>();
    String[] lines = text.split("\\r?\\n");

    for (String line : lines) {
      // Check for each known product grade
      for (String grade : PRODUCT_GRADES) {
        if (line.startsWith(grade)) {
          // Extract the SYSTEM INVENTORY value (first number after grade)
          String[] parts = line.substring(grade.length()).trim().split("\\s+");
          if (parts.length > 0) {
            String inventoryValue = parts[0].replace(",", "");
            inventory.put(grade, inventoryValue);
          }
          break;
        }
      }
    }

    return inventory;
  }

  private static void writeToCSV(String outputFile, Map<String, Map<String, String>> inventoryData) {
    try (FileWriter writer = new FileWriter(outputFile)) {
      // Write CSV header
      writer.write("Date");
      for (String grade : PRODUCT_GRADES) {
        writer.write("," + grade);
      }
      writer.write("\n");

      // Write data rows
      for (Map.Entry<String, Map<String, String>> entry : inventoryData.entrySet()) {
        writer.write(entry.getKey());
        for (String grade : PRODUCT_GRADES) {
          String value = entry.getValue().getOrDefault(grade, "");
          writer.write("," + value);
        }
        writer.write("\n");
      }
    } catch (IOException e) {
      System.err.println("Error writing CSV: " + e.getMessage());
    }
  }
}