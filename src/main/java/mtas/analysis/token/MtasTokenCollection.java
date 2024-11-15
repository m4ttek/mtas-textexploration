package mtas.analysis.token;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import mtas.analysis.util.MtasParserException;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.util.BytesRef;


/**
 * The Class MtasTokenCollection.
 */
public class MtasTokenCollection {

  /** The token collection. */
  private Int2ObjectMap<MtasToken> tokenCollection = new Int2ObjectOpenHashMap<>();

  /** The token collection index. */
  private List<MtasToken> sortedTokenCollection;

  /**
   * Adds the.
   *
   * @param token the token
   * @return the integer
   */
  public int add(MtasToken token) {
    int id = token.getId();
    tokenCollection.put(id, token);
    return id;
  }

  /**
   * Gets the.
   *
   * @param id the id
   * @return the mtas token
   */
  public MtasToken get(int id) {
    return tokenCollection.get(id);
  }

  /**
   * Iterator.
   *
   * @return the iterator
   * @throws MtasParserException the mtas parser exception
   */
  public Iterator<MtasToken> iterator() throws MtasParserException {
    return sortedTokenCollection.iterator();
  }

  /**
   * Prints the.
   *
   * @throws MtasParserException the mtas parser exception
   */
  public void print() throws MtasParserException {
    Iterator<MtasToken> it = this.iterator();
    while (it.hasNext()) {
      MtasToken token = it.next();
      System.out.println(token);
    }
  }

  /**
   * Gets the list.
   *
   * @return the list
   * @throws MtasParserException the mtas parser exception
   */
  public String[][] getList() throws MtasParserException {
    String[][] result = new String[(tokenCollection.size() + 1)][];
    result[0] = new String[] { "id", "start real offset", "end real offset",
        "provide real offset", "start offset", "end offset", "provide offset",
        "start position", "end position", "multiple positions", "parent",
        "provide parent", "payload", "prefix", "postfix" };
    int number = 1;
    Iterator<MtasToken> it = this.iterator();
    while (it.hasNext()) {
      MtasToken token = it.next();
      String[] row = new String[15];
      row[0] = Integer.toString(token.getId());
      if (token.getRealOffsetStart() != -1) {
        row[1] = Integer.toString(token.getRealOffsetStart());
        row[2] = Integer.toString(token.getRealOffsetEnd());
        row[3] = token.getProvideRealOffset() ? "1" : null;
      }
      if (token.getOffsetStart() != -1) {
        row[4] = Integer.toString(token.getOffsetStart());
        row[5] = Integer.toString(token.getOffsetEnd());
        row[6] = token.getProvideOffset() ? "1" : null;
      }
      if (token.getPositionLength() != -1) {
        if (token.getPositionStart() == token.getPositionEnd()) {
          row[7] = Integer.toString(token.getPositionStart());
          row[8] = Integer.toString(token.getPositionEnd());
          row[9] = null;
        } else if ((token.getPositions() == null)
            || (token.getPositions().length == (1 + token.getPositionEnd()
                - token.getPositionStart()))) {
          row[7] = Integer.toString(token.getPositionStart());
          row[8] = Integer.toString(token.getPositionEnd());
          row[9] = null;
        } else {
          row[7] = null;
          row[8] = null;
          row[9] = Arrays.toString(token.getPositions());
        }
      }
      if (token.getParentId() != null) {
        row[10] = token.getParentId().toString();
        row[11] = token.getProvideParentId() ? "1" : null;
      }
      if (token.getPayload() != null) {
        BytesRef payload = token.getPayload();
        row[12] = Float.toString(PayloadHelper.decodeFloat(Arrays.copyOfRange(
            payload.bytes, payload.offset, (payload.offset + payload.length))));
      }
      row[13] = token.getPrefix();
      row[14] = token.getPostfix();
      result[number] = row;
      number++;
    }
    return result;
  }

  /**
   * Check.
   *
   * @param autoRepair the auto repair
   * @param makeUnique the make unique
   * @throws MtasParserException the mtas parser exception
   */
  public void check(Boolean autoRepair, Boolean makeUnique)
      throws MtasParserException {
    if (autoRepair) {
      autoRepair();
    }
    if (makeUnique) {
      makeUnique();
    }
    checkTokenCollectionIndex();
  }

  /**
   * Make unique.
   */
  private void makeUnique() {
    // TODO WHY IT DOESN'T MAKE ANY CHANGE?!!
//    HashMap<String, ArrayList<MtasToken>> currentPositionTokens = new HashMap<>();
//    ArrayList<MtasToken> currentValueTokens;
//    int currentStartPosition = -1;
//    for (Entry<Integer, MtasToken> entry : tokenCollection.entrySet()) {
//      MtasToken currentToken = entry.getValue();
//      if (currentToken.getPositionStart() > currentStartPosition) {
//        currentPositionTokens.clear();
//        currentStartPosition = currentToken.getPositionStart();
//      } else {
//        if (currentPositionTokens.containsKey(currentToken.getValue())) {
//          currentValueTokens = currentPositionTokens.get(currentToken.getValue());
//        } else {
//          currentValueTokens = new ArrayList<>();
//          currentPositionTokens.put(currentToken.getValue(), currentValueTokens);
//        }
//        currentValueTokens.add(currentToken);
//      }
//    }
  }

  /**
   * Auto repair.
   */
  private void autoRepair() {
    IntSet trash = new IntArraySet();
    for (var entry: Int2ObjectMaps.fastIterable(tokenCollection)) {
      boolean putInTrash;
      var token = entry.getValue();
      putInTrash = (token.getPositionStart() == -1) || (token.getPositionEnd() == -1);
      putInTrash |= token.getValue() == null || (token.getValue().isEmpty());
      putInTrash |= token.getPrefix() == null || (token.getPrefix().isEmpty());
      if (putInTrash) {
        trash.add(entry.getIntKey());
      }
    }

    // check parentId and offset
    tokenCollection
            .values()
            .stream()
            .filter(mtasToken -> mtasToken.getParentId() != null
                    && (!tokenCollection.containsKey(mtasToken.getParentId()) || trash.contains(mtasToken.getParentId())))
            .forEach(mtasToken -> mtasToken.setParentId(null));

    // empty bin
    if (!trash.isEmpty()) {
      tokenCollection.keySet().removeAll(trash);
    }
    // always check ids
    if (!tokenCollection.isEmpty()) {
      var stats = tokenCollection.keySet().intStream().summaryStatistics();
      int maxId = stats.getMax();
      int minId = stats.getMin();
      // check
      if ((minId > 0) || ((1 + maxId - minId) != tokenCollection.size())) {
        // create translation
        int newId = 0;
        Int2IntMap translation = new Int2IntArrayMap(tokenCollection.size());
        for (int entry: tokenCollection.keySet()) {
          translation.put(entry, newId);
          newId++;
        }

        // translate objects
        for (var entry: Int2ObjectMaps.fastIterable(tokenCollection)) {
          var token = entry.getValue();
          token.setId(translation.get(entry.getIntKey()));
          Integer parentId = token.getParentId();
          if (parentId != null) {
            token.setParentId(translation.get(parentId));
          }
        }

        // new tokenCollection
        Int2ObjectMap<MtasToken> newTokenCollection = new Int2ObjectOpenHashMap<>(tokenCollection.size());
        for (var entry: Int2ObjectMaps.fastIterable(tokenCollection)) {
          newTokenCollection.put(translation.get(entry.getIntKey()), entry.getValue());
        }
        tokenCollection = newTokenCollection;
        sortedTokenCollection = newTokenCollection.values().stream().sorted(getCompByName()).toList();
      }
    }
  }

  /**
   * Check token collection index.
   *
   * @throws MtasParserException the mtas parser exception
   */
  private void checkTokenCollectionIndex() throws MtasParserException {
      Int2ObjectMaps.fastForEach(tokenCollection, tokenEntry -> {
        var token = tokenEntry.getValue();
        try {
          if ((token.getPositionStart() == -1) || (token.getPositionEnd() == -1)) {
            throw new MtasParserException("no position for token with id " + token.getId() + " (" + token.getValue() + ")");
          } else if (token.getValue() == null || (token.getValue().isEmpty())) {
            throw new MtasParserException("no value for token with id " + token.getId());
          } else if (token.getPrefix() == null || (token.getPrefix().isEmpty())) {
            throw new MtasParserException("no prefix for token with id " + token.getId());
          } else if ((token.getParentId() != null)
              && !tokenCollection.containsKey(token.getParentId())) {
            throw new MtasParserException("missing parentId for token with id " + token.getId());
          } else if ((token.getOffsetStart() == -1) || (token.getOffsetEnd() == -1)) {
            throw new MtasParserException("missing offset for token with id " + token.getId() + " (" + token.getValue() + ")");
          }
        } catch (MtasParserException e) {
          throw new RuntimeException(e);
        }
      });
      var stats = tokenCollection.keySet().intStream().summaryStatistics();
      int maxId = stats.getMax();
      int minId = stats.getMin();
      if ((!tokenCollection.isEmpty())
          && ((minId > 0) || ((1 + maxId - minId) != tokenCollection.size()))) {
        throw new MtasParserException("missing ids");
      }
      this.sortedTokenCollection = tokenCollection.values().stream().sorted(getCompByName()).toList();
  }

  /**
   * Gets the comp by name.
   *
   * @return the comp by name
   */
  public Comparator<MtasToken> getCompByName() {
    return (firstToken, secondToken) -> {
      int p1 = firstToken.getPositionStart();
      int p2 = secondToken.getPositionStart();
      assert p1 != -1 : "no position for " + firstToken;
      assert p2 != -1 : "no position for " + secondToken;
      if (p1 == p2) {
        int o1 = firstToken.getOffsetStart();
        int o2 = secondToken.getOffsetStart();
        if (o1 != -1 && o2 != -1) {
          if (o1 == o2) {
            return firstToken.getValue().compareTo(secondToken.getValue());
          } else {
            return Integer.compare(o1, o2);
          }
        } else {
          return firstToken.getValue().compareTo(secondToken.getValue());
        }
      }
      return Integer.compare(p1, p2);
    };
  }

  /**
   * Clear.
   */
  private void clear() {
    tokenCollection = new Int2ObjectOpenHashMap<>();
    sortedTokenCollection = List.of();
  }

}
