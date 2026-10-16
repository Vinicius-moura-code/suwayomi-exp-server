package suwayomi.tachidesk.manga.impl.sourcehealth

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * The steps a user goes through when using a source. A source "delivers" when each step works:
 * it is listed, the details open, the chapters exist, the pages exist and the images load.
 */
enum class SourceHealthStage {
    /** popular / latest / search results */
    LISTING,
    DETAILS,
    CHAPTERS,

    /** the list of pages of a chapter */
    PAGES,
    IMAGE,
    THUMBNAIL,
    ;

    companion object {
        /** the stages that decide whether a source delivered what it promised */
        val CORE = listOf(DETAILS, CHAPTERS, PAGES, IMAGE)
    }
}

enum class SourceHealthOutcome {
    OK,

    /** the source refused us (HTTP 401/403/429, Cloudflare) */
    BLOCKED,

    /** the page / manga / chapter does not exist (anymore) */
    NOT_FOUND,
    SERVER_ERROR,
    TIMEOUT,

    /** DNS, connection or TLS failure */
    NETWORK,

    /** several sources failed to connect at the same time, probably our own connection: not counted */
    LOCAL_NETWORK,

    /** the call worked but returned nothing (no chapters, no pages) */
    EMPTY,

    /** anything else, e.g. an extension that can't parse the site anymore */
    ERROR,
}

enum class SourceHealthVerdict {
    GOOD,
    FAIR,
    POOR,
    INSUFFICIENT_DATA,
}

/**
 * Latency is stored in buckets instead of single values, so a day of data stays tiny
 * while still allowing percentiles.
 */
object SourceHealthLatency {
    /** exclusive upper bounds, in ms. One more (open ended) bucket follows the last bound. */
    private val UPPER_BOUNDS_MS = longArrayOf(250, 500, 1_000, 2_000, 3_000, 5_000, 8_000, 12_000, 20_000, 30_000, 60_000)

    val BUCKET_COUNT = UPPER_BOUNDS_MS.size + 1

    fun bucketOf(latencyMs: Long): Int {
        val index = UPPER_BOUNDS_MS.indexOfFirst { latencyMs < it }
        return if (index == -1) UPPER_BOUNDS_MS.size else index
    }

    /**
     * Estimates a percentile (0.0 - 1.0) of the given distribution, interpolating inside the bucket.
     *
     * @param countByBucket the number of calls per bucket index
     */
    fun percentile(
        countByBucket: Map<Int, Long>,
        percentile: Double,
    ): Long? {
        val total = countByBucket.values.sum()
        if (total <= 0) return null

        val target = percentile.coerceIn(0.0, 1.0) * total
        var cumulative = 0L
        for (bucket in 0 until BUCKET_COUNT) {
            val count = countByBucket[bucket] ?: 0L
            if (count == 0L) continue

            if (cumulative + count >= target) {
                val lower = if (bucket == 0) 0L else UPPER_BOUNDS_MS[bucket - 1]
                val upper = if (bucket < UPPER_BOUNDS_MS.size) UPPER_BOUNDS_MS[bucket] else lower * 2
                val fraction = (target - cumulative) / count
                return lower + ((upper - lower) * fraction).toLong()
            }
            cumulative += count
        }
        return UPPER_BOUNDS_MS.last()
    }
}

object SourceHealthClassifier {
    private const val MAX_CAUSE_DEPTH = 8

    fun classify(throwable: Throwable): SourceHealthOutcome {
        val chain = generateSequence(throwable) { it.cause }.take(MAX_CAUSE_DEPTH).toList()

        chain.filterIsInstance<HttpException>().firstOrNull()?.let {
            return classifyHttpCode(it.code)
        }

        if (chain.any { it is SocketTimeoutException || it is TimeoutCancellationException }) {
            return SourceHealthOutcome.TIMEOUT
        }
        if (chain.any { it is InterruptedIOException && it.message.orEmpty().contains("timeout", ignoreCase = true) }) {
            return SourceHealthOutcome.TIMEOUT
        }
        if (chain.any { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException || it is SSLException }) {
            return SourceHealthOutcome.NETWORK
        }
        if (chain.any { it.message.orEmpty().contains("cloudflare", ignoreCase = true) }) {
            return SourceHealthOutcome.BLOCKED
        }

        return SourceHealthOutcome.ERROR
    }

    fun classifyHttpCode(code: Int): SourceHealthOutcome =
        when (code) {
            401, 403, 429 -> SourceHealthOutcome.BLOCKED
            404, 410 -> SourceHealthOutcome.NOT_FOUND
            in 500..599 -> SourceHealthOutcome.SERVER_ERROR
            else -> SourceHealthOutcome.ERROR
        }
}
