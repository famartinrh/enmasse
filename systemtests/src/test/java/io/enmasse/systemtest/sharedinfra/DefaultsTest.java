/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.systemtest.sharedinfra;

import io.enmasse.api.model.MessagingInfra;
import io.enmasse.api.model.MessagingInfraCondition;
import io.enmasse.api.model.MessagingTenant;
import io.enmasse.api.model.MessagingTenantCondition;
import io.enmasse.systemtest.TestTag;
import io.enmasse.systemtest.annotations.DefaultMessagingInfra;
import io.enmasse.systemtest.annotations.DefaultMessagingTenant;
import io.enmasse.systemtest.bases.TestBase;
import io.enmasse.systemtest.bases.isolated.ITestIsolatedSharedInfra;
import io.enmasse.systemtest.messaginginfra.resources.MessagingInfraResourceType;
import io.enmasse.systemtest.messaginginfra.resources.MessagingTenantResourceType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag(TestTag.ISOLATED_SHARED_INFRA)
public class DefaultsTest extends TestBase implements ITestIsolatedSharedInfra {

    @Test
    @DefaultMessagingInfra
    public void testDefaultInfra() {
        MessagingInfra infra = infraResourceManager.getDefaultInfra();

        MessagingInfraCondition condition = MessagingInfraResourceType.getCondition(infra.getStatus().getConditions(), "Ready");
        assertNotNull(condition);
        assertEquals("True", condition.getStatus());

        assertEquals(1, kubernetes.listPods(infra.getMetadata().getNamespace(), Map.of("component", "router")).size());
        assertEquals(1, kubernetes.listPods(infra.getMetadata().getNamespace(), Map.of("component", "broker")).size());
    }

    @Test
    @DefaultMessagingInfra
    @DefaultMessagingTenant
    public void testDefaultTenant() {
        MessagingInfra infra = infraResourceManager.getDefaultInfra();
        MessagingTenant tenant = infraResourceManager.getDefaultMessagingTenant();

        assertNotNull(tenant);
        MessagingTenantCondition condition = MessagingTenantResourceType.getCondition(tenant.getStatus().getConditions(), "Ready");
        assertNotNull(condition);
        assertEquals("True", condition.getStatus());
        assertEquals(infra.getMetadata().getName(), tenant.getStatus().getMessagingInfraRef().getName());
        assertEquals(infra.getMetadata().getNamespace(), tenant.getStatus().getMessagingInfraRef().getNamespace());
    }
}
