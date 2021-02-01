/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.planner.plan.nodes.exec;

import org.apache.flink.annotation.Internal;
import org.apache.flink.streaming.api.operators.Input;

import java.util.Arrays;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/**
 * The {@link InputProperty} describes the input information of the {@link ExecNode}.
 *
 * <p>The input concept is not corresponding to the execution edge, but rather to the {@link Input}.
 */
@Internal
public class InputProperty {

    public static final InputProperty DEFAULT = InputProperty.builder().build();

    /**
     * The required input data distribution when the target {@link ExecNode} read data in from the
     * corresponding input.
     */
    private final RequiredDistribution requiredDistribution;

    /** How does the input record trigger the output behavior of the target {@link ExecNode}. */
    private final DamBehavior damBehavior;

    /**
     * The priority of this input read by the target {@link ExecNode}.
     *
     * <p>The smaller the integer, the higher the priority. Same integer indicates the same
     * priority.
     */
    private final int priority;

    private InputProperty(
            RequiredDistribution requiredDistribution, DamBehavior damBehavior, int priority) {
        this.requiredDistribution = requiredDistribution;
        this.damBehavior = damBehavior;
        this.priority = priority;
    }

    public RequiredDistribution getRequiredDistribution() {
        return requiredDistribution;
    }

    public DamBehavior getDamBehavior() {
        return damBehavior;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public String toString() {
        return "InputProperty{"
                + "requiredDistribution="
                + requiredDistribution
                + ", damBehavior="
                + damBehavior
                + ", priority="
                + priority
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder of the {@link InputProperty}. */
    public static class Builder {
        private RequiredDistribution requiredDistribution;
        private DamBehavior damBehavior;
        private int priority;

        private Builder() {
            this.requiredDistribution = UNKNOWN_DISTRIBUTION;
            this.damBehavior = DamBehavior.PIPELINED;
            this.priority = 0;
        }

        public Builder requiredDistribution(RequiredDistribution requiredDistribution) {
            this.requiredDistribution = requiredDistribution;
            return this;
        }

        public Builder damBehavior(DamBehavior damBehavior) {
            this.damBehavior = damBehavior;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public InputProperty build() {
            return new InputProperty(requiredDistribution, damBehavior, priority);
        }
    }

    /** The required input data distribution for records when they are read in. */
    public abstract static class RequiredDistribution {
        private final DistributionType type;

        protected RequiredDistribution(DistributionType type) {
            this.type = checkNotNull(type);
        }

        public DistributionType getType() {
            return type;
        }

        @Override
        public String toString() {
            return type.name();
        }
    }

    /**
     * The input will read the records whose keys hash to a particular hash value. A given record
     * appears on exactly one parallelism.
     */
    public static class HashDistribution extends RequiredDistribution {
        private final int[] keys;

        private HashDistribution(int[] keys) {
            super(DistributionType.HASH);
            this.keys = checkNotNull(keys);
            checkArgument(keys.length > 0, "Hash keys must no be empty.");
        }

        public int[] getKeys() {
            return keys;
        }

        @Override
        public String toString() {
            return "HASH" + Arrays.toString(keys);
        }
    }

    /** The input does not require any specific data distribution. */
    public static final RequiredDistribution ANY_DISTRIBUTION =
            new RequiredDistribution(DistributionType.ANY) {};

    /**
     * The input will read all records for each parallelism of the target node. All records appear
     * in each parallelism.
     */
    public static final RequiredDistribution BROADCAST_DISTRIBUTION =
            new RequiredDistribution(DistributionType.BROADCAST) {};

    /** The input will read all records, and the parallelism of the target node must be 1. */
    public static final RequiredDistribution SINGLETON_DISTRIBUTION =
            new RequiredDistribution(DistributionType.SINGLETON) {};

    /**
     * Returns a place-holder required distribution.
     *
     * <p>Currently {@link InputProperty} is only used for deadlock breakup and multi-input in batch
     * mode, so for {@link ExecNode}s not affecting the algorithm we use this place-holder.
     *
     * <p>We should fill out the detailed {@link InputProperty} for each sub-class of {@link
     * ExecNode} in the future.
     */
    public static final RequiredDistribution UNKNOWN_DISTRIBUTION =
            new RequiredDistribution(DistributionType.UNKNOWN) {};

    /**
     * The input will read the records whose keys hash to a particular hash value.
     *
     * @param keys hash keys
     */
    public static HashDistribution hashDistribution(int[] keys) {
        return new HashDistribution(keys);
    }

    /** Enumeration which describes the type of the input data distribution. */
    public enum DistributionType {

        /** The input will accept any data distribution. */
        ANY,

        /**
         * The input will read the records whose keys hash to a particular hash value. A given
         * record appears on exactly one parallelism.
         */
        HASH,

        /**
         * The input will read all records for each parallelism of the target node. All records
         * appear in each parallelism.
         */
        BROADCAST,

        /** The input will read all records, and the parallelism of the target node must be 1. */
        SINGLETON,

        /** Unknown distribution type, will be filled out in the future. */
        UNKNOWN
    }

    /**
     * Enumeration which describes how an input record may trigger the output behavior of the target
     * {@link ExecNode}.
     */
    public enum DamBehavior {

        /**
         * Constant indicating that some or all output records from the input will immediately
         * trigger one or more output records of the target {@link ExecNode}.
         */
        PIPELINED,

        /**
         * Constant indicating that only the last output record from the input will immediately
         * trigger one or more output records of the target {@link ExecNode}.
         */
        END_INPUT,

        /**
         * Constant indicating that all output records from the input will not trigger output
         * records of the target {@link ExecNode}.
         */
        BLOCKING;

        public boolean stricterOrEqual(DamBehavior o) {
            return ordinal() >= o.ordinal();
        }
    }
}
