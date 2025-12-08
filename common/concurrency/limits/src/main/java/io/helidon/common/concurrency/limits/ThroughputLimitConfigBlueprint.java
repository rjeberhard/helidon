/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.common.concurrency.limits;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.concurrency.limits.spi.LimitProvider;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Configuration of {@link ThroughputLimit}.
 *
 * @see #amount()
 * @see #duration()
 */
@Prototype.Blueprint
@Prototype.Configured(value = ThroughputLimit.TYPE, root = false)
@Prototype.Provides(LimitProvider.class)
interface ThroughputLimitConfigBlueprint extends Prototype.Factory<ThroughputLimit> {
    /**
     * Number of operations to allow during the time duration.
     * Defaults to {@value ThroughputLimit#DEFAULT_LIMIT}.
     * When set to {@code 0}, we switch to unlimited.
     *
     * @return number of operations to allow
     */
    @Option.Configured
    @Option.DefaultInt(ThroughputLimit.DEFAULT_LIMIT)
    int amount();

    /**
     * The duration over which to count operations.
     * Defaults to {@value ThroughputLimit#DEFAULT_DURATION}
     *
     * @return duration over which to count operations
     */
    @Option.Configured
    @Option.Default(ThroughputLimit.DEFAULT_DURATION)
    Duration duration();

    /**
     * Name of this instance.
     *
     * @return name of the instance
     */
    @Option.Default(ThroughputLimit.TYPE)
    String name();

    /**
     * A clock that supplies nanosecond time.
     *
     * @return supplier of current nanoseconds, defaults to {@link System#nanoTime()}
     */
    Optional<Supplier<Long>> clock();

    /**
     * Whether to collect metrics for the AIMD implementation.
     *
     * @return metrics flag
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean enableMetrics();

}
