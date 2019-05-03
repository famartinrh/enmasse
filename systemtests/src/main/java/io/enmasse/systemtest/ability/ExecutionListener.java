/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.ability;

import io.enmasse.systemtest.CustomLogger;
import io.enmasse.systemtest.Environment;
import io.enmasse.systemtest.GlobalLogCollector;
import io.enmasse.systemtest.Kubernetes;
import io.enmasse.systemtest.apiclients.IoTConfigApiClient;
import io.enmasse.systemtest.apiclients.IoTProjectApiClient;
import io.enmasse.systemtest.timemeasuring.TimeMeasuringSystem;
import io.enmasse.systemtest.utils.AddressSpaceUtils;
import io.enmasse.systemtest.utils.IoTUtils;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;

import java.io.File;


public class ExecutionListener implements TestExecutionListener {
    private static final Logger log = CustomLogger.getLogger();

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        Environment env = Environment.getInstance();
        if (!env.skipCleanup()) {
            Kubernetes kube = Kubernetes.getInstance();
            GlobalLogCollector logCollector = new GlobalLogCollector(kube, new File(env.testLogDir()));
            try {
                kube.getAddressSpaceClient().inAnyNamespace().list().getItems().forEach((addrSpace) -> {
                    log.info("address space '{}' will be removed", addrSpace);
                    try {
                        AddressSpaceUtils.deleteAddressSpaceAndWait(addrSpace, logCollector);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (IoTUtils.isIoTInstalled(kube)) {
                try {
                    IoTProjectApiClient iotProjectClient = new IoTProjectApiClient(kube);
                    iotProjectClient.listAllIoTProjects().forEach(project -> {
                        log.info("iot project '{}' will be removed", project.getMetadata().getName());
                        String projectNamespace = project.getMetadata().getNamespace();
                        try {
                            IoTUtils.deleteIoTProjectAndWait(new IoTProjectApiClient(kube, projectNamespace), project);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    IoTConfigApiClient iotConfigClient = new IoTConfigApiClient(kube);
                    iotConfigClient.listIoTConfigs().forEach(config -> {
                        log.info("iot config '{}' will be removed", config.getMetadata().getName());
                        try {
                            iotConfigClient.deleteIoTConfig(config.getMetadata().getName());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            log.warn("Remove address spaces when test run finished - SKIPPED!");
        }
        TimeMeasuringSystem.printAndSaveResults();
    }
}
