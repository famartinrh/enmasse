/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

package io.enmasse.iot.model.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.fabric8.kubernetes.api.model.Doneable;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.Inline;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder",
        inline = @Inline(
                type = Doneable.class,
                prefix = "Doneable",
                value = "done"))
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceRegistryServiceConfig extends ServiceConfig {
    private FileBasedDeviceRegistry file;
    private InfinispanDeviceRegistry infinispan;

    public FileBasedDeviceRegistry getFile() {
        return file;
    }
    public void setFile(FileBasedDeviceRegistry file) {
        this.file = file;
    }

    public InfinispanDeviceRegistry getInfinispan(){ return infinispan;}
    public void setInfinispan(InfinispanDeviceRegistry infinispan) {
        this.infinispan = infinispan;
    }
}
