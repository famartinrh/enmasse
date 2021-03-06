/*
 * Copyright 2019-2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.registry.infinispan.config;

import org.eclipse.hono.deviceregistry.service.deviceconnection.MapBasedDeviceConnectionsConfigProperties;
import org.eclipse.hono.service.deviceconnection.DeviceConnectionAmqpEndpoint;
import org.eclipse.hono.service.deviceconnection.DeviceConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.vertx.core.Vertx;

@Configuration
//@Profile(PROFILE_DEVICE_CONNECTION)
//TODO - enable it again when https://github.com/EnMasseProject/enmasse/issues/4338 is implemented
public class DeviceConnectionServiceConfiguration {

    /**
     * Creates a new instance of an AMQP 1.0 protocol handler for Hono's <em>Device Connection</em> API.
     *
     * @return The handler.
     */
    @Autowired
    @Bean
    @ConditionalOnBean(DeviceConnectionService.class)
    public DeviceConnectionAmqpEndpoint deviceConnectionAmqpEndpoint(final Vertx vertx) {
        return new DeviceConnectionAmqpEndpoint(vertx);
    }

    @Bean
    //TODO - remove when https://github.com/EnMasseProject/enmasse/issues/4338 is implemented
    public MapBasedDeviceConnectionsConfigProperties deviceConnectionsProperties() {
        return new MapBasedDeviceConnectionsConfigProperties();
    }

}
