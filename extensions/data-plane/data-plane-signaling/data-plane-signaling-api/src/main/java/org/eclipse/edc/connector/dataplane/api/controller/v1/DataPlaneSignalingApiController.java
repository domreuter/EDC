/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.connector.dataplane.api.controller.v1;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.connector.dataplane.spi.manager.DataPlaneManager;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowSuspendMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowTerminateMessage;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.exception.InvalidRequestException;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;

@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Path("/v1/dataflows")
public class DataPlaneSignalingApiController implements DataPlaneSignalingApi {

    private final TypeTransformerRegistry typeTransformerRegistry;
    private final DataPlaneAuthorizationService dataPlaneAuthorizationService;
    private final DataPlaneManager dataPlaneManager;
    private final Monitor monitor;

    public DataPlaneSignalingApiController(TypeTransformerRegistry typeTransformerRegistry, DataPlaneAuthorizationService dataPlaneAuthorizationService, DataPlaneManager dataPlaneManager, Monitor monitor) {
        this.typeTransformerRegistry = typeTransformerRegistry;
        this.dataPlaneAuthorizationService = dataPlaneAuthorizationService;
        this.dataPlaneManager = dataPlaneManager;
        this.monitor = monitor;
    }

    @POST
    @Override
    public JsonObject start(JsonObject dataFlowStartMessage) {
        var startMsg = typeTransformerRegistry.transform(dataFlowStartMessage, DataFlowStartMessage.class)
                .onFailure(f -> monitor.warning("Error transforming %s: %s".formatted(DataFlowStartMessage.class, f.getFailureDetail())))
                .orElseThrow(InvalidRequestException::new);

        dataPlaneManager.validate(startMsg)
                .onFailure(f -> monitor.warning("Failed to validate request: %s".formatted(f.getFailureDetail())))
                .orElseThrow(f -> f.getMessages().isEmpty() ?
                        new InvalidRequestException("Failed to validate request: %s".formatted(startMsg.getId())) :
                        new InvalidRequestException(f.getMessages()));

        monitor.debug("Create EDR");
        var dataAddress = dataPlaneAuthorizationService.createEndpointDataReference(startMsg)
                .onFailure(f -> monitor.warning("Error obtaining EDR DataAddress: %s".formatted(f.getFailureDetail())))
                .orElseThrow(InvalidRequestException::new);

        dataPlaneManager.initiate(startMsg);

        return typeTransformerRegistry.transform(dataAddress, JsonObject.class)
                .onFailure(f -> monitor.warning("Error obtaining EDR DataAddress: %s".formatted(f.getFailureDetail())))
                .orElseThrow(f -> new EdcException(f.getFailureDetail()));
    }

    @GET
    @Path("/{id}/state")
    @Override
    public JsonObject getTransferState(@PathParam("id") String transferProcessId) {
        var state = dataPlaneManager.getTransferState(transferProcessId);
        // not really worth to create a dedicated transformer for this simple object

        return Json.createObjectBuilder()
                .add(TYPE, "DataFlowState")
                .add(EDC_NAMESPACE + "state", state.toString())
                .build();
    }

    @POST
    @Path("/{id}/terminate")
    @Override
    public void terminate(@PathParam("id") String dataFlowId, JsonObject terminationMessage) {

        var msg = typeTransformerRegistry.transform(terminationMessage, DataFlowTerminateMessage.class)
                .onFailure(f -> monitor.warning("Error transforming %s: %s".formatted(DataFlowTerminateMessage.class, f.getFailureDetail())))
                .orElseThrow(InvalidRequestException::new);

        dataPlaneManager.terminate(dataFlowId, msg.getReason())
                .orElseThrow(InvalidRequestException::new);
    }

    @POST
    @Path("/{id}/suspend")
    @Override
    public void suspend(@PathParam("id") String id, JsonObject suspendMessage) {
        var msg = typeTransformerRegistry.transform(suspendMessage, DataFlowSuspendMessage.class)
                .onFailure(f -> monitor.warning("Error transforming %s: %s".formatted(DataFlowSuspendMessage.class, f.getFailureDetail())))
                .orElseThrow(InvalidRequestException::new);

        monitor.warning(" >>> A valid DataFlowSuspendMessage was provided, but suspension messages are not yet supported, " +
                "and will depend on https://github.com/eclipse-edc/Connector/issues/3350.");
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
