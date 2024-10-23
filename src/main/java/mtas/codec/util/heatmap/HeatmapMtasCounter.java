package mtas.codec.util.heatmap;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.spatial.prefix.PrefixTreeStrategy;
import org.apache.lucene.spatial.prefix.tree.Cell;
import org.apache.lucene.spatial.prefix.tree.CellIterator;
import org.apache.lucene.spatial.prefix.tree.SpatialPrefixTree;
import org.apache.lucene.util.ArrayUtil;
import org.locationtech.spatial4j.context.SpatialContext;
import org.locationtech.spatial4j.shape.Point;
import org.locationtech.spatial4j.shape.Rectangle;
import org.locationtech.spatial4j.shape.SpatialRelation;

import mtas.codec.util.CodecUtil;
import mtas.codec.util.CodecComponent.ComponentHeatmap;
import mtas.codec.util.CodecComponent.SubComponentFunction;
import mtas.codec.util.collector.MtasDataCollector;

/**
 * Based on org.apache.lucene.spatial.prefix.HeatmapFacetCounter
 */
public class HeatmapMtasCounter {

  /** Maximum number of supported rows (or columns). */
  public static final int MAX_ROWS_OR_COLUMNS = (int) Math.sqrt(ArrayUtil.MAX_ARRAY_LENGTH);
  // static {
  // Math.multiplyExact(MAX_ROWS_OR_COLUMNS, MAX_ROWS_OR_COLUMNS);// will throw if
  // doesn't stay within integer
  // }

  /**
   * Response structure.
   */
  public static class Heatmap {

    /** The data collector. */
    public MtasDataCollector<?, ?> dataCollector;

    /** The functions. */
    public List<SubComponentFunction> functions;

    /** The columns. */
    public final int columns;

    /** The rows. */
    public final int rows;

    /** The region. */
    public final Rectangle region;

    /** The min X. */
    public final double minX;

    /** The max X. */
    public final double maxX;

    /** The min Y. */
    public final double minY;

    /** The max Y. */
    public final double maxY;

    /** The cell width. */
    public final double cellWidth;

    /** The cell height. */
    public final double cellHeight;

    /** The grid level. */
    public final int gridLevel;

    /**
     * Instantiates a new heatmap.
     *
     * @param columns
     *          the columns
     * @param rows
     *          the rows
     * @param region
     *          the region
     * @param cellWidth
     *          the cell width
     * @param cellHeight
     *          the cell height
     * @param gridLevel
     *          the grid level
     * @throws IOException
     *           Signals that an I/O exception has occurred.
     */
    public Heatmap(int columns, int rows, Rectangle region, double cellWidth, double cellHeight, int gridLevel)
        throws IOException {
      this.columns = columns;
      this.rows = rows;
      this.region = region;
      this.minX = region.getMinX();
      this.maxX = region.getMaxX();
      this.minY = region.getMinY();
      this.maxY = region.getMaxY();
      this.cellWidth = cellWidth;
      this.cellHeight = cellHeight;
      this.dataCollector = null;
      this.functions = null;
      this.gridLevel = gridLevel;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
      return "Heatmap{" + columns + "x" + rows + " " + region + '}';
    }

    /**
     * Function need arguments.
     *
     * @return the sets the
     */
    public Set<Integer> functionNeedArguments() {
      Set<Integer> list = new HashSet<>();
      if (functions != null) {
        for (SubComponentFunction function : functions) {
          list.addAll(function.parserFunction.needArgument());
        }
      }
      return list;
    }

    /**
     * Function need positions.
     *
     * @return true, if successful
     */
    public boolean functionNeedPositions() {
      if (functions != null) {
        for (SubComponentFunction function : functions) {
          if (function.parserFunction.needPositions()) {
            return true;
          }
        }
      }
      return false;
    }
  }

  /**
   * Calc values.
   *
   * @param strategy
   *          the strategy
   * @param context
   *          the context
   * @param heatmap
   *          the heatmap
   * @param number
   *          the number
   * @param docSet
   *          the doc set
   * @param values
   *          the values
   * @param args
   *          the args
   * @param positions
   *          the positions
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  public static void calcValues(PrefixTreeStrategy strategy, LeafReaderContext context, ComponentHeatmap heatmap,
      int number, int[] docSet, long[] values, long[][] args, int[] positions) throws IOException {
    if (heatmap.maxCells > (MAX_ROWS_OR_COLUMNS * MAX_ROWS_OR_COLUMNS)) {
      throw new IllegalArgumentException("maxCells (" + heatmap.maxCells + ") should be <= " + MAX_ROWS_OR_COLUMNS);
    }
    if (heatmap.boundsShape == null) {
      heatmap.boundsShape = strategy.getSpatialContext().getWorldBounds();
    }

    final int rows = heatmap.hm.rows;
    final int columns = heatmap.hm.columns;
    double heatMinX = heatmap.hm.minX;
    double heatMaxX = heatmap.hm.maxX;
    double heatMinY = heatmap.hm.minY;
    double heatMaxY = heatmap.hm.maxY;
    final double cellWidth = heatmap.hm.cellWidth;
    final double cellHeight = heatmap.hm.cellHeight;

    if (docSet.length == 0) {
      return; // short-circuit
    }

    // All ancestor cell counts (of gridLevel) will be captured during grid
    // visiting and applied later. If the data is
    // just points then there won't be any ancestors.
    // grid count of ancestors covering all of the heatmap:
    CellValues allCellsAncestorsValues = new CellValues(new long[0], new long[heatmap.hm.functions.size()][],
        new double[heatmap.hm.functions.size()][], new Map[heatmap.hm.functions.size()]);

    // All other ancestors:
    Map<Rectangle, CellValues> ancestorsValues = new HashMap<>();

    // Now lets count!
    PrefixTreeMtasCounter.compute(strategy, context, number, docSet, values, args, positions, heatmap,
        new PrefixTreeMtasCounter.GridVisitor() {

          @Override
          public void visit(Cell cell, CellValues cellValues) {
            final double heatMinX = heatmap.hm.region.getMinX();
            final Rectangle rect = (Rectangle) cell.getShape();
            if (cell.getLevel() == heatmap.gridLevel) {// heatmap level; count it directly
              // convert to col & row
              int column;
              if (rect.getMinX() >= heatMinX) {
                column = (int) Math.round((rect.getMinX() - heatMinX) / cellWidth);
              } else { // due to dateline wrap
                column = (int) Math.round((rect.getMinX() + 360 - heatMinX) / cellWidth);
              }
              int row = (int) Math.round((rect.getMinY() - heatMinY) / cellHeight);
              // note: unfortunately, it's possible for us to visit adjacent cells to the
              // heatmap (if the SpatialPrefixTree
              // allows adjacent cells to overlap on the seam), so we need to skip them
              if (column < 0 || column >= heatmap.hm.columns || row < 0 || row >= heatmap.hm.rows) {
                return;
              }
              // increment
              String key = String.valueOf(column * heatmap.hm.rows + row);
              try {
                heatmap.hm.dataCollector.add(key, cellValues.values(), cellValues.valuesLength());
                for (int i = 0; i < heatmap.hm.functions.size(); i++) {
                  SubComponentFunction f = heatmap.hm.functions.get(i);
                  if (f.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
                    heatmap.hm.functions.get(i).dataCollector.add(key, cellValues.functionValuesLong(i),
                        cellValues.functionValuesLongLength(i));
                  } else if (f.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
                    heatmap.hm.functions.get(i).dataCollector.add(key, cellValues.functionValuesDouble(i),
                        cellValues.functionValuesDoubleLength(i));
                  } else {
                    // should not happen
                    throw new IOException("unexpected dataType " + f.dataType);
                  }
                  if (!cellValues.functionValuesError(i).isEmpty()) {
                    for (Entry<String, Integer> entry : cellValues.functionValuesError(i).entrySet()) {
                      heatmap.hm.functions.get(i).dataCollector.error(key, entry.getKey(), entry.getValue());
                    }
                  }
                }
              } catch (IOException e) {
                // should not happen
                e.printStackTrace();
              }
            } else if (rect.relate(heatmap.hm.region) == SpatialRelation.CONTAINS) {// containing ancestor
              allCellsAncestorsValues.merge(cellValues);
            } else { // ancestor
              // note: not particularly efficient (possible put twice, and Integer wrapper);
              // oh well
              CellValues existingValues = (CellValues) ancestorsValues.put(rect, cellValues);
              if (existingValues != null) {
                cellValues.merge(existingValues);
              }
            }
          }

        });

    // Update the heatmap counts with ancestor counts

    // Apply allCellsAncestorCount
    if (allCellsAncestorsValues.valuesLength() > 0) {
      int n = heatmap.hm.columns * heatmap.hm.rows;
      for (int k = 0; k < n; k++) {
        try {
          String key = String.valueOf(k);
          heatmap.hm.dataCollector.add(key, allCellsAncestorsValues.values(), allCellsAncestorsValues.valuesLength());
          for (int i = 0; i < heatmap.hm.functions.size(); i++) {
            SubComponentFunction f = heatmap.hm.functions.get(i);
            if (f.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
              heatmap.hm.functions.get(i).dataCollector.add(key, allCellsAncestorsValues.functionValuesLong(i),
                  allCellsAncestorsValues.functionValuesLongLength(i));
            } else if (f.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
              heatmap.hm.functions.get(i).dataCollector.add(key, allCellsAncestorsValues.functionValuesDouble(i),
                  allCellsAncestorsValues.functionValuesDoubleLength(i));
            } else {
              // should not happen
              throw new IOException("unexpected dataType " + f.dataType);
            }
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    int[] pair = new int[2];// output of intersectInterval
    for (Map.Entry<Rectangle, CellValues> entry : ancestorsValues.entrySet()) {
      Rectangle rect = entry.getKey(); // from a cell (thus doesn't cross DL)
      final CellValues ancestorValuesEntry = entry.getValue();

      // note: we approach this in a way that eliminates int overflow/underflow (think
      // huge cell, tiny heatmap)
      intersectInterval(heatMinY, heatMaxY, cellHeight, rows, rect.getMinY(), rect.getMaxY(), pair);
      final int startRow = pair[0];
      final int endRow = pair[1];

      if (!heatmap.hm.region.getCrossesDateLine()) {
        intersectInterval(heatMinX, heatMaxX, cellWidth, columns, rect.getMinX(), rect.getMaxX(), pair);
        final int startCol = pair[0];
        final int endCol = pair[1];
        incrementRange(heatmap.hm, startCol, endCol, startRow, endRow, ancestorValuesEntry);

      } else {
        // note: the cell rect might intersect 2 disjoint parts of the heatmap, so we do
        // the left & right separately
        final int leftColumns = (int) Math.round((180 - heatMinX) / cellWidth);
        final int rightColumns = heatmap.hm.columns - leftColumns;
        // left half of dateline:
        if (rect.getMaxX() > heatMinX) {
          intersectInterval(heatMinX, 180, cellWidth, leftColumns, rect.getMinX(), rect.getMaxX(), pair);
          final int startCol = pair[0];
          final int endCol = pair[1];
          incrementRange(heatmap.hm, startCol, endCol, startRow, endRow, ancestorValuesEntry);
        }
        // right half of dateline
        if (rect.getMinX() < heatMaxX) {
          intersectInterval(-180, heatMaxX, cellWidth, rightColumns, rect.getMinX(), rect.getMaxX(), pair);
          final int startCol = pair[0] + leftColumns;
          final int endCol = pair[1] + leftColumns;
          incrementRange(heatmap.hm, startCol, endCol, startRow, endRow, ancestorValuesEntry);
        }
      }
    }
  }

  /**
   * Creates the heatmap.
   *
   * @param heatmap
   *          the heatmap
   * @throws IOException
   *           Signals that an I/O exception has occurred.
   */
  public static void createHeatmap(ComponentHeatmap heatmap) throws IOException {
    final Rectangle inputRect = heatmap.boundsShape.getBoundingBox();
    // First get the rect of the cell at the bottom-left at depth gridLevel
    final SpatialPrefixTree grid = heatmap.strategy.getGrid();
    final SpatialContext ctx = grid.getSpatialContext();
    final Point cornerPt = ctx.makePoint(inputRect.getMinX(), inputRect.getMinY());
    final CellIterator cellIterator = grid.getTreeCellIterator(cornerPt, heatmap.gridLevel);
    Cell cornerCell = null;
    while (cellIterator.hasNext()) {
      cornerCell = cellIterator.next();
    }
//    assert cornerCell != null && cornerCell.getLevel() == heatmap.gridLevel : "Cell not at target level: " + cornerCell;
    final Rectangle cornerRect = (Rectangle) cornerCell.getShape();
//    assert cornerRect.hasArea();
    // Now calculate the number of columns and rows necessary to cover the inputRect
    double heatMinX = cornerRect.getMinX();// note: we might change this below...
    final double cellWidth = cornerRect.getWidth();
    final Rectangle worldRect = ctx.getWorldBounds();
    final int columns = calcRowsOrCols(cellWidth, heatMinX, inputRect.getWidth(), inputRect.getMinX(),
        worldRect.getWidth());
    double heatMinY = cornerRect.getMinY();
    final double cellHeight = cornerRect.getHeight();
    final int rows = calcRowsOrCols(cellHeight, heatMinY, inputRect.getHeight(), inputRect.getMinY(),
        worldRect.getHeight());
//    assert rows > 0 && columns > 0;
    if (columns > MAX_ROWS_OR_COLUMNS || rows > MAX_ROWS_OR_COLUMNS || columns * rows > heatmap.maxCells) {
      throw new IllegalArgumentException(
          "Too many cells (" + columns + " x " + rows + ") for level " + heatmap.gridLevel + " shape " + inputRect);
    }

    // Create resulting heatmap bounding rectangle & Heatmap object.
    final double halfCellWidth = cellWidth / 2.0;
    // if X world-wraps, use world bounds' range
    if (columns * cellWidth + halfCellWidth > worldRect.getWidth()) {
      heatMinX = worldRect.getMinX();
    }
    double heatMaxX = heatMinX + columns * cellWidth;
    if (Math.abs(heatMaxX - worldRect.getMaxX()) < halfCellWidth) {// numeric conditioning issue
      heatMaxX = worldRect.getMaxX();
    } else if (heatMaxX > worldRect.getMaxX()) {// wraps dateline (won't happen if !geo)
      heatMaxX = heatMaxX - worldRect.getMaxX() + worldRect.getMinX();
    }
    final double halfCellHeight = cellHeight / 2.0;
    double heatMaxY = heatMinY + rows * cellHeight;
    if (Math.abs(heatMaxY - worldRect.getMaxY()) < halfCellHeight) {// numeric conditioning issue
      heatMaxY = worldRect.getMaxY();
    }
    heatmap.hm = new Heatmap(columns, rows, ctx.makeRectangle(heatMinX, heatMaxX, heatMinY, heatMaxY), cellWidth,
        cellHeight, heatmap.gridLevel);
  }

  /**
   * Intersect interval.
   *
   * @param heatMin
   *          the heat min
   * @param heatMax
   *          the heat max
   * @param heatCellLen
   *          the heat cell len
   * @param numCells
   *          the num cells
   * @param cellMin
   *          the cell min
   * @param cellMax
   *          the cell max
   * @param out
   *          the out
   */
  private static void intersectInterval(double heatMin, double heatMax, double heatCellLen, int numCells,
      double cellMin, double cellMax, int[] out) {
//    assert heatMin < heatMax && cellMin < cellMax;
    // precondition: we know there's an intersection
    if (heatMin >= cellMin) {
      out[0] = 0;
    } else {
      out[0] = (int) Math.round((cellMin - heatMin) / heatCellLen);
    }
    if (heatMax <= cellMax) {
      out[1] = numCells - 1;
    } else {
      out[1] = (int) Math.round((cellMax - heatMin) / heatCellLen) - 1;
    }
  }

  /**
   * Increment range.
   *
   * @param heatmap
   *          the heatmap
   * @param startColumn
   *          the start column
   * @param endColumn
   *          the end column
   * @param startRow
   *          the start row
   * @param endRow
   *          the end row
   * @param cellValues
   *          the cell values
   */
  private static void incrementRange(Heatmap heatmap, int startColumn, int endColumn, int startRow, int endRow,
      CellValues cellValues) {
    // startColumn & startRow are not necessarily within the heatmap range; likewise
    // numRows/columns may overlap.
    if (startColumn < 0) {
      endColumn += startColumn;
      startColumn = 0;
    }
    endColumn = Math.min(heatmap.columns - 1, endColumn);

    if (startRow < 0) {
      endRow += startRow;
      startRow = 0;
    }
    endRow = Math.min(heatmap.rows - 1, endRow);

    if (startRow > endRow) {
      return;// short-circuit
    }
    for (int c = startColumn; c <= endColumn; c++) {
      int cBase = c * heatmap.rows;
      for (int r = startRow; r <= endRow; r++) {
        try {
          String key = String.valueOf(cBase + r);
          heatmap.dataCollector.add(key, cellValues.values(), cellValues.valuesLength());
          for (int i = 0; i < heatmap.functions.size(); i++) {
            SubComponentFunction f = heatmap.functions.get(i);
            if (f.dataType.equals(CodecUtil.DATA_TYPE_LONG)) {
              heatmap.functions.get(i).dataCollector.add(key, cellValues.functionValuesLong(i),
                  cellValues.functionValuesLongLength(i));
            } else if (f.dataType.equals(CodecUtil.DATA_TYPE_DOUBLE)) {
              heatmap.functions.get(i).dataCollector.add(key, cellValues.functionValuesDouble(i),
                  cellValues.functionValuesDoubleLength(i));
            } else {
              // should not happen
              throw new IOException("unexpected dataType " + f.dataType);
            }
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * Computes the number of intervals (rows or columns) to cover a range given the
   * sizes.
   *
   * @param cellRange
   *          the cell range
   * @param cellMin
   *          the cell min
   * @param requestRange
   *          the request range
   * @param requestMin
   *          the request min
   * @param worldRange
   *          the world range
   * @return the int
   */
  private static int calcRowsOrCols(double cellRange, double cellMin, double requestRange, double requestMin,
      double worldRange) {
//    assert requestMin >= cellMin;
    // Idealistically this wouldn't be so complicated but we concern ourselves with
    // overflow and edge cases
    double range = (requestRange + (requestMin - cellMin));
    if (range == 0) {
      return 1;
    }
    final double intervals = Math.ceil(range / cellRange);
    if (intervals > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;// should result in an error soon (exceed thresholds)
    }
    // ensures we don't have more intervals than world bounds (possibly due to
    // rounding/edge issue)
    final long intervalsMax = Math.round(worldRange / cellRange);
    if (intervalsMax > Integer.MAX_VALUE) {
      // just return intervals
      return (int) intervals;
    }
    return Math.min((int) intervalsMax, (int) intervals);
  }

  /**
   * Instantiates a new heatmap mtas counter.
   */
  private HeatmapMtasCounter() {
  }
}
