/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.hydra.job.web.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.addthis.codec.json.CodecJSON;
import com.addthis.hydra.job.HostFailWorker;
import com.addthis.hydra.job.RebalanceOutcome;
import com.addthis.hydra.job.auth.PermissionsManager;
import com.addthis.hydra.job.spawn.Spawn;
import com.addthis.hydra.job.mq.HostState;
import com.addthis.maljson.JSONArray;
import com.addthis.maljson.JSONObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/host")
public class HostResource {

    private static final Logger log = LoggerFactory.getLogger(HostResource.class);

    private final Spawn spawn;
    private final HostFailWorker hostFailWorker;

    private final PermissionsManager permissionsManager;

    public HostResource(Spawn spawn) {
        this.spawn = spawn;
        this.hostFailWorker = spawn.getHostFailWorker();
        this.permissionsManager = spawn.getPermissionsManager();
    }

    @GET
    @Path("/rebalance")
    @Produces(MediaType.APPLICATION_JSON)
    public Response rebalanceHost(@QueryParam("id") String hostUuid,
                                  @QueryParam("user") String user,
                                  @QueryParam("token") String token,
                                  @QueryParam("sudo") String sudo) throws Exception {
        try {
            if (!permissionsManager.adminAction(user, token, sudo)) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("insufficient privileges").build();
            }
            String[] hostUuids = hostUuid.split(",");
            JSONArray outcomes = new JSONArray();
            for (String uuid : hostUuids) {
                emitLogLineForAction(user, "host rebalance on " + hostUuid);
                RebalanceOutcome outcome = spawn.rebalanceHost(uuid);
                JSONObject json = CodecJSON.encodeJSON(outcome);
                outcomes.put(json);
            }
            return Response.ok(outcomes.toString()).build();
        } catch (Exception ex)  {
            log.warn("", ex);
            return Response.serverError().entity("Host Rebalance Error: " + ex.getMessage()).build();
        }

    }

    @GET
    @Path("/fail")
    @Produces(MediaType.APPLICATION_JSON)
    public Response failHost(@QueryParam("id") String hostUuids,
                             @QueryParam("deadFs") boolean filesystemDead,
                             @QueryParam("user") String user,
                             @QueryParam("token") String token,
                             @QueryParam("sudo") String sudo) throws Exception {
        try {
            if (!permissionsManager.adminAction(user, token, sudo)) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("insufficient privileges").build();
            }
            emitLogLineForAction(user, "fail host on " + hostUuids);
            HostFailWorker.FailState failureMode;
            if (filesystemDead) {
                failureMode = HostFailWorker.FailState.FAILING_FS_DEAD;
            } else {
                failureMode = HostFailWorker.FailState.FAILING_FS_OKAY;
            }
            hostFailWorker.markHostsToFail(hostUuids, failureMode);
            JSONObject json = new JSONObject();
            json.put("success", hostUuids.split(",").length);
            return Response.ok(json.toString()).build();
        } catch (Exception ex)  {
            log.warn("", ex);
            return Response.serverError().entity("Host Fail Error: " + ex.getMessage()).build();
        }
    }

    @GET
    @Path("/failcancel")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancelFailHost(@QueryParam("id") String hostUuids,
                                   @QueryParam("user") String user,
                                   @QueryParam("token") String token,
                                   @QueryParam("sudo") String sudo) throws Exception {
        try {
            if (!permissionsManager.adminAction(user, token, sudo)) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("insufficient privileges").build();
            }
            hostFailWorker.removeHostsForFailure(hostUuids);
            JSONObject json = new JSONObject();
            json.put("success", hostUuids.split(",").length);
            return Response.ok(json.toString()).build();
        } catch (Exception ex)  {
            log.warn("", ex);
            return Response.serverError().entity("Host Fail Error: " + ex.getMessage()).build();
        }
    }

    @GET
    @Path("/failinfo")
    @Produces(MediaType.APPLICATION_JSON)
    public Response hostFailInfo(@QueryParam("id") String hostUuids,
                                 @QueryParam("deadFs") boolean filesystemDead,
                                 @QueryParam("user") String user,
                                 @QueryParam("token") String token,
                                 @QueryParam("sudo") String sudo) throws Exception {
        try {
            if (!permissionsManager.adminAction(user, token, sudo)) {
                Response.status(Response.Status.UNAUTHORIZED).entity("insufficient privileges").build();
            }
            return Response.ok(spawn.getHostFailWorker().getInfoForHostFailure(hostUuids, filesystemDead).toString()).build();
        } catch (Exception ex)  {
            log.warn("", ex);
            return Response.serverError().entity("Host Fail Error: " + ex.getMessage()).build();
        }

    }

    @GET
    @Path("/drop")
    @Produces(MediaType.APPLICATION_JSON)
    public Response dropHosts(@QueryParam("id") String hostUuid,
                              @QueryParam("user") String user,
                              @QueryParam("token") String token,
                              @QueryParam("sudo") String sudo) throws Exception {
        try {
            if (!permissionsManager.adminAction(user, token, sudo)) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("insufficient privileges").build();
            }
            String[] hostUuids = hostUuid.split(",");
            for (String uuid : hostUuids) {
                emitLogLineForAction(user, "delete host on " + uuid);
                spawn.deleteHost(uuid);
            }
            return Response.ok().build();
        } catch (Exception ex)  {
            log.warn("", ex);
            return Response.serverError().entity("Host Drop Error: " + ex.getMessage()).build();
        }

    }

    @GET
    @Path("/toggle")
    @Produces(MediaType.APPLICATION_JSON)
    public Response enableHosts(@QueryParam("id") String hostUuid,
                                @QueryParam("user") String user,
                                @QueryParam("token") String token,
                                @QueryParam("sudo") String sudo,
                                @QueryParam("disable") boolean disable) throws Exception {
        try {
            if (!permissionsManager.adminAction(user, token, sudo)) {
                return Response.status(Response.Status.UNAUTHORIZED).entity("insufficient privileges").build();
            }
            emitLogLineForAction(user, "toggle hosts");
            spawn.toggleHosts(hostUuid, disable);
            return Response.ok().build();
        } catch (Exception ex)  {
            log.warn("", ex);
            return Response.serverError().entity("Host Disable Error: " + ex.getMessage()).build();
        }

    }

    @GET
    @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listHosts() {
        JSONArray hosts = new JSONArray();
        try {
            for (HostState host : spawn.hostManager.listHostStatus(null)) {
                hosts.put(spawn.getHostStateUpdateEvent(host));
            }
            return Response.ok(hosts.toString()).build();
        } catch (Exception ex)  {
            log.warn("", ex);
            return Response.serverError().entity(ex.toString()).build();
        }
    }

    private static void emitLogLineForAction(String user, String desc) {
        log.warn("User " + user + " initiated action: " + desc);
    }
}
