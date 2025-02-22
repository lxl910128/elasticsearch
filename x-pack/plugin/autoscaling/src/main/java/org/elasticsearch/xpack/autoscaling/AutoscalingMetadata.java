/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.autoscaling;

import org.elasticsearch.Version;
import org.elasticsearch.cluster.AbstractDiffable;
import org.elasticsearch.cluster.Diff;
import org.elasticsearch.cluster.DiffableUtils;
import org.elasticsearch.cluster.NamedDiff;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.xcontent.ConstructingObjectParser;
import org.elasticsearch.xcontent.ParseField;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentParser;
import org.elasticsearch.xpack.autoscaling.policy.AutoscalingPolicyMetadata;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AutoscalingMetadata implements Metadata.NonRestorableCustom {

    public static final String NAME = "autoscaling";

    public static final AutoscalingMetadata EMPTY = new AutoscalingMetadata(Collections.emptySortedMap());

    private static final ParseField POLICIES_FIELD = new ParseField("policies");

    @SuppressWarnings("unchecked")
    private static final ConstructingObjectParser<AutoscalingMetadata, Void> PARSER = new ConstructingObjectParser<>(
        NAME,
        c -> new AutoscalingMetadata(
            new TreeMap<>(
                ((List<AutoscalingPolicyMetadata>) c[0]).stream().collect(Collectors.toMap(p -> p.policy().name(), Function.identity()))
            )
        )
    );

    static {
        PARSER.declareNamedObjects(
            ConstructingObjectParser.constructorArg(),
            (p, c, n) -> AutoscalingPolicyMetadata.parse(p, n),
            POLICIES_FIELD
        );
    }

    public static AutoscalingMetadata parse(final XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    private final SortedMap<String, AutoscalingPolicyMetadata> policies;

    public SortedMap<String, AutoscalingPolicyMetadata> policies() {
        return policies;
    }

    public AutoscalingMetadata(final SortedMap<String, AutoscalingPolicyMetadata> policies) {
        this.policies = policies;
    }

    public AutoscalingMetadata(final StreamInput in) throws IOException {
        final int size = in.readVInt();
        final SortedMap<String, AutoscalingPolicyMetadata> policies = new TreeMap<>();
        for (int i = 0; i < size; i++) {
            final AutoscalingPolicyMetadata policyMetadata = new AutoscalingPolicyMetadata(in);
            policies.put(policyMetadata.policy().name(), policyMetadata);
        }
        this.policies = policies;
    }

    @Override
    public void writeTo(final StreamOutput out) throws IOException {
        out.writeVInt(policies.size());
        for (final Map.Entry<String, AutoscalingPolicyMetadata> policy : policies.entrySet()) {
            policy.getValue().writeTo(out);
        }
    }

    @Override
    public EnumSet<Metadata.XContentContext> context() {
        return Metadata.ALL_CONTEXTS;
    }

    @Override
    public Diff<Metadata.Custom> diff(final Metadata.Custom previousState) {
        return new AutoscalingMetadataDiff((AutoscalingMetadata) previousState, this);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    @Override
    public Version getMinimalSupportedVersion() {
        return Version.V_7_8_0;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.field(POLICIES_FIELD.getPreferredName(), policies);
        return builder;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final AutoscalingMetadata metadata = (AutoscalingMetadata) o;
        return policies.equals(metadata.policies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(policies);
    }

    public static class AutoscalingMetadataDiff implements NamedDiff<Metadata.Custom> {

        final Diff<Map<String, AutoscalingPolicyMetadata>> policies;

        public AutoscalingMetadataDiff(final AutoscalingMetadata before, final AutoscalingMetadata after) {
            this.policies = DiffableUtils.diff(before.policies, after.policies, DiffableUtils.getStringKeySerializer());
        }

        public AutoscalingMetadataDiff(final StreamInput in) throws IOException {
            this.policies = DiffableUtils.readJdkMapDiff(
                in,
                DiffableUtils.getStringKeySerializer(),
                AutoscalingPolicyMetadata::new,
                AutoscalingMetadataDiff::readFrom
            );
        }

        @Override
        public Metadata.Custom apply(final Metadata.Custom part) {
            return new AutoscalingMetadata(new TreeMap<>(policies.apply(((AutoscalingMetadata) part).policies)));
        }

        @Override
        public String getWriteableName() {
            return NAME;
        }

        @Override
        public void writeTo(final StreamOutput out) throws IOException {
            policies.writeTo(out);
        }

        static Diff<AutoscalingPolicyMetadata> readFrom(final StreamInput in) throws IOException {
            return AbstractDiffable.readDiffFrom(AutoscalingPolicyMetadata::new, in);
        }

    }

}
