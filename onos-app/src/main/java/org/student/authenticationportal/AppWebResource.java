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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
@Path("/")
public class AppWebResource extends AbstractWebResource {

    private final Logger log = LoggerFactory.getLogger(AppComponent.class);
    private final String MACADDRESS = "macAddress";
    private static final String INVALID_JSON = "Invalid JSON data";
    private final AuthenticationHandler authenticationHandler = AuthenticationHandler.getInstance();

    /**
     * Get hello world greeting.
     *
     * @return 200 OK
     */
    @GET
    @Path("/")
    public Response getGreeting() {
        ObjectNode node = mapper().createObjectNode().put("hello", "world");
        return ok(node).build();
    }

    @GET
    @Path("/test")
    public Response getTest() {
        ObjectNode responseBody = new ObjectNode(JsonNodeFactory.instance);
        responseBody.put("message", "it works!");
        return Response.status(200).entity(responseBody).build();
    }

    /**
     * Authenticates a client.
     *
     * @param stream input JSON
     * @return 200 OK if the port state was set to the given value
     */
    @POST
    @Path("/authenticateClient")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response authenticateclient(InputStream stream) {
        try {
            ObjectNode root = readTreeFromStream(mapper(), stream);
            log.info("Recieved Request: " + root.toString());
            JsonNode node = root.get(MACADDRESS);
            if (!node.isMissingNode()) {
                log.info("Authenticating Client " + node.asText());
                authenticationHandler.authenticateClient(node.asText());
                return Response.ok().build();
            }
            throw new IllegalArgumentException(INVALID_JSON);
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
    }

}
