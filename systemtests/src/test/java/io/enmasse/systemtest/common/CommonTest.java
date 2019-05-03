/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.common;

import io.enmasse.address.model.Address;
import io.enmasse.address.model.AddressSpace;
import io.enmasse.address.model.AuthenticationServiceType;
import io.enmasse.systemtest.*;
import io.enmasse.systemtest.bases.TestBase;
import io.enmasse.systemtest.cmdclients.KubeCMDClient;
import io.enmasse.systemtest.utils.AddressSpaceUtils;
import io.enmasse.systemtest.utils.AddressUtils;
import io.enmasse.systemtest.utils.TestUtils;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.enmasse.systemtest.TestTag.isolated;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag(isolated)
class CommonTest extends TestBase {
    private static Logger log = CustomLogger.getLogger();

    @Test
    void testAccessLogs() throws Exception {
        AddressSpace standard = AddressSpaceUtils.createAddressSpaceObject("standard-addr-space-logs", AddressSpaceType.STANDARD, AuthenticationServiceType.STANDARD);
        createAddressSpace(standard);

        Address dest = AddressUtils.createQueueAddressObject("test-queue", DestinationPlan.STANDARD_SMALL_QUEUE);
        setAddresses(standard, dest);

        kubernetes.listPods().forEach(pod -> {
            kubernetes.getContainersFromPod(pod.getMetadata().getName()).forEach(container -> {
                log.info("Getting log from pod: {}, for container: {}", pod.getMetadata().getName(), container.getName());
                assertFalse(kubernetes.getLog(pod.getMetadata().getName(), container.getName()).isEmpty());
            });
        });
    }

    @Test
    void testRestartComponents() throws Exception {
        List<Label> labels = new LinkedList<>();
        labels.add(new Label("component", "api-server"));
        labels.add(new Label("name", "keycloak"));
        labels.add(new Label("name", "keycloak-controller"));
        labels.add(new Label("name", "address-space-controller"));

        UserCredentials user = new UserCredentials("frantisek", "dobrota");
        AddressSpace standard = AddressSpaceUtils.createAddressSpaceObject("addr-space-restart-standard", AddressSpaceType.STANDARD, AuthenticationServiceType.STANDARD);
        AddressSpace brokered = AddressSpaceUtils.createAddressSpaceObject("addr-space-restart-brokered", AddressSpaceType.BROKERED, AuthenticationServiceType.STANDARD);
        createAddressSpaceList(standard, brokered);
        createUser(brokered, user);
        createUser(standard, user);

        List<Address> brokeredAddresses = getAllBrokeredAddresses();
        List<Address> standardAddresses = getAllStandardAddresses();

        setAddresses(brokered, brokeredAddresses.toArray(new Address[0]));
        setAddresses(standard, standardAddresses.toArray(new Address[0]));

        assertCanConnect(brokered, user, brokeredAddresses);
        assertCanConnect(standard, user, standardAddresses);

        log.info("------------------------------------------------------------");
        log.info("------------------- Start with restating -------------------");
        log.info("------------------------------------------------------------");

        List<Pod> pods = kubernetes.listPods();
        int runningPodsBefore = pods.size();
        log.info("Number of running pods before restarting any: {}", runningPodsBefore);

        for (Label label : labels) {
            log.info("Restarting {}", label.labelValue);
            KubeCMDClient.deletePodByLabel(label.getLabelName(), label.getLabelValue());
            Thread.sleep(30_000);
            TestUtils.waitForExpectedReadyPods(kubernetes, runningPodsBefore, new TimeoutBudget(10, TimeUnit.MINUTES));
            assertSystemWorks(brokered, standard, user, brokeredAddresses, standardAddresses);
        }

        log.info("Restarting whole enmasse");
        KubeCMDClient.deletePodByLabel("app", kubernetes.getEnmasseAppLabel());
        Thread.sleep(180_000);
        TestUtils.waitForExpectedReadyPods(kubernetes, runningPodsBefore, new TimeoutBudget(10, TimeUnit.MINUTES));
        AddressUtils.waitForDestinationsReady(standard, new TimeoutBudget(10, TimeUnit.MINUTES),
                standardAddresses.toArray(new Address[0]));
        assertSystemWorks(brokered, standard, user, brokeredAddresses, standardAddresses);

        //TODO: Uncomment when #2127 will be fixedy

//        Pod qdrouter = pods.stream().filter(pod -> pod.getMetadata().getName().contains("qdrouter")).collect(Collectors.toList()).get(0);
//        kubernetes.deletePod(environment.namespace(), qdrouter.getMetadata().getName());
//        assertSystemWorks(brokered, standard, user, brokeredAddresses, standardAddresses);
    }

    @Test
    void testMonitoringTools() throws Exception {
        AddressSpace standard = AddressSpaceUtils.createAddressSpaceObject("standard-addr-space-monitor", AddressSpaceType.STANDARD, AuthenticationServiceType.STANDARD);
        createAddressSpace(standard);
        createUser(standard, new UserCredentials("jenda", "cenda"));
        setAddresses(standard, getAllStandardAddresses().toArray(new Address[0]));

        String qdRouterName = TestUtils.listRunningPods(kubernetes, standard).stream()
                .filter(pod -> pod.getMetadata().getName().contains("qdrouter"))
                .collect(Collectors.toList()).get(0).getMetadata().getName();
        assertTrue(KubeCMDClient.runQDstat(qdRouterName, "-c", "--sasl-username=jenda", "--sasl-password=cenda").getRetCode());
        assertTrue(KubeCMDClient.runQDstat(qdRouterName, "-a", "--sasl-username=jenda", "--sasl-password=cenda").getRetCode());
        assertTrue(KubeCMDClient.runQDstat(qdRouterName, "-l", "--sasl-username=jenda", "--sasl-password=cenda").getRetCode());
    }

    private void assertSystemWorks(AddressSpace brokered, AddressSpace standard, UserCredentials existingUser,
                                   List<Address> brAddresses, List<Address> stAddresses) throws Exception {
        log.info("Check if system works");
        assertCanConnect(standard, existingUser, stAddresses);
        assertCanConnect(brokered, existingUser, brAddresses);
        getAddressSpace(brokered.getMetadata().getName());
        getAddressSpace(standard.getMetadata().getName());
        createUser(brokered, new UserCredentials("jenda", "cenda"));
        createUser(standard, new UserCredentials("jura", "fura"));
    }

    private class Label {
        String labelName;
        String labelValue;

        Label(String labelName, String labelValue) {
            this.labelName = labelName;
            this.labelValue = labelValue;
        }

        String getLabelName() {
            return labelName;
        }

        String getLabelValue() {
            return labelValue;
        }
    }
}

