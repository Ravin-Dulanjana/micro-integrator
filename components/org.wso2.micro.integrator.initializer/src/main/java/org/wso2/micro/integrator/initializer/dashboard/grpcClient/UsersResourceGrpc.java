package org.wso2.micro.integrator.initializer.dashboard.grpcClient;

import com.google.gson.JsonObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.commons.json.JsonUtil;
import org.json.JSONObject;
import org.wso2.micro.core.util.AuditLogger;
import org.wso2.micro.integrator.security.user.api.UserStoreException;
import org.wso2.micro.integrator.security.user.api.UserStoreManager;
import org.wso2.micro.integrator.security.user.core.multiplecredentials.UserAlreadyExistsException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class UsersResourceGrpc {

    private static final Log LOG = LogFactory.getLog(UsersResourceGrpc.class);
    private static final String USER_ID = "userId";
    private static final String IS_ADMIN = "isAdmin";
    private static List<String> getUserResults() throws UserStoreException {
        String searchPattern = GrpcUtils.getQueryParameter(messageContext, PATTERN);
        if (Objects.isNull(searchPattern)) {
            searchPattern = "*";
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Searching for users with the pattern: " + searchPattern);
        }
        List<String> patternUsersList = Arrays.asList(Utils.getUserStore(null).listUsers(searchPattern, -1));
        if (LOG.isDebugEnabled()) {
            LOG.debug("Retrieved list of users for the pattern: ");
            patternUsersList.forEach(LOG::debug);
        }
        String roleFilter = Utils.getQueryParameter(messageContext, ROLE);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Searching for users with the role: " + roleFilter);
        }
        List<String> users;
        if (Objects.isNull(roleFilter)) {
            users = patternUsersList;
        } else {
            List<String> roleUserList = Arrays.asList(Utils.getUserStore(null).getUserListOfRole(roleFilter));
            if (LOG.isDebugEnabled()) {
                LOG.debug("Retrieved list of users for the role: ");
                roleUserList.forEach(LOG::debug);
            }
            users = patternUsersList.stream().filter(roleUserList::contains).collect(Collectors.toList());
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Filtered list of users: ");
            users.forEach(LOG::debug);
        }
        return users;
    }


    protected JSONObject handleGet() throws UserStoreException {
        return setResponseBody(getUserResults());
    }

    protected JSONObject populateSearchResults(String searchKey) throws UserStoreException {

        List<String> users = getUserResults().stream()
                .filter(name -> name.toLowerCase().contains(searchKey))
                .collect(Collectors.toList());
        return setResponseBody(users);
    }

    private JSONObject setResponseBody(List<String> users) {
        JSONObject jsonBody = Utils.createJSONList(users.size());
        for (String user : users) {
            JSONObject userObject = new JSONObject();
            userObject.put(USER_ID, user);
            jsonBody.getJSONArray(LIST).put(userObject);
        }
        return jsonBody;
    }

    private JSONObject handlePost(MessageContext messageContext,
                                  org.apache.axis2.context.MessageContext axis2MessageContext)
            throws UserStoreException, IOException, ResourceNotFoundException {
        if (!Utils.isUserAuthenticated(messageContext)) {
            LOG.warn("Adding a user without authenticating/authorizing the request sender. Adding "
                    + "authetication and authorization handlers is recommended.");
        }
        if (!JsonUtil.hasAJsonPayload(axis2MessageContext)) {
            return Utils.createJsonErrorObject("JSON payload is missing");
        }
        JsonObject payload = Utils.getJsonPayload(axis2MessageContext);
        boolean isAdmin = false;
        if (payload.has(USER_ID) && payload.has(PASSWORD)) {
            String[] roleList = null;
            if (payload.has(IS_ADMIN) && payload.get(IS_ADMIN).getAsBoolean()) {
                String adminRole = Utils.getRealmConfiguration().getAdminRoleName();
                roleList = new String[]{adminRole};
                isAdmin = payload.get(IS_ADMIN).getAsBoolean();
            }
            String user = payload.get(USER_ID).getAsString();
            String domain = null;
            if (payload.has(DOMAIN) ) {
                domain = payload.get(DOMAIN).getAsString();
            }
            UserStoreManager userStoreManager = Utils.getUserStore(domain);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Adding user, id: " + user + ", roleList: " + Arrays.toString(roleList));
            }
            try {
                synchronized (this) {
                    userStoreManager.addUser(user, payload.get(PASSWORD).getAsString(),
                            roleList, null, null);
                }
            } catch (UserAlreadyExistsException e) {
                throw new IOException("User: " + user + " already exists.", e);
            }
            JSONObject jsonBody = new JSONObject();
            jsonBody.put(USER_ID, user);
            jsonBody.put(STATUS, "Added");
            String performedBy = Constants.ANONYMOUS_USER;
            if (messageContext.getProperty(Constants.USERNAME_PROPERTY) !=  null) {
                performedBy = messageContext.getProperty(Constants.USERNAME_PROPERTY).toString();
            }
            JSONObject info = new JSONObject();
            info.put(USER_ID, user);
            info.put(IS_ADMIN, isAdmin);
            AuditLogger.logAuditMessage(performedBy, Constants.AUDIT_LOG_TYPE_USER, Constants.AUDIT_LOG_ACTION_CREATED, info);
            return jsonBody;
        } else {
            throw new IOException("Missing one or more of the fields, '" + USER_ID + "', '" + PASSWORD + "' in the "
                    + "payload.");
        }
    }
}
