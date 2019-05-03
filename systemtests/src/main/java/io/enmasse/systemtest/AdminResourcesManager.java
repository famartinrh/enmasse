/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest;

import io.enmasse.admin.model.v1.*;
import io.enmasse.systemtest.utils.TestUtils;
import io.fabric8.kubernetes.api.model.Pod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class AdminResourcesManager {

    private static Logger log = CustomLogger.getLogger();
    private ArrayList<AddressPlan> addressPlans;
    private ArrayList<AddressSpacePlan> addressSpacePlans;
    private ArrayList<StandardInfraConfig> standardInfraConfigs;
    private ArrayList<BrokeredInfraConfig> brokeredInfraConfigs;
    private ArrayList<AuthenticationService> authServices;

    public void setUp() {
        addressPlans = new ArrayList<>();
        addressSpacePlans = new ArrayList<>();
        standardInfraConfigs = new ArrayList<>();
        brokeredInfraConfigs = new ArrayList<>();
        authServices = new ArrayList<>();
    }

    public void tearDown() throws Exception {
        if (!Environment.getInstance().skipCleanup()) {
            for (AddressSpacePlan addressSpacePlan : addressSpacePlans) {
                Kubernetes.getInstance().getAddressSpacePlanClient().delete(addressSpacePlan);
            }

            for (AddressPlan addressPlan : addressPlans) {
                Kubernetes.getInstance().getAddressPlanClient().delete(addressPlan);
            }

            for (StandardInfraConfig infraConfigDefinition : standardInfraConfigs) {
                Kubernetes.getInstance().getStandardInfraConfigClient().delete(infraConfigDefinition);
            }

            for (BrokeredInfraConfig infraConfigDefinition : brokeredInfraConfigs) {
                Kubernetes.getInstance().getBrokeredInfraConfigClient().delete(infraConfigDefinition);
            }

            for (AuthenticationService authService : authServices) {
                Kubernetes.getInstance().getAuthenticationServiceClient().delete(authService);
            }

            addressPlans.clear();
            addressSpacePlans.clear();
        }
    }

    //------------------------------------------------------------------------------------------------
    // Address plans
    //------------------------------------------------------------------------------------------------

    public void createAddressPlan(AddressPlan addressPlan) throws Exception {
        createAddressPlan(addressPlan, false);
    }

    public void createAddressPlan(AddressPlan addressPlan, boolean replaceExisting) throws Exception {
        var client = Kubernetes.getInstance().getAddressPlanClient();
        if (replaceExisting) {
            client.createOrReplace(addressPlan);
        } else {
            client.create(addressPlan);
        }
        addressPlans.add(addressPlan);
    }

    public void removeAddressPlan(AddressPlan addressPlan) throws Exception {
        Kubernetes.getInstance().getAddressPlanClient().delete(addressPlan);
        addressPlans.removeIf(addressPlanIter -> addressPlanIter.getMetadata().getName().equals(addressPlan.getMetadata().getName()));
    }

    public void replaceAddressPlan(AddressPlan plan) throws Exception {
        Kubernetes.getInstance().getAddressPlanClient().createOrReplace(plan);
    }

    public AddressPlan getAddressPlan(String name) throws Exception {
        return Kubernetes.getInstance().getAddressPlanClient().withName(name).get();
    }

    //------------------------------------------------------------------------------------------------
    // Address space plans
    //------------------------------------------------------------------------------------------------

    public void createAddressSpacePlan(AddressSpacePlan addressSpacePlan) throws Exception {
        createAddressSpacePlan(addressSpacePlan, false);
    }

    public void createAddressSpacePlan(AddressSpacePlan addressSpacePlan, boolean replaceExisting) throws Exception {
        var client = Kubernetes.getInstance().getAddressSpacePlanClient();
        if (replaceExisting) {
            client.createOrReplace(addressSpacePlan);
        } else {
            client.create(addressSpacePlan);
        }
        addressSpacePlans.add(addressSpacePlan);
    }

    public void removeAddressSpacePlan(AddressSpacePlan addressSpacePlan) throws Exception {
        Kubernetes.getInstance().getAddressSpacePlanClient().delete(addressSpacePlan);
        addressSpacePlans.removeIf(spacePlanIter -> spacePlanIter.getMetadata().getName().equals(addressSpacePlan.getMetadata().getName()));
    }

    public AddressSpacePlan getAddressSpacePlan(String config) throws Exception {
        return Kubernetes.getInstance().getAddressSpacePlanClient().withName(config).get();
    }

    //------------------------------------------------------------------------------------------------
    // Infra configs
    //------------------------------------------------------------------------------------------------

    public BrokeredInfraConfig getBrokeredInfraConfig(String name) throws Exception {
        return Kubernetes.getInstance().getBrokeredInfraConfigClient().withName(name).get();
    }

    public StandardInfraConfig getStandardInfraConfig(String name) throws Exception {
        return Kubernetes.getInstance().getStandardInfraConfigClient().withName(name).get();
    }

    public void createInfraConfig(InfraConfig infraConfigDefinition) throws Exception {
        if (infraConfigDefinition.getKind().equals("StandardInfraConfig")) {
            var client = Kubernetes.getInstance().getStandardInfraConfigClient();
            client.createOrReplace((StandardInfraConfig) infraConfigDefinition);
            standardInfraConfigs.add((StandardInfraConfig) infraConfigDefinition);
        } else {
            var client = Kubernetes.getInstance().getBrokeredInfraConfigClient();
            client.createOrReplace((BrokeredInfraConfig) infraConfigDefinition);
            brokeredInfraConfigs.add((BrokeredInfraConfig) infraConfigDefinition);
        }
    }

    public void removeInfraConfig(InfraConfig infraConfigDefinition) throws Exception {
        if (infraConfigDefinition.getKind().equals("StandardInfraConfig")) {
            var client = Kubernetes.getInstance().getStandardInfraConfigClient();
            client.delete((StandardInfraConfig) infraConfigDefinition);
            standardInfraConfigs.removeIf(infraId -> infraId.getMetadata().getName().equals(infraConfigDefinition.getMetadata().getName()));
        } else {
            var client = Kubernetes.getInstance().getBrokeredInfraConfigClient();
            client.delete((BrokeredInfraConfig) infraConfigDefinition);
            brokeredInfraConfigs.removeIf(infraId -> infraId.getMetadata().getName().equals(infraConfigDefinition.getMetadata().getName()));
        }
    }

    //------------------------------------------------------------------------------------------------
    // Authentication services
    //------------------------------------------------------------------------------------------------

    public AuthenticationService getAuthService(String name) throws Exception {
        return Kubernetes.getInstance().getAuthenticationServiceClient().withName(name).get();
    }

    public void createAuthService(AuthenticationService authService) throws Exception {
        createAuthService(authService, false);
    }

    public void createAuthService(AuthenticationService authenticationService, boolean replaceExisting) throws Exception {
        var client = Kubernetes.getInstance().getAuthenticationServiceClient();
        if (replaceExisting) {
            client.createOrReplace(authenticationService);
        } else {
            client.create(authenticationService);
            authServices.add(authenticationService);
        }
        String desiredPodName = authenticationService.getMetadata().getName();
        TestUtils.waitUntilCondition("Auth service is deployed: " + desiredPodName, phase -> {
                    List<Pod> pods = TestUtils.listReadyPods(Kubernetes.getInstance());
                    long matching = pods.stream().filter(pod ->
                            pod.getMetadata().getName().contains(desiredPodName)).count();
                    if (matching != 1) {
                        List<String> podNames = pods.stream().map(p -> p.getMetadata().getName()).collect(Collectors.toList());
                        log.info("Still awaiting pod with name : {}, matching : {}, current pods  {}",
                                desiredPodName, matching, podNames);
                    }

                    return matching == 1;
                },
                new TimeoutBudget(5, TimeUnit.MINUTES));
    }

    public void removeAuthService(AuthenticationService authService) throws Exception {
        Kubernetes.getInstance().getAuthenticationServiceClient().delete(authService);
        authServices.removeIf(authserviceId -> authserviceId.getMetadata().getName().equals(authService.getMetadata().getName()));
        TestUtils.waitUntilCondition("Auth service is deleted: " + authService.getMetadata().getName(), (phase) ->
                        TestUtils.listReadyPods(Kubernetes.getInstance()).stream().noneMatch(pod ->
                                pod.getMetadata().getName().contains(authService.getMetadata().getName())),
                new TimeoutBudget(5, TimeUnit.MINUTES));
    }
}
