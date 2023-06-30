package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.json.JSONObject;
import org.wso2.micro.integrator.security.user.api.UserStoreException;
import org.wso2.micro.integrator.security.user.api.UserStoreManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RolesResourceGrpc {


    protected org.wso2.micro.integrator.grpc.proto.RoleList handleGet() throws UserStoreException {
        String[] roles = GrpcUtils.getUserStore(null).getRoleNames();
        return setResponseBody(Arrays.asList(roles));
    }

    private org.wso2.micro.integrator.grpc.proto.RoleList setResponseBody(List<String> roles) {

        org.wso2.micro.integrator.grpc.proto.RoleList.Builder roleListBuilder = org.wso2.micro.integrator.grpc.proto.RoleList.newBuilder().setCount(roles.size());
        for (String role : roles) {
            roleListBuilder.addRoles(role);
        }
        return roleListBuilder.build();
    }

    private List<String> getSearchResults(String searchKey) throws UserStoreException {
        String[] roles = GrpcUtils.getUserStore(null).getRoleNames();
        List<String> searchResults = new ArrayList<>();

        for (String role : roles) {
            if (role.toLowerCase().contains(searchKey)) {
                searchResults.add(role);
            }
        }
        return searchResults;
    }

    protected org.wso2.micro.integrator.grpc.proto.RoleList populateSearchResults(String searchKey) throws UserStoreException {

        List<String> roles = getSearchResults(searchKey);
        return setResponseBody(roles);
    }

    protected JSONObject handlePost(org.apache.axis2.context.MessageContext axis2MessageContext)
            throws UserStoreException, ResourceNotFoundException, IOException {
        /*
        if (!GrpcUtils.isUserAuthenticated(messageContext)) {
            LOG.warn("Adding a user without authenticating/authorizing the request sender. Adding "
                    + "authetication and authorization handlers is recommended.");
        }
        */
/*        if (!JsonUtil.hasAJsonPayload(axis2MessageContext)) {
            return Utils.createJsonErrorObject("JSON payload is missing");
        }*/
        JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
        String domain = null;
        if (payload.has(DOMAIN) ) {
            domain = payload.get(DOMAIN).getAsString();
        }
        UserStoreManager userStoreManager = GrpcUtils.getUserStore(domain);
        if (payload.has(ROLE)) {
            String role = payload.get(ROLE).getAsString();
            if (userStoreManager.isExistingRole(role)) {
                throw new UserStoreException("The role : " + role + " already exists");
            }
            userStoreManager.addRole(role, null, null, false);
            JSONObject roleResponse = new JSONObject();
            roleResponse.put(ROLE, role);
            roleResponse.put(STATUS, "Added");
            return roleResponse;
        } else {
            throw new IOException("Missing role name in the payload");
        }
    }

    protected JSONObject handlePut(org.apache.axis2.context.MessageContext axis2MessageContext)
            throws UserStoreException, ResourceNotFoundException, IOException {

        /*if (!Utils.isUserAuthenticated(messageContext)) {
            LOG.warn("Adding a user without authenticating/authorizing the request sender. Adding "
                    + "authetication and authorization handlers is recommended.");
        }
        if (!JsonUtil.hasAJsonPayload(axis2MessageContext)) {
            return Utils.createJsonErrorObject("JSON payload is missing");
        }*/
        JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
        String domain = null;
        if (payload.has(DOMAIN) ) {
            domain = payload.get(DOMAIN).getAsString();
        }
        UserStoreManager userStoreManager = Utils.getUserStore(domain);
        ArrayList<String> addedRoleList = new ArrayList<>();
        ArrayList<String> removedRoleList = new ArrayList<>();
        if ((payload.has(ROLE_LIST_ADDED) || payload.has(ROLE_LIST_REMOVED)) && payload.has(USER_ID)) {
            String userId = payload.get(USER_ID).getAsString();
            if (!userStoreManager.isExistingUser(userId)) {
                throw new UserStoreException("The user : " + userId + " does not exists");
            }
            if (payload.has(ROLE_LIST_ADDED)) {
                JsonArray addedRoles = payload.getAsJsonArray(ROLE_LIST_ADDED);
                for (JsonElement roleElement : addedRoles) {
                    addedRoleList.add(roleElement.getAsString());
                }
            }
            if (payload.has(ROLE_LIST_REMOVED)) {
                JsonArray removedRoles = payload.getAsJsonArray(ROLE_LIST_REMOVED);
                for (JsonElement roleElement : removedRoles) {
                    removedRoleList.add(roleElement.getAsString());
                }
            }
            userStoreManager.updateRoleListOfUser(userId,removedRoleList.toArray(new String[0]),addedRoleList.toArray(new String[0]));
            JSONObject roleResponse = new JSONObject();
            roleResponse.put(USER_ID, userId);
            roleResponse.put(STATUS, "Added/removed the roles");
            return roleResponse;
        } else {
            throw new IOException("Missing one or more of the fields, '" + USER_ID + "', '" + ROLE_LIST_ADDED + "', '" +
                    ROLE_LIST_REMOVED + "' in the "
                    + "payload.");
        }
    }
}
