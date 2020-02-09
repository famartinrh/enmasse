/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.controller;

import io.enmasse.address.model.*;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.vertx.core.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class ExportsController implements Controller {
    private static final Logger log = LoggerFactory.getLogger(ExportsController.class.getName());

    private final KubernetesClient client;

    public ExportsController(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public AddressSpace reconcileActive(AddressSpace addressSpace) {
        try {
            Map<String, List<ExportSpec>> exportsMap = new HashMap<>();
            for (EndpointSpec endpointSpec : addressSpace.getSpec().getEndpoints()) {
                if (endpointSpec.getExports() != null) {
                    exportsMap.put(endpointSpec.getName(), endpointSpec.getExports());
                }
            }

            for (EndpointStatus endpointStatus : addressSpace.getStatus().getEndpointStatuses()) {
                List<ExportSpec> exports = exportsMap.get(endpointStatus.getName());
                if (exports != null) {
                    for (ExportSpec export : exports) {
                        switch (export.getKind()) {
                            case Secret:
                                exportAsSecret(export.getName(), endpointStatus, addressSpace);
                                break;
                            case ConfigMap:
                                exportAsConfigMap(export, endpointStatus, addressSpace);
                                break;
                            case Service:
                                exportAsService(export.getName(), endpointStatus, addressSpace);
                                break;
                            default:
                                log.info("Unknown export kind {} for address space {}, ignoring", export.getKind(), addressSpace.getMetadata().getName());
                                break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error exporting endpoints for address space {}", addressSpace.getMetadata().getName(), e);
        }
        return addressSpace;
    }

    private void exportAsSecret(String name, EndpointStatus endpointStatus, AddressSpace addressSpace) {
        Map<String, String> exportMap = buildExportMap(addressSpace.getStatus(), endpointStatus);
        Secret secret = new SecretBuilder()
                .editOrNewMetadata()
                .withName(name)
                .withNamespace(addressSpace.getMetadata().getNamespace())
                .addToOwnerReferences(new OwnerReferenceBuilder()
                        .withBlockOwnerDeletion(false)
                        .withApiVersion(addressSpace.getApiVersion())
                        .withController(true)
                        .withKind(addressSpace.getKind())
                        .withName(addressSpace.getMetadata().getName())
                        .withUid(addressSpace.getMetadata().getUid())
                        .build())
                .endMetadata()
                .addToStringData(exportMap)
                .build();

        Secret existing = client.secrets().inNamespace(addressSpace.getMetadata().getNamespace()).withName(name).get();
        if (existing != null) {
            Map<String, String> decodedExportMap = decodeExportMap(existing.getData());
            if (!decodedExportMap.equals(exportMap)) {
                client.secrets().inNamespace(addressSpace.getMetadata().getNamespace()).withName(name).replace(secret);
            }
        } else {
            client.secrets().inNamespace(addressSpace.getMetadata().getNamespace()).withName(name).createOrReplace(secret);
        }
    }

    private void exportAsConfigMap(ExportSpec export, EndpointStatus endpointStatus, AddressSpace addressSpace) {
		String name = export.getName();
		var mapBuilder = new ConfigMapBuilder()
                .editOrNewMetadata()
                .withName(name)
                .withNamespace(addressSpace.getMetadata().getNamespace())
                .addToOwnerReferences(new OwnerReferenceBuilder()
                        .withBlockOwnerDeletion(false)
                        .withApiVersion(addressSpace.getApiVersion())
                        .withController(true)
                        .withKind(addressSpace.getKind())
                        .withName(addressSpace.getMetadata().getName())
                        .withUid(addressSpace.getMetadata().getUid())
                        .build())
                .endMetadata();

		ConfigMap configMap;
		if (export.getFormat()!=null && export.getFormat()==ExportFormat.json) {
			configMap = mapBuilder
					.addToBinaryData("connect.json", Base64.getEncoder().encodeToString(buildExportJson(addressSpace.getStatus(), endpointStatus).getBytes()))
					.build();
		} else {
			configMap = mapBuilder
					.addToData(buildExportMap(addressSpace.getStatus(), endpointStatus))
					.build();
		}

        ConfigMap existing = client.configMaps().inNamespace(addressSpace.getMetadata().getNamespace()).withName(name).get();
        if (existing != null && ( !configMap.getData().equals(existing.getData()) || !configMap.getBinaryData().equals(existing.getBinaryData())) ) {
            client.configMaps().inNamespace(addressSpace.getMetadata().getNamespace()).withName(name).replace(configMap);
        } else {
            client.configMaps().inNamespace(addressSpace.getMetadata().getNamespace()).withName(name).createOrReplace(configMap);
        }
    }

    private void exportAsService(String name, EndpointStatus endpointStatus, AddressSpace addressSpace) {
        Service service = new ServiceBuilder()
                .editOrNewMetadata()
                .withName(name)
                .withNamespace(addressSpace.getMetadata().getNamespace())
                .addToOwnerReferences(new OwnerReferenceBuilder()
                        .withBlockOwnerDeletion(false)
                        .withApiVersion(addressSpace.getApiVersion())
                        .withController(true)
                        .withKind(addressSpace.getKind())
                        .withName(addressSpace.getMetadata().getName())
                        .withUid(addressSpace.getMetadata().getUid())
                        .build())
                .endMetadata()
                .editOrNewSpec()
                .withType("ExternalName")
                .withExternalName(endpointStatus.getServiceHost() + ".cluster.local")
                .withPorts(ServiceHelper.toServicePortList(endpointStatus.getServicePorts()))
                .endSpec()
                .build();

        Service existing = client.services().inNamespace(addressSpace.getMetadata().getNamespace()).withName(name).get();
        if (existing != null &&
                (!endpointStatus.getServiceHost().equals(existing.getSpec().getExternalName())
                        || !endpointStatus.getServicePorts().equals(ServiceHelper.fromServicePortList(existing.getSpec().getPorts())))) {
            client.services().inNamespace(addressSpace.getMetadata().getNamespace()).withName(name).replace(service);
        } else {
            client.services().inNamespace(addressSpace.getMetadata().getNamespace()).withName(name).createOrReplace(service);
        }
    }

    private static Map<String, String> buildExportMap(AddressSpaceStatus addressSpaceStatus, EndpointStatus endpointStatus) {
        Map<String, String> map = new HashMap<>();
        map.put("service.host", endpointStatus.getServiceHost());
        for (Map.Entry<String, Integer> portEntry : endpointStatus.getServicePorts().entrySet()) {
            map.put("service.port." + portEntry.getKey(), String.valueOf(portEntry.getValue()));
        }
        if (endpointStatus.getExternalHost() != null) {
            map.put("external.host", endpointStatus.getExternalHost());
        }
        for (Map.Entry<String, Integer> portEntry : endpointStatus.getExternalPorts().entrySet()) {
            map.put("external.port." + portEntry.getKey(), String.valueOf(portEntry.getValue()));
        }

        if (addressSpaceStatus.getCaCert() != null) {
            map.put("ca.crt", addressSpaceStatus.getCaCert());
        }
        return map;
    }

    private static String buildExportJson(AddressSpaceStatus addressSpaceStatus, EndpointStatus endpointStatus) {
        JsonObject json = new JsonObject();

        if ( endpointStatus.getExternalHost() != null ) {
            // amqps set up
            json.put("host", endpointStatus.getExternalHost());
            json.put("port", endpointStatus.getExternalPorts().getOrDefault("amqps", 443));
            json.put("scheme", "amqps");
            JsonObject tls = new JsonObject();
            if ( endpointStatus.getCert() != null ) {
                tls.put("cert", endpointStatus.getCert());
            }
            if ( addressSpaceStatus.getCaCert() != null ) {
                tls.put("ca", addressSpaceStatus.getCaCert());
            }
            tls.put("verify", true);
            json.put("tls", tls);
        } else {
            // amqp set up
            json.put("host", endpointStatus.getServiceHost());
            json.put("port", endpointStatus.getServicePorts().getOrDefault("amqp", 5672));
            json.put("tls", new JsonObject().put("verify", false));
        }
        // TODO user password?
        return json.encode();
    }

    private static Map<String, String> decodeExportMap(Map<String, String> data) {
        Map<String, String> exportData = new HashMap<>();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            exportData.put(entry.getKey(), new String(Base64.getDecoder().decode(entry.getValue()), StandardCharsets.UTF_8));
        }
        return exportData;
    }

    @Override
    public String toString() {
        return "ExportsController";
    }
}
