package mtas.codec.util;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Class Status.
 */
public class Status {

  /** The number segments finished. */
  public AtomicInteger numberSegmentsFinished = null;
  
  /** The number segments total. */
  public AtomicInteger numberSegmentsTotal = null;

  /** The sub number segments total. */
  public AtomicInteger subNumberSegmentsTotal = null;
  
  /** The sub number segments finished total. */
  public volatile AtomicInteger subNumberSegmentsFinishedTotal = null;
  
  /** The sub number segments finished. */
  public Map<String, Integer> subNumberSegmentsFinished = new ConcurrentHashMap<>();

  /** The number documents found. */
  public AtomicLong numberDocumentsFound = null;
  
  /** The number documents finished. */
  public AtomicLong numberDocumentsFinished = null;
  
  /** The number documents total. */
  public AtomicLong numberDocumentsTotal = null;

  /** The sub number documents total. */
  public AtomicLong subNumberDocumentsTotal = null;
  
  /** The sub number documents finished total. */
  public AtomicLong subNumberDocumentsFinishedTotal = null;
  
  /** The sub number documents finished. */
  public Map<String, Long> subNumberDocumentsFinished = new ConcurrentHashMap<>();

  /**
   * Inits the.
   *
   * @param numberOfDocuments the number of documents
   * @param numberOfSegments the number of segments
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public void init(long numberOfDocuments, int numberOfSegments) throws IOException {
    if (numberDocumentsTotal == null) {
      numberDocumentsTotal = new AtomicLong(numberOfDocuments);
    }
    if (numberSegmentsTotal == null) {
      numberSegmentsTotal = new AtomicInteger(numberOfSegments);
    }
    numberDocumentsFinished = (numberDocumentsFinished == null) ? new AtomicLong() : numberDocumentsFinished;
    if (numberSegmentsFinished == null) {
      numberSegmentsFinished = new AtomicInteger(0);
    }
    subNumberDocumentsTotal = new AtomicLong(numberDocumentsTotal.get() * subNumberDocumentsFinished.size());
    subNumberDocumentsFinishedTotal = (subNumberDocumentsFinishedTotal == null) ? new AtomicLong()
        : subNumberDocumentsFinishedTotal;
    subNumberSegmentsTotal = new AtomicInteger(numberOfSegments * subNumberSegmentsFinished.size());
    if (subNumberSegmentsFinishedTotal == null) {
      subNumberSegmentsFinishedTotal = new AtomicInteger();
    }
  }

  /**
   * Adds the subs.
   *
   * @param subItems the sub items
   */
  public void addSubs(Set<String> subItems) {
    for (String subItem : subItems) {
      addSub(subItem);
    }
  }

  /**
   * Adds the sub.
   *
   * @param subItem the sub item
   */
  public void addSub(String subItem) {
    if (!subNumberSegmentsFinished.containsKey(subItem)) {
      subNumberSegmentsFinished.put(subItem, 0);
      if (numberSegmentsTotal != null) {
        subNumberSegmentsTotal.addAndGet(numberSegmentsTotal.get());
      }
    }
    if (!subNumberDocumentsFinished.containsKey(subItem)) {
      subNumberDocumentsFinished.put(subItem, 0L);
      if (numberDocumentsTotal != null) {
        subNumberDocumentsTotal.addAndGet(numberDocumentsTotal.get());
      }
    }
  }

}
