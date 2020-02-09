/*
 * Copyright 2019, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.enmasse.address.model;

import io.enmasse.admin.model.v1.AbstractWithAdditionalProperties;
import io.fabric8.kubernetes.api.model.Doneable;
import io.sundr.builder.annotations.Buildable;
import io.sundr.builder.annotations.BuildableReference;
import io.sundr.builder.annotations.Inline;

import java.util.Objects;

@Buildable(
        editableEnabled = false,
        generateBuilderPackage = false,
        builderPackage = "io.fabric8.kubernetes.api.builder",
        refs= {@BuildableReference(AbstractWithAdditionalProperties.class)},
        inline = @Inline(
                type = Doneable.class,
                prefix = "Doneable",
                value = "done"
        )
)
public class ExportSpec extends AbstractWithAdditionalProperties {
    private ExportKind kind;
    private String name;
    private ExportFormat format;

    public ExportKind getKind() {
        return kind;
    }

    public void setKind(ExportKind kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ExportFormat getFormat() {
		return format;
	}

	public void setFormat(ExportFormat format) {
		this.format = format;
	}

	@Override
    public String toString() {
        return "ExportSpec{" +
                "kind=" + kind + ", format=" + format +
                ", name='" + name + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExportSpec that = (ExportSpec) o;
        return kind == that.kind &&
                Objects.equals(name, that.name) &&
                format == that.format;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name, format);
    }
}
