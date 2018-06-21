/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.lock.client;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.palantir.lock.v2.LockToken;
import com.palantir.lock.v2.TimelockService;
import com.palantir.logsafe.SafeArg;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ExecutorService provided here should have sufficiently many threads to unlock locks from write transactions
 * that may be ongoing in parallel. These locks are released asynchronously so that user code can get back to
 * servicing requests while we do cleanup.
 *
 * There is another layer of retrying below us (at the HTTP client level) for external timelock users.
 * Also, in the event we fail to unlock (e.g. because of a connection issue), locks will eventually time-out.
 * Thus not retrying is reasonably safe (as long as we can guarantee that the lock won't otherwise be refreshed).
 */
public class AsyncTimeLockUnlocker {
    private static final Logger log = LoggerFactory.getLogger(AsyncTimeLockUnlocker.class);

    private static final Duration KICK_JOB_INTERVAL = Duration.ofSeconds(1);

    private final TimelockService timelockService;
    private final ScheduledExecutorService scheduledExecutorService;

    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicReference<Set<LockToken>> outstandingLockTokens = new AtomicReference<>(ImmutableSet.of());

    public AsyncTimeLockUnlocker(TimelockService timelockService, ScheduledExecutorService scheduledExecutorService) {
        this.timelockService = timelockService;
        this.scheduledExecutorService = scheduledExecutorService;
        schedulePeriodicKickJob();
    }

    public void enqueue(Set<LockToken> tokens) {
        outstandingLockTokens.getAndAccumulate(tokens, Sets::union);
        scheduleIfNoTaskScheduled();
    }

    private void scheduleIfNoTaskScheduled() {
        if (available.compareAndSet(true, false)) {
            try {
                scheduledExecutorService.submit(this::unlockOutstanding);
            } finally {
                available.set(true);
            }
        }
    }

    private void unlockOutstanding() {
        Set<LockToken> toUnlock = outstandingLockTokens.getAndSet(ImmutableSet.of());
        if (toUnlock.isEmpty()) {
            return;
        }
        try {
            timelockService.tryUnlock(toUnlock);
        } catch (Throwable t) {
            log.info("Failed to unlock lock tokens {} from timelock. They will eventually expire on their own, but if"
                    + " this message recurs frequently, it may be worth investigation.",
                    SafeArg.of("lockTokens", toUnlock),
                    t);
        }
    }

    private void schedulePeriodicKickJob() {
        // This exists to handle a specific race, where transaction A adds itself to outstandingLockTokens
        // but an already running task in transaction B has read the tokens and is trying to unlock, and
        // then no transactions follow - the tokens registered by A will not unlock.
        // Under high continuous volume of transactions, this job is not important.
        // Also, it won't affect correctness as it is basically doing an empty-set enqueue.
        scheduledExecutorService.scheduleAtFixedRate(
                this::scheduleIfNoTaskScheduled, 0, KICK_JOB_INTERVAL.getSeconds(), TimeUnit.SECONDS);
    }
}