package suwayomi.tachidesk.server.metrics

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import java.util.concurrent.TimeUnit

/**
 * Local Prometheus metrics registry for development observability.
 * Scraped at GET /api/metrics (intentionally unauthenticated for local Docker scrape).
 */
object MetricsRegistry {
    val prometheusRegistry: PrometheusMeterRegistry =
        PrometheusMeterRegistry(PrometheusConfig.DEFAULT).also { registry ->
            Metrics.addRegistry(registry)
            ClassLoaderMetrics().bindTo(registry)
            JvmMemoryMetrics().bindTo(registry)
            JvmGcMetrics().bindTo(registry)
            JvmThreadMetrics().bindTo(registry)
            ProcessorMetrics().bindTo(registry)
            UptimeMetrics().bindTo(registry)
        }

    fun scrape(): String = prometheusRegistry.scrape()

    fun recordHttpRequest(
        method: String,
        status: Int,
        pathGroup: String,
        durationNanos: Long,
    ) {
        Timer
            .builder("http.server.requests")
            .description("HTTP server request latency")
            .tag("method", method)
            .tag("status", status.toString())
            .tag("uri", pathGroup)
            .register(prometheusRegistry)
            .record(durationNanos, TimeUnit.NANOSECONDS)
    }
}
