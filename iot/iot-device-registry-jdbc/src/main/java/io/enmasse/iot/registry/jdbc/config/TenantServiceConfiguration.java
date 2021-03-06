/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.registry.jdbc.config;

import org.eclipse.hono.service.management.tenant.TenantManagementHttpEndpoint;
import org.eclipse.hono.service.management.tenant.TenantManagementService;
import org.eclipse.hono.service.tenant.TenantAmqpEndpoint;
import org.eclipse.hono.service.tenant.TenantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;

@Configuration
public class TenantServiceConfiguration {

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Tenant</em> API.
     *
     * @return The handler.
     */
    @Autowired
    @Bean
    @ConditionalOnBean(TenantService.class)
    public TenantAmqpEndpoint tenantAmqpEndpoint(final Vertx vertx) {
        return new TenantAmqpEndpoint(vertx);
    }

    /**
     * Creates a new instance of an HTTP protocol handler for Hono's <em>Tenant</em> API.
     *
     * @return The handler.
     */
    @Autowired
    @Bean
    @ConditionalOnBean(TenantManagementService.class)
    public TenantManagementHttpEndpoint tenantHttpEndpoint(final Vertx vertx) {
        return new TenantManagementHttpEndpoint(vertx);
    }

}
