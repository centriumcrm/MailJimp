/*
 * Copyright 2011 Michael Laccetti
 *
 * This file is part of MailJimp.
 *
 * MailJimp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * MailJimp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with MailJimp.  If not, see <http://www.gnu.org/licenses/>.
 */
package mailjimp.service.impl;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.ws.rs.core.MediaType;

import mailjimp.dom.enums.EmailType;
import mailjimp.dom.enums.InterestGroupingType;
import mailjimp.dom.enums.InterestGroupingUpdateType;
import mailjimp.dom.enums.MemberStatus;
import mailjimp.dom.request.list.ListBatchSubscribeRequest;
import mailjimp.dom.request.list.ListBatchSubscribeStruct;
import mailjimp.dom.request.list.ListBatchUnsubscribeRequest;
import mailjimp.dom.request.list.ListInterestGroupAddRequest;
import mailjimp.dom.request.list.ListInterestGroupDelRequest;
import mailjimp.dom.request.list.ListInterestGroupUpdateRequest;
import mailjimp.dom.request.list.ListInterestGroupingAddRequest;
import mailjimp.dom.request.list.ListInterestGroupingDelRequest;
import mailjimp.dom.request.list.ListInterestGroupingUpdateRequest;
import mailjimp.dom.request.list.ListInterestGroupingsRequest;
import mailjimp.dom.request.list.ListMemberInfoRequest;
import mailjimp.dom.request.list.ListMembersRequest;
import mailjimp.dom.request.list.ListSubscribeRequest;
import mailjimp.dom.request.list.ListUnsubscribeRequest;
import mailjimp.dom.request.list.ListUpdateMemberRequest;
import mailjimp.dom.request.list.ListsRequest;
import mailjimp.dom.request.security.ApiKeyAddRequest;
import mailjimp.dom.request.security.ApiKeyExpireRequest;
import mailjimp.dom.request.security.ApiKeyRequest;
import mailjimp.dom.response.MailJimpErrorResponse;
import mailjimp.dom.response.list.ListBatchSubscribeResponse;
import mailjimp.dom.response.list.InterestGrouping;
import mailjimp.dom.response.list.ListBatchUnsubscribeResponse;
import mailjimp.dom.response.list.ListMemberInfoResponse;
import mailjimp.dom.response.list.ListMembersResponse;
import mailjimp.dom.response.list.ListsResponse;
import mailjimp.dom.response.list.MailingList;
import mailjimp.dom.response.list.MemberInfo;
import mailjimp.dom.response.list.MemberResponseInfo;
import mailjimp.dom.security.ApiKey;
import mailjimp.service.MailJimpException;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

public class MailJimpJsonService extends AbstractMailJimpService {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ObjectMapper m = new ObjectMapper();

  private Client client;
  private WebResource resource;

  public MailJimpJsonService() {
    super();
  }

  public MailJimpJsonService(String username, String password, String apiKey, String apiVersion, boolean ssl) {
    super(username, password, apiKey, apiVersion, ssl);
  }

  @PostConstruct
  public void init() {
    // check if everything is configured.
    checkConfig();
    log.info("Creating MailChimp integration client.");
    String url = buildServerURL();
    log.info("Server URL is: {}", url);
    client = Client.create();
    resource = client.resource(url);
    
    SerializationConfig s = m.getSerializationConfig();
    s.setSerializationInclusion(Inclusion.NON_NULL);
    s.withDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    m.setSerializationConfig(s);
    
    DeserializationConfig d = m.getDeserializationConfig();
    d.withDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
    m.setDeserializationConfig(d);
    
    m.setDateFormat(new SimpleDateFormat("yyyy-MM-MM HH:mm:ss"));
  }
  
  private <V> V performRequest(String method, Object param, TypeReference<V> typeRef) throws MailJimpException {
    String requestJson = null;
    try {
      requestJson = m.writeValueAsString(param);
    } catch (Exception ex) {
      log.error("Could not convert lists request to JSON.", ex);
      throw new MailJimpException("Could not convert parameter to JSON.", ex);
    }

    log.debug("Outbound request: {}", requestJson);
    ClientResponse response = resource.queryParam("method", method).accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, requestJson);

    if (response.getStatus() != 200) {
      log.error("Failed : HTTP error code : " + response.getStatus());
      String entity = null;
      try {
        IOUtils.toString(response.getEntityInputStream());
      } catch (Exception ex) {
        log.warn("Could not convert entity to String.", ex);
      }
      throw new MailJimpException(String.format("Could not perform action (%s).  %s", method, entity));
    }

    String responseJson = response.getEntity(String.class);
    log.debug("Inbound response: {}", responseJson);
    
    if (responseJson.startsWith("{\"error\":")) {
      try {
        MailJimpErrorResponse err = m.readValue(responseJson, MailJimpErrorResponse.class);
        throw new MailJimpException(String.format("Error while performing action (%s).  Message: %s.  Code: %s.", method, err.getError(), err.getCode()));
      } catch (IOException ie) {
        log.error("Could not convert error from JSON to Object.", ie);
        throw new MailJimpException("Could not convert error from JSON to Object.", ie);
      }
    }

    try {
      V val = m.readValue(responseJson, typeRef);
      return val;
    } catch (Exception ex) {
      log.error(String.format("Could not convert JSON to expected type (%s).", typeRef.getType().getClass().getCanonicalName()), ex);
      throw new MailJimpException(String.format("Could not convert JSON to expected type (%s).", typeRef.getType().getClass().getCanonicalName()), ex);
    }
  }
  
  @Override
  public List<ApiKey> apikeys(boolean expired) throws MailJimpException {
    List<ApiKey> response = performRequest("apikeys", new ApiKeyRequest(apiKey, username, password, expired), new TypeReference<List<ApiKey>>() {/* empty */});
    log.debug("Api keys info: {}", response);
    return response;
  }

  @Override
  public String apikeyAdd() throws MailJimpException {
    String apikey = performRequest("apikeyAdd", new ApiKeyAddRequest(apiKey, username, password), new TypeReference<String>() {/* empty */});
    log.debug("API key add: {}", apikey);
    return apikey;
  }

  @Override
  public boolean apikeyExpire(String keyToExpire) throws MailJimpException {
    Boolean response = performRequest("apikeyExpire", new ApiKeyExpireRequest(keyToExpire, username, password), new TypeReference<Boolean>() {/* empty */});
    log.debug("API key expire: {}", response);
    return response;
  }

  @Override
  public List<MailingList> lists() throws MailJimpException {
    ListsResponse response = performRequest("lists", new ListsRequest(apiKey), new TypeReference<ListsResponse>() {/* empty */});
    log.debug("List info: {}", response);
    return response.getLists();
  }

  @Override
  public List<MemberResponseInfo> listMembers(String listId, MemberStatus memberStatus, Date since, Integer start, Integer limit) throws MailJimpException {
    ListMembersResponse response = performRequest("listMembers", new ListMembersRequest(apiKey, listId, memberStatus.getStatus(), since, start, limit), new TypeReference<ListMembersResponse>() {/* empty */});
    log.debug("List info: {}", response);
    return response.getMembers();
  }

  @Override
  public List<MemberInfo> listMemberInfo(String listId, List<String> emailAddresses) throws MailJimpException {
    ListMemberInfoResponse response = performRequest("listMemberInfo", new ListMemberInfoRequest(apiKey, listId, emailAddresses), new TypeReference<ListMemberInfoResponse>() {/* empty */});
    log.debug("List member info response: {}", response);
    return response.getMembers();
  }

  @Override
  public boolean listSubscribe(String listId, String emailAddress, Map<String, Object> mergeVars, EmailType emailType, boolean doubleOptin, boolean updateExisting, boolean replaceInterests, boolean sendWelcome) throws MailJimpException {
    Boolean response = performRequest("listSubscribe", new ListSubscribeRequest(apiKey, listId, emailAddress, mergeVars, emailType, doubleOptin, updateExisting, replaceInterests, sendWelcome), new TypeReference<Boolean>() {/* empty */});
    log.debug("List subscribe response: {}", response);
    return response;
  }

  @Override
  public ListBatchSubscribeResponse listBatchSubscribe(String listId, List<ListBatchSubscribeStruct> batch, boolean doubleOptin, boolean updateExisting, boolean replaceInterests) throws MailJimpException {
    ListBatchSubscribeResponse response = performRequest("listBatchSubscribe", new ListBatchSubscribeRequest(apiKey, listId, batch, doubleOptin, updateExisting, replaceInterests), new TypeReference<ListBatchSubscribeResponse>() {/* empty */});
    log.debug("List batch subscribe response: {}", response);
    return response;
  }

  @Override
  public ListBatchUnsubscribeResponse listBatchUnsubscribe(String listId, List<String> emails, boolean deleteMember, boolean sendGoodbye, boolean sendNotify) throws MailJimpException {
    ListBatchUnsubscribeResponse response = performRequest("listBatchUnsubscribe", new ListBatchUnsubscribeRequest(apiKey, listId, emails, deleteMember, sendGoodbye, sendNotify), new TypeReference<ListBatchUnsubscribeResponse>() {/* empty */});
    log.debug("List batch unsubscribe response: {}", response);
    return response;
  }

  @Override
  public boolean listUpdateMember(String listId, String emailAddress, Map<String, Object> mergeVars, EmailType emailType, boolean replaceInterests) throws MailJimpException {
    Boolean response = performRequest("listUpdateMember", new ListUpdateMemberRequest(apiKey, listId, emailAddress, mergeVars, emailType, replaceInterests), new TypeReference<Boolean>() {/* empty */});
    log.debug("List update member response: {}", response);
    return response;
  }

  @Override
  public boolean listUnsubscribe(String listId, String emailAddress, boolean deleteMember, boolean sendGoodbye, boolean sendNotify) throws MailJimpException {
    Boolean response = performRequest("listUnsubscribe", new ListUnsubscribeRequest(apiKey, listId, emailAddress, deleteMember, sendGoodbye, sendNotify), new TypeReference<Boolean>() {/* empty */});
    log.debug("List unsubscribe response: {}", response);
    return response;
  }

  @Override
  public List<InterestGrouping> listInterestGroupings(String listId) throws MailJimpException {
    List<InterestGrouping> response = performRequest("listInterestGroupings", new ListInterestGroupingsRequest(apiKey, listId), new TypeReference<List<InterestGrouping>>() {/* empty */});
    log.debug("List interesting groupings response: {}", response);
    return response;
  }

  @Override
  public boolean listInterestGroupAdd(String listId, String groupName, Integer groupingId) throws MailJimpException {
    Boolean success = performRequest("listInterestGroupAdd", new ListInterestGroupAddRequest(apiKey, listId, groupName, groupingId), new TypeReference<Boolean>() {/* empty */});
    log.debug("List interest group add: {}", success);
    return success;
  }

  @Override
  public boolean listInterestGroupDelete(String listId, String groupName, Integer groupingId) throws MailJimpException {
    Boolean success = performRequest("listInterestGroupDel", new ListInterestGroupDelRequest(apiKey, listId, groupName, groupingId), new TypeReference<Boolean>() {/* empty */});
    log.debug("List interest group delete: {}", success);
    return success;
  }

  @Override
  public boolean listInterestGroupUpdate(String listId, String oldName, String newName) throws MailJimpException {
    Boolean success = performRequest("listInterestGroupUpdate", new ListInterestGroupUpdateRequest(apiKey, listId, oldName, newName), new TypeReference<Boolean>() {/* empty */});
    log.debug("List interest group update: {}", success);
    return success;
  }

  @Override
  public Integer listInterestGroupingAdd(String listId, String name, InterestGroupingType type, List<String> groups) throws MailJimpException {
    Integer response = performRequest("listInterestGroupingAdd", new ListInterestGroupingAddRequest(apiKey, listId, name, type, groups), new TypeReference<Integer>() {/* empty */});
    log.debug("New interesting grouping ID: {}", response);
    return response;
  }

  @Override
  public Boolean listInterestGroupingUpdate(Integer groupingId, InterestGroupingUpdateType fieldNameToUpdate, String value) throws MailJimpException {
    Boolean response = performRequest("listInterestGroupingUpdate", new ListInterestGroupingUpdateRequest(apiKey, groupingId, fieldNameToUpdate, value), new TypeReference<Boolean>() {/* empty */});
    log.debug("Update interesting grouping status: {}", response);
    return response;
  }

  @Override
  public Boolean listInterestGroupingDelete(Integer groupingId) throws MailJimpException {
    Boolean response = performRequest("listInterestGroupingDel", new ListInterestGroupingDelRequest(apiKey, groupingId), new TypeReference<Boolean>() {/* empty */});
    log.debug("Delete interesting grouping status: {}", response);
    return response;
  }
}