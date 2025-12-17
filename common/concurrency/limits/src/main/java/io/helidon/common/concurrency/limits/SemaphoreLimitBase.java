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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Tag;
import io.helidon.metrics.api.Timer;

import static io.helidon.common.concurrency.limits.LimitHandlers.LimiterHandler;
import static io.helidon.metrics.api.Meter.Scope.VENDOR;

/**
 * Base class for semaphore-based limits like FixedLimit and ThroughputLimit.
 */
@SuppressWarnings("removal")
abstract class SemaphoreLimitBase extends LimitAlgorithmDeprecatedBase implements Limit, SemaphoreLimit {

    static final int DEFAULT_QUEUE_LENGTH = 0;

    private LimiterHandler handler;
    private int initialPermits;
    private Semaphore semaphore;
    private final AtomicInteger concurrentRequests;
    private final AtomicInteger rejectedRequests;
    private final Supplier<Long> clock;
    private final boolean enableMetrics;
    private final String name;
    private int queueLength;

    private Timer rttTimer;
    private Timer queueWaitTimer;

    private String originName;

    /**
     * Constructor initializing common fields.
     */
    protected SemaphoreLimitBase(Optional<Supplier<Long>> configuredClock, boolean enableMetrics, String name) {
        this.concurrentRequests = new AtomicInteger();
        this.rejectedRequests = new AtomicInteger();
        this.clock = configuredClock.orElseGet(() -> System::nanoTime);
        this.enableMetrics = enableMetrics;
        this.name = name;
    }

    @Override
    public abstract String type();

    @Override
    public Outcome tryAcquireOutcome(boolean wait) {
        return doTryAcquire(wait);
    }

    @Override
    public <T> Result<T> call(Callable<T> callable) throws Exception {
        return doInvoke(callable);
    }

    @Override
    public Outcome run(Runnable runnable) throws Exception {
        return doInvoke(() -> {
            runnable.run();
            return null;
        }).outcome();
    }

    @SuppressWarnings("removal")
    @Override
    public Semaphore semaphore() {
        return handler.semaphore();
    }

    @Override
    public void init(String socketName) {
        originName = socketName;
        if (enableMetrics) {
            MetricsFactory metricsFactory = MetricsFactory.getInstance();
            MeterRegistry meterRegistry = Metrics.globalRegistry();

            Tag socketNameTag = null;
            if (!socketName.equals("@default")) {
                socketNameTag = Tag.create("socketName", socketName);
            }

            if (semaphore != null) {
                Gauge.Builder<Integer> queueLengthBuilder = metricsFactory.gaugeBuilder(
                        name + "_queue_length", semaphore::getQueueLength).scope(VENDOR);
                if (socketNameTag != null) {
                    queueLengthBuilder.tags(List.of(socketNameTag));
                }
                meterRegistry.getOrCreate(queueLengthBuilder);
            }

            Gauge.Builder<Integer> concurrentRequestsBuilder = metricsFactory.gaugeBuilder(
                    name + "_concurrent_requests", concurrentRequests::get).scope(VENDOR);
            if (socketNameTag != null) {
                concurrentRequestsBuilder.tags(List.of(socketNameTag));
            }
            meterRegistry.getOrCreate(concurrentRequestsBuilder);

            Gauge.Builder<Integer> rejectedRequestsBuilder = metricsFactory.gaugeBuilder(
                    name + "_rejected_requests", rejectedRequests::get).scope(VENDOR);
            if (socketNameTag != null) {
                rejectedRequestsBuilder.tags(List.of(socketNameTag));
            }
            meterRegistry.getOrCreate(rejectedRequestsBuilder);

            Timer.Builder rttTimerBuilder = metricsFactory.timerBuilder(name + "_rtt")
                    .scope(VENDOR)
                    .baseUnit(Timer.BaseUnits.MILLISECONDS);
            if (socketNameTag != null) {
                rttTimerBuilder.tags(List.of(socketNameTag));
            }
            rttTimer = meterRegistry.getOrCreate(rttTimerBuilder);

            Timer.Builder waitTimerBuilder = metricsFactory.timerBuilder(name + "_queue_wait_time")
                    .scope(VENDOR)
                    .baseUnit(Timer.BaseUnits.MILLISECONDS);
            if (socketNameTag != null) {
                waitTimerBuilder.tags(List.of(socketNameTag));
            }
            queueWaitTimer = meterRegistry.getOrCreate(waitTimerBuilder);
        }
    }

    protected void updateMetrics(long startTime, long endTime) {
        long rtt = endTime - startTime;
        if (rttTimer != null) {
            rttTimer.record(rtt, TimeUnit.NANOSECONDS);
        }
    }

    protected void setHandler(LimiterHandler handler) {
        this.handler = handler;
    }

    protected int getInitialPermits() {
        return initialPermits;
    }

    protected void setInitialPermits(int initialPermits) {
        this.initialPermits = initialPermits;
    }

    protected Semaphore getSemaphore() {
        return semaphore;
    }

    protected void setSemaphore(Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    protected AtomicInteger getConcurrentRequests() {
        return concurrentRequests;
    }

    protected Supplier<Long> getClock() {
        return clock;
    }

    protected String getName() {
        return name;
    }

    protected int getQueueLength() {
        return queueLength;
    }

    protected void setQueueLength(int queueLength) {
        this.queueLength = queueLength;
    }

    // Deprecated methods
    @Deprecated(since = "4.3.0", forRemoval = true)
    @Override
    Outcome doTryAcquireObs(boolean wait) {
        return doTryAcquire(wait);
    }

    @Deprecated(since = "4.3.0", forRemoval = true)
    @Override
    <T> Result<T> doInvokeObs(Callable<T> callable) throws Exception {
        return doInvoke(callable);
    }

    private Outcome doTryAcquire(boolean wait) {

        Optional<LimitAlgorithm.Token> token = handler.tryAcquireToken(false);

        if (token.isPresent()) {
            return Outcome.immediateAcceptance(originName, type(), token.get());

        }
        if (wait && queueLength > 0) {
            long startWait = clock.get();
            token = handler.tryAcquireToken(true);
            long endWait = clock.get();
            if (token.isPresent()) {
                if (queueWaitTimer != null) {
                    queueWaitTimer.record(endWait - startWait, TimeUnit.NANOSECONDS);
                }
                return Outcome.deferredAcceptance(originName,
                        type(),
                        token.get(),
                        startWait,
                        endWait);
            }
            return Outcome.deferredRejection(originName, type(), startWait, endWait);
        }
        rejectedRequests.getAndIncrement();
        return Outcome.immediateRejection(originName, type());
    }

    private <T> Result<T> doInvoke(Callable<T> callable) throws Exception {

        Outcome outcome = doTryAcquire(true);
        if (!(outcome instanceof Outcome.Accepted accepted)) {
            throw new LimitException("No more permits available for the semaphore");
        }
        LimitAlgorithm.Token token = accepted.token();
        try {
            T response = callable.call();
            token.success();
            return Result.create(response, outcome);
        } catch (IgnoreTaskException e) {
            token.ignore();
            return Result.create(e.handle(), outcome);
        } catch (Throwable ex) {
            token.dropped();
            throw ex;
        }
    }
}
