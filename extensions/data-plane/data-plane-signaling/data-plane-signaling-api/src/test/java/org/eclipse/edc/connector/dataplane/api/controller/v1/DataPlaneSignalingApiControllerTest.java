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

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import org.eclipse.edc.connector.dataplane.spi.DataFlowStates;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.connector.dataplane.spi.manager.DataPlaneManager;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowSuspendMessage;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowTerminateMessage;
import org.eclipse.edc.spi.types.domain.transfer.FlowType;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.jersey.testfixtures.RestControllerTestBase;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.result.Result.failure;
import static org.eclipse.edc.spi.result.Result.success;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class DataPlaneSignalingApiControllerTest extends RestControllerTestBase {

    private final TypeTransformerRegistry transformerRegistry = mock();
    private final DataPlaneAuthorizationService authService = mock();
    private final DataPlaneManager dataplaneManager = mock();

    @DisplayName("Expect HTTP 200 and the correct EDR when a data flow is started")
    @Test
    void start() {
        var flowStartMessage = createFlowStartMessage();
        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowStartMessage.class)))
                .thenReturn(success(flowStartMessage));
        when(dataplaneManager.validate(any())).thenReturn(success(true));
        when(authService.createEndpointDataReference(any()))
                .thenReturn(success(DataAddress.Builder.newInstance().type("test-edr").build()));

        when(transformerRegistry.transform(isA(DataAddress.class), eq(JsonObject.class)))
                .thenReturn(success(Json.createObjectBuilder().add("foo", "bar").build()));

        var jsonObject = Json.createObjectBuilder().build();
        var result = baseRequest()
                .contentType(ContentType.JSON)
                .body(jsonObject)
                .post("/v1/dataflows")
                .then()
                .statusCode(200)
                .extract().body().as(JsonObject.class);

        assertThat(result).hasEntrySatisfying("foo", val -> assertThat(((JsonString) val).getString()).isEqualTo("bar"));
        verify(dataplaneManager).initiate(eq(flowStartMessage));
    }

    @DisplayName("Expect HTTP 400 when DataFlowStartMessage is invalid")
    @Test
    void start_whenInvalidMessage() {
        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowStartMessage.class)))
                .thenReturn(success(createFlowStartMessage()));
        when(dataplaneManager.validate(any())).thenReturn(Result.failure("foobar"));

        var jsonObject = Json.createObjectBuilder().build();
        baseRequest()
                .contentType(ContentType.JSON)
                .body(jsonObject)
                .post("/v1/dataflows")
                .then()
                .statusCode(400);

        verify(transformerRegistry).transform(isA(JsonObject.class), eq(DataFlowStartMessage.class));
        verify(dataplaneManager).validate(any(DataFlowStartMessage.class));
        verifyNoMoreInteractions(transformerRegistry, dataplaneManager, authService);

    }

    @DisplayName("Expect HTTP 400 when DataFlowStartMessage cannot be deserialized")
    @Test
    void start_whenTransformationFails() {
        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowStartMessage.class))).thenReturn(Result.failure("foo-bar"));

        var jsonObject = Json.createObjectBuilder().build();
        baseRequest()
                .contentType(ContentType.JSON)
                .body(jsonObject)
                .post("/v1/dataflows")
                .then()
                .statusCode(400);

        verify(transformerRegistry).transform(isA(JsonObject.class), eq(DataFlowStartMessage.class));
        verifyNoMoreInteractions(transformerRegistry, dataplaneManager, authService);
    }

    @DisplayName("Expect HTTP 400 when an EDR cannot be created")
    @Test
    void start_whenCreateEdrFails() {
        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowStartMessage.class)))
                .thenReturn(success(createFlowStartMessage()));
        when(dataplaneManager.validate(any())).thenReturn(success(true));
        when(authService.createEndpointDataReference(any()))
                .thenReturn(Result.failure("test-failure"));

        var jsonObject = Json.createObjectBuilder().build();
        baseRequest()
                .contentType(ContentType.JSON)
                .body(jsonObject)
                .post("/v1/dataflows")
                .then()
                .statusCode(400);

        verify(transformerRegistry).transform(isA(JsonObject.class), eq(DataFlowStartMessage.class));
        verify(dataplaneManager).validate(any(DataFlowStartMessage.class));
        verify(authService).createEndpointDataReference(any());
        verifyNoMoreInteractions(transformerRegistry, dataplaneManager, authService);
    }

    @DisplayName("Expect HTTP 500 when the DataAddress cannot be serialized on egress")
    @Test
    void start_whenDataAddressTransformationFails() {
        var flowStartMessage = createFlowStartMessage();
        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowStartMessage.class)))
                .thenReturn(success(flowStartMessage));
        when(dataplaneManager.validate(any())).thenReturn(success(true));
        when(authService.createEndpointDataReference(any()))
                .thenReturn(success(DataAddress.Builder.newInstance().type("test-edr").build()));

        when(transformerRegistry.transform(isA(DataAddress.class), eq(JsonObject.class)))
                .thenReturn(failure("test-failure"));

        var jsonObject = Json.createObjectBuilder().build();
        var result = baseRequest()
                .contentType(ContentType.JSON)
                .body(jsonObject)
                .post("/v1/dataflows")
                .then()
                .statusCode(500);

        verify(dataplaneManager).initiate(eq(flowStartMessage));
    }

    @DisplayName("Expect HTTP 200 and the correct response when getting the state")
    @Test
    void getTransferState() {
        var flowId = "test-id";
        when(dataplaneManager.getTransferState(eq(flowId))).thenReturn(DataFlowStates.RECEIVED);

        var state = baseRequest()
                .get("/v1/dataflows/%s/state".formatted(flowId))
                .then()
                .statusCode(200)
                .extract().as(JsonObject.class);
        assertThat(state.getString(EDC_NAMESPACE + "state")).isEqualTo("RECEIVED");
    }

    @DisplayName("Expect HTTP 204 when DataFlow is terminated successfully")
    @Test
    void terminate() {
        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowTerminateMessage.class)))
                .thenReturn(success(DataFlowTerminateMessage.Builder.newInstance().reason("test-reason").build()));
        var flowId = "test-id";
        when(dataplaneManager.terminate(eq(flowId), any())).thenReturn(StatusResult.success());

        var jsonObject = Json.createObjectBuilder().build();
        baseRequest()
                .contentType(ContentType.JSON)
                .body(jsonObject)
                .post("/v1/dataflows/%s/terminate".formatted(flowId))
                .then()
                .statusCode(204);
    }

    @DisplayName("Expect HTTP 400 when DataFlow is cannot be terminated")
    @Test
    void terminate_whenCannotTerminate() {
        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowTerminateMessage.class)))
                .thenReturn(success(DataFlowTerminateMessage.Builder.newInstance().reason("test-reason").build()));
        var flowId = "test-id";
        when(dataplaneManager.terminate(eq(flowId), any())).thenReturn(StatusResult.failure(ResponseStatus.FATAL_ERROR));

        var jsonObject = Json.createObjectBuilder().build();
        baseRequest()
                .contentType(ContentType.JSON)
                .body(jsonObject)
                .post("/v1/dataflows/%s/terminate".formatted(flowId))
                .then()
                .statusCode(400);
    }

    @DisplayName("Expect HTTP 501 because suspension messages are not yet supported")
    @Test
    void suspend() {
        var flowId = "test-id";
        when(transformerRegistry.transform(isA(JsonObject.class), eq(DataFlowSuspendMessage.class))).thenReturn(success(DataFlowSuspendMessage.Builder.newInstance()
                .reason("foo-reaset")
                .build()));

        var jsonObject = Json.createObjectBuilder().build();
        baseRequest()
                .contentType(ContentType.JSON)
                .body(jsonObject)
                .post("/v1/dataflows/%s/suspend".formatted(flowId))
                .then()
                .statusCode(501)
                .body(Matchers.containsString("Not Implemented"));

    }

    @Override
    protected Object controller() {
        return new DataPlaneSignalingApiController(transformerRegistry, authService, dataplaneManager, mock());
    }

    private DataFlowStartMessage createFlowStartMessage() {
        return DataFlowStartMessage.Builder.newInstance()
                .processId("processId")
                .assetId("assetId")
                .agreementId("agreementId")
                .participantId("participantId")
                .flowType(FlowType.PUSH)
                .callbackAddress(URI.create("http://localhost"))
                .sourceDataAddress(DataAddress.Builder.newInstance().type("sourceType").build())
                .destinationDataAddress(DataAddress.Builder.newInstance().type("destType").build())
                .build();
    }

    private RequestSpecification baseRequest() {
        return given()
                .baseUri("http://localhost:" + port)
                .when();
    }
}