package mtas.codec.util;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mtas.analysis.token.MtasToken;

/**
 * The Class GroupHit.
 */
public class GroupHit {

    private static final Pattern pattern = Pattern.compile("^([^\\.]*)\\.([^\\.]*)$");

    /**
     * The hash.
     */
    private final int hash;

    /**
     * The key.
     */
    private final String key;

    /**
     * The data hit.
     */
    private final List<String>[] dataHit;

    /**
     * The data left.
     */
    private final List<String>[] dataLeft;

    /**
     * The data right.
     */
    private final List<String>[] dataRight;

    /**
     * The missing hit.
     */
    private final Set<String>[] missingHit;

    /**
     * The missing left.
     */
    private final Set<String>[] missingLeft;

    /**
     * The missing right.
     */
    private final Set<String>[] missingRight;

    /**
     * The unknown hit.
     */
    private final Set<String>[] unknownHit;

    /**
     * The unknown left.
     */
    private final Set<String>[] unknownLeft;

    /**
     * The unknown right.
     */
    private final Set<String>[] unknownRight;

    /**
     * The Constant KEY_START.
     */
    public static final String KEY_START = MtasToken.DELIMITER + "grouphit" + MtasToken.DELIMITER;

    public List<String>[] getDataHit() {
        return dataHit;
    }

    public List<String>[] getDataLeft() {
        return dataLeft;
    }

    public List<String>[] getDataRight() {
        return dataRight;
    }

    public Set<String>[] getMissingHit() {
        return missingHit;
    }

    public Set<String>[] getMissingLeft() {
        return missingLeft;
    }

    public Set<String>[] getMissingRight() {
        return missingRight;
    }

    public Set<String>[] getUnknownHit() {
        return unknownHit;
    }

    public Set<String>[] getUnknownLeft() {
        return unknownLeft;
    }

    public Set<String>[] getUnknownRight() {
        return unknownRight;
    }

    /**
     * Sort.
     *
     * @param data the data
     * @return the list
     */
    private List<CodecSearchTree.MtasTreeHit<String>> sort(List<CodecSearchTree.MtasTreeHit<String>> data) {
        data.sort((hit1, hit2) -> {
            int compare = Integer.compare(hit1.additionalId, hit2.additionalId);
            compare = (compare == 0) ? Long.compare(hit1.additionalRef, hit2.additionalRef) : compare;
            return compare;
        });
        return data;
    }

    /**
     * Instantiates a new group hit.
     *
     * @param list          the list
     * @param start         the start
     * @param end           the end
     * @param hitStart      the hit start
     * @param hitEnd        the hit end
     * @param group         the group
     * @param knownPrefixes the known prefixes
     * @throws UnsupportedEncodingException the unsupported encoding exception
     */
    @SuppressWarnings("unchecked")
    public GroupHit(List<CodecSearchTree.MtasTreeHit<String>> list, int start, int end, int hitStart, int hitEnd, ComponentGroup group,
                    Set<String> knownPrefixes) throws UnsupportedEncodingException {
        String key1;
        // compute dimensions
        int leftRangeEnd = Math.min(end, hitStart - 1);
        int leftRangeLength = Math.max(0, 1 + leftRangeEnd - start);
        int hitLength = 1 + hitEnd - hitStart;
        int rightRangeStart = Math.max(start, hitEnd + 1);
        int rightRangeLength = Math.max(0, 1 + end - rightRangeStart);
        // create initial arrays
        /** The key left. */
        String keyLeft;
        if (leftRangeLength > 0) {
            keyLeft = "";
            dataLeft = (ArrayList<String>[]) new ArrayList[leftRangeLength];
            missingLeft = (HashSet<String>[]) new HashSet[leftRangeLength];
            unknownLeft = (HashSet<String>[]) new HashSet[leftRangeLength];
            for (int p = 0; p < leftRangeLength; p++) {
                dataLeft[p] = new ArrayList<>();
                missingLeft[p] = new HashSet<>();
                unknownLeft[p] = new HashSet<>();
            }
        } else {
            dataLeft = null;
            missingLeft = null;
            unknownLeft = null;
        }
        /** The key hit. */
        String keyHit;
        if (hitLength > 0) {
            dataHit = (ArrayList<String>[]) new ArrayList[hitLength];
            missingHit = (HashSet<String>[]) new HashSet[hitLength];
            unknownHit = (HashSet<String>[]) new HashSet[hitLength];
            for (int p = 0; p < hitLength; p++) {
                dataHit[p] = new ArrayList<>();
                missingHit[p] = new HashSet<>();
                unknownHit[p] = new HashSet<>();
            }
        } else {
            keyHit = null;
            dataHit = null;
            missingHit = null;
            unknownHit = null;
        }
        /** The key right. */
        String keyRight;
        if (rightRangeLength > 0) {
            dataRight = (ArrayList<String>[]) new ArrayList[rightRangeLength];
            missingRight = (HashSet<String>[]) new HashSet[rightRangeLength];
            unknownRight = (HashSet<String>[]) new HashSet[rightRangeLength];
            for (int p = 0; p < rightRangeLength; p++) {
                dataRight[p] = new ArrayList<>();
                missingRight[p] = new HashSet<>();
                unknownRight[p] = new HashSet<>();
            }
        } else {
            dataRight = null;
            missingRight = null;
            unknownRight = null;
        }

        // construct missing sets
        if (group.getHitInside() != null) {
            for (int p = hitStart; p <= hitEnd; p++) {
                missingHit[p - hitStart].addAll(group.getHitInside());
            }
        }
        if (group.getHitInsideLeft() != null) {
            for (int p = hitStart; p <= Math.min(hitEnd, hitStart + group.getHitInsideLeft().length - 1); p++) {
                if (group.getHitInsideLeft()[p - hitStart] != null) {
                    missingHit[p - hitStart].addAll(group.getHitInsideLeft()[p - hitStart]);
                }
            }
        }
        if (group.getHitLeft() != null) {
            for (int p = hitStart; p <= Math.min(hitEnd, hitStart + group.getHitLeft().length - 1); p++) {
                if (group.getHitLeft()[p - hitStart] != null) {
                    missingHit[p - hitStart].addAll(group.getHitLeft()[p - hitStart]);
                }
            }
        }
        if (group.getHitInsideRight() != null) {
            for (int p = Math.max(hitStart, hitEnd - group.getHitInsideRight().length + 1); p <= hitEnd; p++) {
                if (group.getHitInsideRight()[hitEnd - p] != null) {
                    missingHit[p - hitStart].addAll(group.getHitInsideRight()[hitEnd - p]);
                }
            }
        }
        if (group.getHitRight() != null) {
            for (int p = hitStart; p <= Math.min(hitEnd, hitStart + group.getHitRight().length - 1); p++) {
                if (group.getHitRight()[p - hitStart] != null) {
                    missingHit[p - hitStart].addAll(group.getHitRight()[p - hitStart]);
                }
            }
        }
        if (group.getLeft() != null) {
            for (int p = 0; p < Math.min(leftRangeLength, group.getLeft().length); p++) {
                if (group.getLeft()[p] != null) {
                    missingLeft[p].addAll(group.getLeft()[p]);
                }
            }
        }
        if (group.getHitRight() != null) {
            for (int p = 0; p < Math.min(leftRangeLength, group.getHitRight().length - dataHit.length); p++) {
                if (group.getHitRight()[p + dataHit.length] != null) {
                    missingLeft[p].addAll(group.getHitRight()[p + dataHit.length]);
                }
            }
        }
        if (group.getRight() != null) {
            for (int p = 0; p < Math.min(rightRangeLength, group.getRight().length); p++) {
                if (group.getRight()[p] != null) {
                    missingRight[p].addAll(group.getRight()[p]);
                }
            }
        }
        if (group.getHitLeft() != null) {
            for (int p = 0; p < Math.min(rightRangeLength, group.getHitLeft().length - dataHit.length); p++) {
                if (group.getHitLeft()[p + dataHit.length] != null) {
                    missingRight[p].addAll(group.getHitLeft()[p + dataHit.length]);
                }
            }
        }

        // fill arrays and update missing administration
        List<CodecSearchTree.MtasTreeHit<String>> sortedList = sort(list);
        for (CodecSearchTree.MtasTreeHit<String> hit : sortedList) {
            // inside hit
            if (group.getHitInside() != null && hit.idData != null && group.getHitInside().contains(hit.idData)) {
                for (int p = Math.max(hitStart, hit.startPosition); p <= Math.min(hitEnd, hit.endPosition); p++) {
                    dataHit[p - hitStart].add(hit.refData);
                    missingHit[p - hitStart].remove(MtasToken.getPrefixFromValue(hit.refData));
                }
            } else if ((group.getHitInsideLeft() != null || group.getHitLeft() != null || group.getHitInsideRight() != null
                    || group.getHitRight() != null) && hit.idData != null) {
                for (int p = Math.max(hitStart, hit.startPosition); p <= Math.min(hitEnd, hit.endPosition); p++) {
                    int pHitLeft = p - hitStart;
                    int pHitRight = hitEnd - p;
                    if (group.getHitInsideLeft() != null && pHitLeft <= (group.getHitInsideLeft().length - 1)
                            && group.getHitInsideLeft()[pHitLeft] != null && group.getHitInsideLeft()[pHitLeft].contains(hit.idData)) {
                        // keyHit += hit.refData;
                        dataHit[p - hitStart].add(hit.refData);
                        missingHit[p - hitStart].remove(MtasToken.getPrefixFromValue(hit.refData));
                    } else if (group.getHitLeft() != null && pHitLeft <= (group.getHitLeft().length - 1)
                            && group.getHitLeft()[pHitLeft] != null && group.getHitLeft()[pHitLeft].contains(hit.idData)) {
                        // keyHit += hit.refData;
                        dataHit[p - hitStart].add(hit.refData);
                        missingHit[p - hitStart].remove(MtasToken.getPrefixFromValue(hit.refData));
                    } else if (group.getHitInsideRight() != null && pHitRight <= (group.getHitInsideRight().length - 1)
                            && group.getHitInsideRight()[pHitRight] != null && group.getHitInsideRight()[pHitRight].contains(hit.idData)) {
                        dataHit[p - hitStart].add(hit.refData);
                        missingHit[p - hitStart].remove(MtasToken.getPrefixFromValue(hit.refData));
                    } else if (group.getHitRight() != null && pHitRight <= (group.getHitRight().length - 1)
                            && group.getHitRight()[pHitRight] != null && group.getHitRight()[pHitRight].contains(hit.idData)) {
                        dataHit[p - hitStart].add(hit.refData);
                        missingHit[p - hitStart].remove(MtasToken.getPrefixFromValue(hit.refData));
                    }
                }
            }
            // left
            if (hit.startPosition < hitStart) {
                if ((group.getLeft() != null || (group.getHitRight() != null && group.getHitRight().length > (1 + hitEnd - hitStart)))
                        && hit.idData != null) {
                    for (int p = Math.min(hit.endPosition, hitStart - 1); p >= hit.startPosition; p--) {
                        int pLeft = hitStart - 1 - p;
                        int pHitRight = hitEnd - p;
                        if (group.getLeft() != null && pLeft <= (group.getLeft().length - 1) && group.getLeft()[pLeft] != null
                                && group.getLeft()[pLeft].contains(hit.idData)) {
                            dataLeft[hitStart - 1 - p].add(hit.refData);
                            missingLeft[hitStart - 1 - p].remove(MtasToken.getPrefixFromValue(hit.refData));
                        } else if (group.getHitRight() != null && pHitRight <= (group.getHitRight().length - 1)
                                && group.getHitRight()[pHitRight] != null && group.getHitRight()[pHitRight].contains(hit.idData)) {
                            dataLeft[hitStart - 1 - p].add(hit.refData);
                            missingLeft[hitStart - 1 - p].remove(MtasToken.getPrefixFromValue(hit.refData));
                        }
                    }
                }
            }
            // right
            if (hit.endPosition > hitEnd) {
                if ((group.getRight() != null || (group.getHitLeft() != null && group.getHitLeft().length > (1 + hitEnd - hitStart)))
                        && hit.idData != null) {
                    for (int p = Math.max(hit.startPosition, hitEnd + 1); p <= hit.endPosition; p++) {
                        int pRight = p - hitEnd - 1;
                        int pHitLeft = p - hitStart;
                        if (group.getRight() != null && pRight <= (group.getRight().length - 1) && group.getRight()[pRight] != null
                                && group.getRight()[pRight].contains(hit.idData)) {
                            dataRight[p - rightRangeStart].add(hit.refData);
                            missingRight[p - rightRangeStart].remove(MtasToken.getPrefixFromValue(hit.refData));
                        } else if (group.getHitLeft() != null && pHitLeft <= (group.getHitLeft().length - 1)
                                && group.getHitLeft()[pHitLeft] != null && group.getHitLeft()[pHitLeft].contains(hit.idData)) {
                            dataRight[p - rightRangeStart].add(hit.refData);
                            missingRight[p - rightRangeStart].remove(MtasToken.getPrefixFromValue(hit.refData));
                        }
                    }
                }
            }
        }
        // register unknown
        if (missingLeft != null) {
            for (int i = 0; i < missingLeft.length; i++) {
                for (String prefix : missingLeft[i]) {
                    if (!knownPrefixes.contains(prefix)) {
                        unknownLeft[i].add(prefix);
                    }
                }
            }
        }
        if (missingHit != null) {
            for (int i = 0; i < missingHit.length; i++) {
                for (String prefix : missingHit[i]) {
                    if (!knownPrefixes.contains(prefix)) {
                        unknownHit[i].add(prefix);
                    }
                }
            }
        }
        if (missingRight != null) {
            for (int i = 0; i < missingRight.length; i++) {
                for (String prefix : missingRight[i]) {
                    if (!knownPrefixes.contains(prefix)) {
                        unknownRight[i].add(prefix);
                    }
                }
            }
        }
        // construct keys
        keyLeft = dataToString(dataLeft, missingLeft, true);
        keyHit = dataToString(dataHit, missingHit, false);
        keyRight = dataToString(dataRight, missingRight, false);
        key1 = KEY_START;

        int hashLeft;
        if (keyLeft != null) {
            key1 += keyLeft;
            hashLeft = keyLeft.hashCode();
        } else {
            hashLeft = 1;
        }
        key1 += "|";

        int hashHit;
        if (keyHit != null) {
            key1 += keyHit;
            hashHit = keyHit.hashCode();
        } else {
            hashHit = 1;
        }
        key1 += "|";

        int hashRight;
        if (keyRight != null) {
            key1 += keyRight;
            hashRight = keyRight.hashCode();
        } else {
            hashRight = 1;
        }
        // compute hash
        key = key1;
        hash = hashHit * (hashLeft ^ 3) * (hashRight ^ 5);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return hash;
    }

    /**
     * Data equals.
     *
     * @param d1 the d 1
     * @param d2 the d 2
     * @return true, if successful
     */
    private boolean dataEquals(List<String>[] d1, List<String>[] d2) {
        List<String> a1;
        List<String> a2;
        if (d1 == null && d2 == null) {
            return true;
        } else if (d1 == null || d2 == null) {
            return false;
        } else {
            if (d1.length == d2.length) {
                for (int i = 0; i < d1.length; i++) {
                    a1 = d1[i];
                    a2 = d2[i];
                    if (a1 != null && a2 != null && a1.size() == a2.size()) {
                        for (int j = 0; j < a1.size(); j++) {
                            if (!a1.get(j).equals(a2.get(j))) {
                                return false;
                            }
                        }
                    } else {
                        return false;
                    }
                }
                return true;
            } else {
                return false;
            }
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        GroupHit other = (GroupHit) obj;
        if (hashCode() != other.hashCode()) {
            return false;
        }
        if (!dataEquals(dataHit, other.dataHit)) {
            return false;
        }
        if (!dataEquals(dataLeft, other.dataLeft)) {
            return false;
        }
        return dataEquals(dataRight, other.dataRight);
    }

    /**
     * Data to string.
     *
     * @param data    the data
     * @param missing the missing
     * @param reverse the reverse
     * @return the string
     * @throws UnsupportedEncodingException the unsupported encoding exception
     */
    private String dataToString(List<String>[] data, Set<String>[] missing, boolean reverse)
            throws UnsupportedEncodingException {
        StringBuilder text = null;
        Base64.Encoder encoder = Base64.getEncoder();
        String prefix;
        String postfix;
        List<String> dataItem;
        Set<String> missingItem;
        if (data != null && missing != null && data.length == missing.length) {
            for (int i = 0; i < data.length; i++) {
                if (reverse) {
                    dataItem = data[(data.length - i - 1)];
                    missingItem = missing[(data.length - i - 1)];
                } else {
                    dataItem = data[i];
                    missingItem = missing[i];
                }
                if (i > 0) {
                    text.append(",");
                } else {
                    text = new StringBuilder();
                }
                for (int j = 0; j < dataItem.size(); j++) {
                    if (j > 0) {
                        text.append("&");
                    }
                    prefix = MtasToken.getPrefixFromValue(dataItem.get(j));
                    postfix = MtasToken.getPostfixFromValue(dataItem.get(j));
                    text.append(encoder.encodeToString(prefix.getBytes(StandardCharsets.UTF_8)));
                    if (!postfix.isEmpty()) {
                        text.append(".");
                        text.append(encoder.encodeToString(postfix.getBytes(StandardCharsets.UTF_8)));
                    }
                }
                if (missingItem != null) {
                    String[] tmpMissing = missingItem.toArray(new String[0]);
                    for (int j = 0; j < tmpMissing.length; j++) {
                        if (j > 0 || !dataItem.isEmpty()) {
                            text.append("&");
                        }
                        text.append(encoder.encodeToString(("!" + tmpMissing[j]).getBytes(StandardCharsets.UTF_8)));
                    }
                }
            }
        }
        return text != null ? text.toString() : null;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return key;
    }

    /**
     * Key to sub sub object.
     *
     * @param key    the key
     * @param newKey the new key
     * @return the map[]
     */
    private static Map<String, String>[] keyToSubSubObject(String key, StringBuilder newKey) {
        if (!key.isEmpty()) {
            newKey.append(" [");
            String prefix;
            String postfix;
            String[] parts = key.split("&");
            Map<String, String>[] result = new HashMap[parts.length];

            Base64.Decoder decoder = Base64.getDecoder();
            Matcher matcher;
            StringBuilder tmpNewKey = null;
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].isEmpty()) {
                    result[i] = null;
                } else {
                    HashMap<String, String> subResult = new HashMap<>();
                    matcher = pattern.matcher(parts[i]);
                    if (tmpNewKey != null) {
                        tmpNewKey.append(" & ");
                    } else {
                        tmpNewKey = new StringBuilder();
                    }
                    if (matcher.matches()) {
                        prefix = new String(decoder.decode(matcher.group(1).getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8);
                        postfix = new String(decoder.decode(matcher.group(2).getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8);
                        tmpNewKey.append(prefix.replace("=", "\\="));
                        tmpNewKey.append("=\"").append(postfix.replace("\"", "\\\"")).append("\"");
                        subResult.put("prefix", prefix);
                        subResult.put("value", postfix);
                    } else {
                        prefix = new String(decoder.decode(parts[i].getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
                        tmpNewKey.append(prefix.replace("=", "\\="));
                        if (prefix.startsWith("!")) {
                            subResult.put("missing", prefix.substring(1));
                        } else {
                            subResult.put("prefix", prefix);
                        }
                    }
                    result[i] = subResult;
                }
            }
            if (tmpNewKey != null) {
                newKey.append(tmpNewKey);
            }
            newKey.append("]");
            return result;
        } else {
            newKey.append(" []");
            return null;
        }
    }

    /**
     * Key to sub object.
     *
     * @param key    the key
     * @param newKey the new key
     * @return the map
     */
    private static Map<Integer, Map<String, String>[]> keyToSubObject(String key, StringBuilder newKey) {
        Map<Integer, Map<String, String>[]> result = new HashMap<>();
        if (key == null || key.trim().isEmpty()) {
            return null;
        } else {
            String[] parts = key.split(",", -1);
            if (parts.length > 0) {
                for (int i = 0; i < parts.length; i++) {
                    result.put(i, keyToSubSubObject(parts[i].trim(), newKey));
                }
                return result;
            } else {
                return null;
            }
        }
    }

    /**
     * Key to object.
     *
     * @param key    the key
     * @param newKey the new key
     * @return the map
     */
    public static Map<String, Map<Integer, Map<String, String>[]>> keyToObject(String key, StringBuilder newKey) {
        if (key.startsWith(KEY_START)) {
            String content = key.substring(KEY_START.length());
            StringBuilder keyLeft = new StringBuilder();
            StringBuilder keyHit = new StringBuilder();
            StringBuilder keyRight = new StringBuilder();
            Map<String, Map<Integer, Map<String, String>[]>> result = new HashMap<>();
            Map<Integer, Map<String, String>[]> resultLeft = null;
            Map<Integer, Map<String, String>[]> resultHit = null;
            Map<Integer, Map<String, String>[]> resultRight = null;
            String[] parts = content.split("\\|", -1);
            if (parts.length == 3) {
                resultLeft = keyToSubObject(parts[0].trim(), keyLeft);
                resultHit = keyToSubObject(parts[1].trim(), keyHit);
                resultRight = keyToSubObject(parts[2].trim(), keyRight);
            } else if (parts.length == 1) {
                resultHit = keyToSubObject(parts[0].trim(), keyHit);
            }
            if (resultLeft != null) {
                result.put("left", resultLeft);
            }
            result.put("hit", resultHit);
            if (resultRight != null) {
                result.put("right", resultRight);
            }
            newKey.append(keyLeft);
            newKey.append(" |");
            newKey.append(keyHit);
            newKey.append(" |");
            newKey.append(keyRight);
            return result;
        } else {
            return null;
        }
    }

}
