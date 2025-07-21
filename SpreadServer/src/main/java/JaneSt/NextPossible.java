//package JaneSt;
//
//import java.util.HashMap;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Arrays;
//
//public class NextPossible {
//  // Declare letterMap at the class level (not inside Square)
//  private HashMap<Double, String> letterMap = new HashMap<>();
//  {
//    letterMap.put(1.1, "A");
//    letterMap.put(1.2, "A");
//    letterMap.put(1.3, "A");
//    letterMap.put(1.4, "A");
//    letterMap.put(1.5, "A");
//    letterMap.put(1.6, "A");
//    letterMap.put(2.1, "A");
//    letterMap.put(2.2, "A");
//    letterMap.put(2.3, "A");
//    letterMap.put(2.4, "A");
//    letterMap.put(3.1, "A");
//    letterMap.put(3.2, "A");
//    letterMap.put(2.5, "B");
//    letterMap.put(2.6, "B");
//    letterMap.put(3.3, "B");
//    letterMap.put(3.4, "B");
//    letterMap.put(3.5, "B");
//    letterMap.put(3.6, "B");
//    letterMap.put(4.1, "B");
//    letterMap.put(4.2, "B");
//    letterMap.put(Arrays.asList(4, 3), "B");
//    letterMap.put(Arrays.asList(4, 4), "B");
//    letterMap.put(Arrays.asList(5, 1), "B");
//    letterMap.put(Arrays.asList(5, 2), "B");
//    letterMap.put(Arrays.asList(4, 5), "C");
//    letterMap.put(Arrays.asList(4, 6), "C");
//    letterMap.put(Arrays.asList(5, 3), "C");
//    letterMap.put(Arrays.asList(5, 4), "C");
//    letterMap.put(Arrays.asList(5, 5), "C");
//    letterMap.put(Arrays.asList(5, 6), "C");
//    letterMap.put(Arrays.asList(6, 1), "C");
//    letterMap.put(Arrays.asList(6, 2), "C");
//    letterMap.put(Arrays.asList(6, 3), "C");
//    letterMap.put(Arrays.asList(6, 4), "C");
//    letterMap.put(Arrays.asList(6, 5), "C");
//    letterMap.put(Arrays.asList(6, 6), "C");
//
//
//  }
//
//  public class Square {
//    public Integer x;
//    public Integer y;
//    public String letter;
//    public Square lastSquare;
//
//    public Square(Integer x, Integer y, String letter) {
//      this.x = x;
//      this.y = y;
//      this.letter = letter;
//      this.lastSquare = null;
//    }
//
//    public LinkedList<Square> nextPossibleSquares() {
//      Integer maxX = 6;
//      Integer maxY = 6;
//      Integer minX = 1;
//      Integer minY = 1;
//      Integer currX = this.x;
//      Integer currY = this.y;
//      Square lastSquare = this.lastSquare;
//      LinkedList<Square> possibleMoves = new LinkedList<>();
//
//      //up and to left
//      if (currX > minX && currY <= 4 ) {
//        Integer newX = currX - 1;
//        Integer newY = currY + 2;
//        Square nextMove = new Square(currX - 1, currY + 2, letterMap.get(1.2));
//      }
//
//      return possibleMoves;
//    }
//
//
//  }
//
//}