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

package org.eclipse.edc.connector.dataplane.api;

import org.eclipse.edc.connector.dataplane.api.controller.DataPlanePublicApiV2Controller;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;

import java.util.concurrent.Executors;

/**
 * This extension provides generic endpoints which are open to public participants of the Dataspace to execute
 * requests on the actual data source.
 */
@Extension(value = DataPlanePublicApiExtension.NAME)
public class DataPlanePublicApiExtension implements ServiceExtension {
    public static final String NAME = "Data Plane Public API";
    private static final int DEFAULT_PUBLIC_PORT = 8185;
    private static final String PUBLIC_API_CONFIG = "web.http.public";
    private static final String PUBLIC_CONTEXT_ALIAS = "public";
    private static final String PUBLIC_CONTEXT_PATH = "/api/v2/public";

    private static final int DEFAULT_THREAD_POOL = 10;

    private static final WebServiceSettings PUBLIC_SETTINGS = WebServiceSettings.Builder.newInstance()
            .apiConfigKey(PUBLIC_API_CONFIG)
            .contextAlias(PUBLIC_CONTEXT_ALIAS)
            .defaultPath(PUBLIC_CONTEXT_PATH)
            .defaultPort(DEFAULT_PUBLIC_PORT)
            .name(NAME)
            .build();

    @Inject
    private WebServer webServer;

    @Inject
    private WebServiceConfigurer webServiceConfigurer;

    @Inject
    private PipelineService pipelineService;

    @Inject
    private WebService webService;

    @Inject
    private ExecutorInstrumentation executorInstrumentation;
    @Inject
    private DataPlaneAuthorizationService authorizationService;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var configuration = webServiceConfigurer.configure(context, webServer, PUBLIC_SETTINGS);
        var executorService = executorInstrumentation.instrument(
                Executors.newFixedThreadPool(DEFAULT_THREAD_POOL),
                "Data plane proxy transfers"
        );
        var publicApiController = new DataPlanePublicApiV2Controller(pipelineService, executorService, authorizationService);
        webService.registerResource(configuration.getContextAlias(), publicApiController);
    }
}
