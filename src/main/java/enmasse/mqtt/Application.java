/*
 * Copyright 2016 Red Hat Inc.
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

package enmasse.mqtt;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EnMasse MQTT frontend main application class
 */
@SpringBootApplication // same as using @Configuration, @EnableAutoConfiguration and @ComponentScan
public class Application {

    private static Logger LOG = LoggerFactory.getLogger(Application.class);

    private final Vertx vertx = Vertx.vertx();

    @Value(value = "${enmasse.mqtt.maxinstances:1}")
    private int maxInstances;
    @Value(value = "${enmasse.mqtt.startuptimeout:20}")
    private int startupTimeout;
    @Autowired
    private MqttFrontend mqttFrontend;

    private AtomicBoolean running = new AtomicBoolean();

    @PostConstruct
    public void registerVerticles() {

        if (this.running.compareAndSet(false, true)) {

            // instance count is upper bounded to the number of available processors
            int instanceCount =
                    (this.maxInstances > 0 && this.maxInstances < Runtime.getRuntime().availableProcessors()) ?
                            this.maxInstances :
                            Runtime.getRuntime().availableProcessors();


            try {

                CountDownLatch latch = new CountDownLatch(1);

                Future<Void> startFuture = Future.future();
                startFuture.setHandler(done -> {
                    if (done.succeeded()) {
                        latch.countDown();
                    } else {
                        LOG.error("Could not start MQTT frontend", done.cause());
                    }
                });

                // start deploying more verticle instances
                this.deployVerticles(instanceCount, startFuture);

                // wait for deploying end
                if (latch.await(this.startupTimeout, TimeUnit.SECONDS)) {
                    LOG.info("MQTT frontend startup completed successfully");
                } else {
                    LOG.error("Startup timed out after {} seconds, shutting down ...", this.startupTimeout);
                    // TODO: shutdown
                }

            } catch (InterruptedException e) {

                LOG.error("Startup process has been interrupted, shutting down ...");
                // TODO: shutdown
            }
        }
    }

    /**
     * Execute verticles deploy operation
     *
     * @param instanceCount     number of verticle instances to deploy
     * @param resultHandler     handler called when the deploy ends
     */
    private void deployVerticles(int instanceCount, Future<Void> resultHandler) {

        LOG.debug("Starting up {} instances of MQTT frontend verticle", instanceCount);

        List<Future> results = new ArrayList<>();

        for (int i = 1; i <= instanceCount; i++) {

            int instanceId = i;
            Future<Void> result = Future.future();
            results.add(result);

            this.vertx.deployVerticle(this.mqttFrontend, done -> {
                if (done.succeeded()) {
                    LOG.debug("Verticle instance {} deployed [{}]", instanceId, done.result());
                    result.complete();
                } else {
                    LOG.debug("Failed to deploy verticle instance {}", instanceId, done.cause());
                    result.fail(done.cause());
                }
            });
        }

        // combine all futures related to verticle instances deploy
        CompositeFuture.all(results).setHandler(done -> {
            if (done.succeeded()) {
                resultHandler.complete();
            } else {
                resultHandler.fail(done.cause());
            }
        });
    }

    @PreDestroy
    public void shutdown() {

    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
