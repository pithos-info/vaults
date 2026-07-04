/*
 * Copyright 2026 Pithos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package info.pithos.vault;

import info.pithos.runtime.core.context.ApplicationContext;
import info.pithos.runtime.core.log.ServiceLogger;
import info.pithos.runtime.core.metrics.InfraOperation;
import info.pithos.runtime.core.metrics.MetricsCommitter;
import info.pithos.runtime.core.util.Util;
import info.pithos.runtime.model.metrics.Metrics.ComponentType;
import info.pithos.runtime.model.metrics.Metrics.MetricEvent;
import info.pithos.runtime.model.metrics.Metrics.MetricUnit;
import info.pithos.runtime.model.protocol.Context.LogLevelType;
import info.pithos.runtime.model.protocol.Context.RequestContext;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

public abstract class AbstractVaultClient implements VaultClient {

    protected final ApplicationContext context;

    protected AbstractVaultClient(ApplicationContext context) {
        if (context == null) throw new IllegalArgumentException("context = null");
        this.context = context;
    }

    protected abstract String componentProvider();

    protected String createSecretPath(RequestContext requestContext, String name) {
        return Util.createKey(requestContext, createSecretPathName(name));
    }

    protected <T> CompletableFuture<T> submitAsync(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, context.getSystemContext().getForkJoinExecutor());
    }

    protected String createSecretPathName(String name) {
        return context.getSystemContext().getServiceName() + ":" + name;
    }

    // ── Metrics helpers ───────────────────────────────────────────────────────

    protected void recordOp(RequestContext rc, String secretName, VaultOperation op,
                            long startMs, Throwable ex) {
        MetricsCommitter mc = context.getMetricsCommitter();
        if (mc == null) return;
        long elapsedMs = System.currentTimeMillis() - startMs;
        String provider = componentProvider();
        emitPair(mc, rc, secretName, provider, op, elapsedMs, ex);
        emitPair(mc, rc, provider, provider, op, elapsedMs, ex);
        ServiceLogger log = context.getSystemContext().getLogger();
        if (ex == null) {
            log.logRequest(rc, getClass(), LogLevelType.DEBUG, "{} {} {}ms", op.stem(), secretName, elapsedMs);
        } else {
            log.logRequest(rc, getClass(), LogLevelType.ERROR, ex, "{} {} failed after {}ms", op.stem(), secretName, elapsedMs);
        }
    }

    private static void emitPair(MetricsCommitter mc, RequestContext rc, String componentId,
                                  String provider, InfraOperation op, long elapsedMs, Throwable ex) {
        mc.record(rc, MetricEvent.newBuilder()
            .setMetric(op.latency()).setUnit(MetricUnit.MS).setValue(elapsedMs)
            .setComponentType(ComponentType.VAULT).setComponentId(componentId).setComponentProvider(provider)
            .build());
        mc.record(rc, MetricEvent.newBuilder()
            .setMetric(InfraOperation.outcome(op, ex)).setUnit(MetricUnit.COUNT).setValue(1.0)
            .setComponentType(ComponentType.VAULT).setComponentId(componentId).setComponentProvider(provider)
            .build());
    }
}
