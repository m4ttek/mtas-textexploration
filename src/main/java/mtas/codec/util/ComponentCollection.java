package mtas.codec.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.noggit.JSONParser;
import org.noggit.ObjectBuilder;

/**
 * The Class ComponentCollection.
 */
public final class ComponentCollection implements BasicComponent {

    /**
     * The Constant ACTION_CREATE.
     */
    public static final String ACTION_CREATE = "create";

    /**
     * The Constant ACTION_CHECK.
     */
    public static final String ACTION_CHECK = "check";

    /**
     * The Constant ACTION_LIST.
     */
    public static final String ACTION_LIST = "list";

    /**
     * The Constant ACTION_POST.
     */
    public static final String ACTION_POST = "post";

    /**
     * The Constant ACTION_IMPORT.
     */
    public static final String ACTION_IMPORT = "import";

    /**
     * The Constant ACTION_DELETE.
     */
    public static final String ACTION_DELETE = "delete";

    /**
     * The Constant ACTION_EMPTY.
     */
    public static final String ACTION_EMPTY = "empty";

    /**
     * The Constant ACTION_GET.
     */
    public static final String ACTION_GET = "get";

    /**
     * The key.
     */
    public String key;

    /**
     * The version.
     */
    public String version;

    /**
     * The original version.
     */
    public String originalVersion;

    /**
     * The id.
     */
    public String id;

    /**
     * The action.
     */
    private final String action;

    /**
     * The fields.
     */
    private Set<String> fields;

    /**
     * The values.
     */
    private HashSet<String> values;

    /**
     * Instantiates a new component collection.
     *
     * @param key    the key
     * @param action the action
     */
    public ComponentCollection(String key, String action) {
        this.key = key;
        this.action = action;
        this.version = null;
        this.originalVersion = null;
        values = new HashSet<>();
    }

    /**
     * Sets the list variables.
     *
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void setListVariables() throws IOException {
        if (action.equals(ACTION_LIST)) {
            // do nothing
        } else {
            throw new IOException("not allowed with action " + action);
        }
    }

    /**
     * Sets the create variables.
     *
     * @param id     the id
     * @param fields the fields
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void setCreateVariables(String id, Set<String> fields) throws IOException {
        if (action.equals(ACTION_CREATE)) {
            this.id = id;
            this.fields = fields;
        } else {
            throw new IOException("not allowed with action " + action);
        }
    }

    /**
     * Sets the check variables.
     *
     * @param id the new check variables
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void setCheckVariables(String id) throws IOException {
        if (action.equals(ACTION_CHECK)) {
            this.id = id;
        } else {
            throw new IOException("not allowed with action " + action);
        }
    }

    /**
     * Sets the gets the variables.
     *
     * @param id the new gets the variables
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void setGetVariables(String id) throws IOException {
        if (action.equals(ACTION_GET)) {
            this.id = id;
        } else {
            throw new IOException("not allowed with action " + action);
        }
    }

    /**
     * Sets the post variables.
     *
     * @param id              the id
     * @param values          the values
     * @param originalVersion the original version
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void setPostVariables(String id, HashSet<String> values, String originalVersion) throws IOException {
        if (action.equals(ACTION_POST)) {
            this.id = id;
            this.values = values;
            this.originalVersion = originalVersion;
        } else {
            throw new IOException("not allowed with action " + action);
        }
    }

    /**
     * Sets the import variables.
     *
     * @param id         the id
     * @param url        the url
     * @param collection the collection
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void setImportVariables(String id, String url, String collection) throws IOException {
        if (action.equals(ACTION_IMPORT)) {
            this.id = id;
            String importUrlBuffer = url + "select" +
                    "?q=*:*&rows=0&wt=json" +
                    "&mtas=true&mtas.collection=true" +
                    "&mtas.collection.0.key=0" +
                    "&mtas.collection.0.action=get" +
                    "&mtas.collection.0.id=" + URLEncoder.encode(collection, StandardCharsets.UTF_8);
            Map<String, Object> params = getImport(importUrlBuffer);
            try {
                if (params.containsKey("mtas") && params.get("mtas") instanceof Map) {
                    Map<String, Object> mtasParams = (Map<String, Object>) params.get("mtas");
                    if (mtasParams.containsKey("collection") && mtasParams.get("collection") instanceof List) {
                        List<Object> mtasCollectionList = (List<Object>) mtasParams.get("collection");
                        if (mtasCollectionList.size() == 1 && mtasCollectionList.get(0) instanceof Map) {
                            Map<String, Object> collectionData = (Map<String, Object>) mtasCollectionList.get(0);
                            if (collectionData.containsKey("values") && collectionData.get("values") instanceof List) {
                                List<String> valuesList = (List<String>) collectionData.get("values");
                                values.addAll(valuesList);
                            } else {
                                throw new IOException("no values in response");
                            }
                        } else {
                            throw new IOException("no valid mtas collection item in response");
                        }
                    } else {
                        throw new IOException("no valid mtas collection in response");
                    }
                } else {
                    throw new IOException("no valid mtas in response");
                }
            } catch (ClassCastException e) {
                throw new IOException("unexpected response", e);
            }
        } else {
            throw new IOException("not allowed with action " + action);
        }
    }

    /**
     * Gets the import.
     *
     * @param collectionGetUrl the collection get url
     * @return the import
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private Map<String, Object> getImport(String collectionGetUrl) throws IOException {
        // get data
        URL url = new URL(collectionGetUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(false);
        connection.setDoInput(true);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("charset", "utf-8");
        connection.setUseCaches(false);
        // process response
        InputStream is = null;
        try {
            is = connection.getInputStream();
        } catch (IOException ioe) {
            throw new IOException("Couldn't get data from url");
        }
        InputStreamReader in = new InputStreamReader(is, StandardCharsets.UTF_8);
        Map<String, Object> params = new HashMap<>();
        getParamsFromJSON(params, toString(in));
        connection.disconnect();
        return params;
    }

    private String toString(InputStreamReader in) throws IOException {
        try (var lines = new BufferedReader(in).lines()) {
            return lines.collect(Collectors.joining());
        }
    }

    /**
     * Sets the delete variables.
     *
     * @param id the new delete variables
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void setDeleteVariables(String id) throws IOException {
        if (action.equals(ACTION_DELETE)) {
            this.id = id;
        } else {
            throw new IOException("not allowed with action " + action);
        }
    }

    /**
     * Action.
     *
     * @return the string
     */
    public String action() {
        return action;
    }

    /**
     * Original version.
     *
     * @return the string
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public String originalVersion() throws IOException {
        if (action.equals(ACTION_POST)) {
            return originalVersion;
        } else {
            throw new IOException("unexpected call for " + action);
        }
    }

    /**
     * Values.
     *
     * @return the hash set
     */
    public HashSet<String> values() {
        return values;
    }

    /**
     * Fields.
     *
     * @return the sets the
     */
    public Set<String> fields() {
        return fields;
    }

    /**
     * Adds the value.
     *
     * @param value the value
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public void addValue(String value) throws IOException {
        if (action.equals(ACTION_CREATE)) {
            if (version == null) {
                values.add(value);
            } else {
                throw new IOException("version already set");
            }
        } else {
            throw new IOException("not allowed for action '" + action + "'");
        }
    }

    /**
     * Gets the params from JSON.
     *
     * @param params the params
     * @param json   the json
     * @return the params from JSON
     */
    private static void getParamsFromJSON(Map<String, Object> params, String json) {
        JSONParser parser = new JSONParser(json);
        try {
            Object o = ObjectBuilder.getVal(parser);
            if (!(o instanceof Map)) {
                return;
            }
            Map<String, Object> map = (Map<String, Object>) o;
            // To make consistent with json.param handling, we should make query
            // params come after json params (i.e. query params should
            // appear to overwrite json params.

            // Solr params are based on String though, so we need to convert
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (params.get(key) != null) {
                    continue;
                }

                if (val == null) {
                    params.remove(key);
                } else {
                    params.put(key, val);
                }
            }

        } catch (Exception e) {
            // ignore parse exceptions at this stage, they may be caused by
            // incomplete
            // macro expansions
        }

    }

}
