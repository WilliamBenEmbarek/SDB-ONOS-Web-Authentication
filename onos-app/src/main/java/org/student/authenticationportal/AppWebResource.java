/*
 * Copyright 2021-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.student.authenticationportal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.rest.AbstractWebResource;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;

import static org.onlab.util.Tools.nullIsNotFound;
import static org.onlab.util.Tools.readTreeFromStream;
import static org.onosproject.net.DeviceId.deviceId;

/**
 * Sample web resource.
 */
@Path("sample")
public class AppWebResource extends AbstractWebResource {

    private final String AUTHENTICATED = "authenticated";
    private static final String INVALID_JSON = "Invalid JSON data";
    private final AuthenticationHandler authenticationHandler = AuthenticationHandler.getInstance();

    /**
     * Get hello world greeting.
     *
     * @return 200 OK
     */
    @GET
    @Path("")
    public Response getGreeting() {
        ObjectNode node = mapper().createObjectNode().put("hello", "world");
        return ok(node).build();
    }

    /**
     * Authenticates a client.
     *
     * @onos.rsModel auth
     * @param id device identifier
     * @param stream input JSON
     * @return 200 OK if the port state was set to the given value
     */
    @POST
    @Path("authenticateClient/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticateclient(@PathParam("id") String id, InputStream stream) {
        try {
            ObjectNode root = readTreeFromStream(mapper(), stream);
            JsonNode node = root.path(AUTHENTICATED);
            if (!node.isMissingNode()) {
                authenticationHandler.authenticateClient(id);
                return Response.ok().build();
            }

            throw new IllegalArgumentException(INVALID_JSON);
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
    }

}
