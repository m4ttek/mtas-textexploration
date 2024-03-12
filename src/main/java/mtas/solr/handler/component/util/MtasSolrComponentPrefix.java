package mtas.solr.handler.component.util;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Set;
import java.util.SortedSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.handler.component.ShardRequest;
import org.apache.solr.handler.component.ShardResponse;

import mtas.codec.util.CodecComponent.ComponentField;
import mtas.codec.util.CodecComponent.ComponentFields;
import mtas.codec.util.CodecComponent.ComponentPrefix;
import mtas.solr.handler.component.MtasSolrSearchComponent;

/**
 * The Class MtasSolrComponentPrefix.
 */
public class MtasSolrComponentPrefix
    implements MtasSolrComponent<ComponentPrefix> {

  /** The Constant log. */
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** The search component. */
  MtasSolrSearchComponent searchComponent;

  /** The Constant NAME. */
  public static final String NAME = "prefix";

  /** The Constant PARAM_MTAS_PREFIX. */
  public static final String PARAM_MTAS_PREFIX = MtasSolrSearchComponent.PARAM_MTAS
      + ".prefix";

  /** The Constant NAME_MTAS_PREFIX_FIELD. */
  public static final String NAME_MTAS_PREFIX_FIELD = "field";

  /** The Constant NAME_MTAS_PREFIX_KEY. */
  public static final String NAME_MTAS_PREFIX_KEY = "key";

  /**
   * Instantiates a new mtas solr component prefix.
   *
   * @param searchComponent the search component
   */
  public MtasSolrComponentPrefix(MtasSolrSearchComponent searchComponent) {
    this.searchComponent = searchComponent;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * mtas.solr.handler.component.util.MtasSolrComponent#prepare(org.apache.solr.
   * handler.component.ResponseBuilder,
   * mtas.codec.util.CodecComponent.ComponentFields)
   */
  @Override
public void prepare(ResponseBuilder rb, ComponentFields mtasFields)
      throws IOException {
    Set<String> ids = MtasSolrResultUtil
        .getIdsFromParameters(rb.req.getParams(), PARAM_MTAS_PREFIX);
    if (!ids.isEmpty()) {
      int tmpCounter = 0;
      String[] fields = new String[ids.size()];
      String[] keys = new String[ids.size()];
      for (String id : ids) {
        fields[tmpCounter] = rb.req.getParams().get(
            PARAM_MTAS_PREFIX + "." + id + "." + NAME_MTAS_PREFIX_FIELD, null);
        keys[tmpCounter] = rb.req.getParams()
            .get(PARAM_MTAS_PREFIX + "." + id + "." + NAME_MTAS_PREFIX_KEY,
                String.valueOf(tmpCounter))
            .trim();
        tmpCounter++;
      }
      String uniqueKeyField = rb.req.getSchema().getUniqueKeyField().getName();
      mtasFields.doPrefix = true;
      // init and checks
      for (String field : fields) {
        if (field == null || field.isEmpty()) {
          throw new IOException("no (valid) field in mtas prefix");
        } else if (!mtasFields.list.containsKey(field)) {
          mtasFields.list.put(field, new ComponentField(uniqueKeyField));
        }
      }
      MtasSolrResultUtil.compareAndCheck(keys, fields, NAME_MTAS_PREFIX_KEY,
          NAME_MTAS_PREFIX_FIELD, true);
      for (int i = 0; i < fields.length; i++) {
        String field = fields[i];
        String key = ((keys == null) || (keys[i] == null)
            || (keys[i].isEmpty())) ? String.valueOf(i) + ":" + field
                : keys[i].trim();
        mtasFields.list.get(field).prefix = new ComponentPrefix(key);
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * mtas.solr.handler.component.util.MtasSolrComponent#modifyRequest(org.apache
   * .solr.handler.component.ResponseBuilder,
   * org.apache.solr.handler.component.SearchComponent,
   * org.apache.solr.handler.component.ShardRequest)
   */
  @Override
public void modifyRequest(ResponseBuilder rb, SearchComponent who,
      ShardRequest sreq) {
    if (sreq.params.getBool(MtasSolrSearchComponent.PARAM_MTAS, false)) {
      if (sreq.params.getBool(PARAM_MTAS_PREFIX, false)
          && (sreq.purpose & ShardRequest.PURPOSE_GET_TOP_IDS) != 0) {
        // do nothing
      } else {
        // remove prefix for other requests
        Set<String> keys = MtasSolrResultUtil
            .getIdsFromParameters(rb.req.getParams(), PARAM_MTAS_PREFIX);
        sreq.params.remove(PARAM_MTAS_PREFIX);
        for (String key : keys) {
          sreq.params.remove(
              PARAM_MTAS_PREFIX + "." + key + "." + NAME_MTAS_PREFIX_FIELD);
          sreq.params.remove(
              PARAM_MTAS_PREFIX + "." + key + "." + NAME_MTAS_PREFIX_KEY);
        }
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * mtas.solr.handler.component.util.MtasSolrComponent#create(mtas.codec.util.
   * CodecComponent.BasicComponent, java.lang.Boolean)
   */
  @Override
public SimpleOrderedMap<Object> create(ComponentPrefix prefix, Boolean encode)
      throws IOException {
    SimpleOrderedMap<Object> mtasPrefixResponse = new SimpleOrderedMap<>();
    mtasPrefixResponse.add("key", prefix.key);
    if (encode) {
      mtasPrefixResponse.add("_encoded_singlePosition",
          MtasSolrResultUtil.encode(prefix.singlePositionList));
      mtasPrefixResponse.add("_encoded_multiplePosition",
          MtasSolrResultUtil.encode(prefix.multiplePositionList));
      mtasPrefixResponse.add("_encoded_setPosition",
          MtasSolrResultUtil.encode(prefix.setPositionList));
      mtasPrefixResponse.add("_encoded_intersecting",
          MtasSolrResultUtil.encode(prefix.intersectingList));
    } else {
      mtasPrefixResponse.add("singlePosition", prefix.singlePositionList);
      mtasPrefixResponse.add("multiplePosition", prefix.multiplePositionList);
      mtasPrefixResponse.add("setPosition", prefix.setPositionList);
      mtasPrefixResponse.add("intersecting", prefix.intersectingList);
    }
    return mtasPrefixResponse;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * mtas.solr.handler.component.util.MtasSolrComponent#finishStage(org.apache.
   * solr.handler.component.ResponseBuilder)
   */
  @Override
@SuppressWarnings("unchecked")
  public void finishStage(ResponseBuilder rb) {
    if (rb.req.getParams().getBool(MtasSolrSearchComponent.PARAM_MTAS, false)
        && rb.stage >= ResponseBuilder.STAGE_EXECUTE_QUERY
        && rb.stage < ResponseBuilder.STAGE_GET_FIELDS) {
      for (ShardRequest sreq : rb.finished) {
        if (sreq.params.getBool(MtasSolrSearchComponent.PARAM_MTAS, false)
            && sreq.params.getBool(PARAM_MTAS_PREFIX, false)) {
          for (ShardResponse shardResponse : sreq.responses) {
            NamedList<Object> response = shardResponse.getSolrResponse()
                .getResponse();
            try {
              ArrayList<NamedList<Object>> data = (ArrayList<NamedList<Object>>) response
                  .findRecursive("mtas", NAME);
              if (data != null) {
                MtasSolrResultUtil.decode(data);
              }
            } catch (ClassCastException e) {
              log.debug("Error", e);
              // shouldnt happen
            }
          }
        }
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * mtas.solr.handler.component.util.MtasSolrComponent#distributedProcess(org.
   * apache.solr.handler.component.ResponseBuilder,
   * mtas.codec.util.CodecComponent.ComponentFields)
   */
  @Override
@SuppressWarnings("unchecked")
  public void distributedProcess(ResponseBuilder rb, ComponentFields mtasFields)
      throws IOException {
    // rewrite
    NamedList<Object> mtasResponse = null;
    try {
      mtasResponse = (NamedList<Object>) rb.rsp.getValues().get("mtas");
    } catch (ClassCastException e) {
      log.debug("Error", e);
      mtasResponse = null;
    }
    if (mtasResponse != null) {
      ArrayList<Object> mtasResponsePrefix;
      try {
        mtasResponsePrefix = (ArrayList<Object>) mtasResponse.get(NAME);
        if (mtasResponsePrefix != null) {
          NamedList<Object> mtasResponsePrefixItem;
          for (Object mtasResponsePrefixItemRaw : mtasResponsePrefix) {
            mtasResponsePrefixItem = (NamedList<Object>) mtasResponsePrefixItemRaw;
            repairPrefixItems(mtasResponsePrefixItem);
            MtasSolrResultUtil.rewrite(mtasResponsePrefixItem, searchComponent);
          }
        }
      } catch (ClassCastException e) {
        log.debug("Error", e);
        mtasResponse.remove(NAME);
      }
    }
  }

  /**
   * Repair prefix items.
   *
   * @param mtasResponse the mtas response
   */
  @SuppressWarnings("unchecked")
  private void repairPrefixItems(NamedList<Object> mtasResponse) {
    // repair prefix lists
    try {
      ArrayList<NamedList<?>> list = (ArrayList<NamedList<?>>) mtasResponse
          .findRecursive(NAME);
      // MtasSolrResultUtil.rewrite(list);
      if (list != null) {
        for (NamedList<?> item : list) {
          SortedSet<String> singlePosition = (SortedSet<String>) item
              .get("singlePosition");
          SortedSet<String> multiplePosition = (SortedSet<String>) item
              .get("multiplePosition");
          if (singlePosition != null && multiplePosition != null) {
            for (String prefix : multiplePosition) {
              if (singlePosition.contains(prefix)) {
                singlePosition.remove(prefix);
              }
            }
          }
        }
      }
    } catch (ClassCastException e) {
      log.debug("Error", e);
    }
  }

}
