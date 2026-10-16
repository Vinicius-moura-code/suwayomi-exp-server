package suwayomi.tachidesk.manga.impl.sourcehealth

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.local.LocalSource
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.plus
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.table.SourceHealthFailureTable
import suwayomi.tachidesk.manga.model.table.SourceHealthTable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Passively measures how sources behave while they are being used: it never calls a source by itself.
 *
 * Calls are counted in memory and written to the database in batches, so measuring never slows
 * down (or breaks) the call being measured.
 */
object SourceHealthRecorder {
    private val logger = KotlinLogging.logger {}

    private val FLUSH_INTERVAL = 30.seconds
    private val PRUNE_INTERVAL = 6.hours

    /** health rows older than this are removed */
    const val RETENTION_DAYS = 90

    /** failures kept (per source) to explain a bad verdict */
    const val MAX_FAILURES_PER_SOURCE = 100
    private const val MAX_MESSAGE_CHARS = 500

    /** this many different sources failing to connect within [LOCAL_NETWORK_WINDOW_MS] is blamed on our connection */
    private const val LOCAL_NETWORK_SOURCE_THRESHOLD = 3
    private const val LOCAL_NETWORK_WINDOW_MS = 60_000L

    data class Key(
        val sourceId: Long,
        val day: Int,
        val stage: SourceHealthStage,
        val outcome: SourceHealthOutcome,
        val bucket: Int,
    )

    private data class PendingFailure(
        val sourceId: Long,
        val mangaId: Int?,
        val stage: SourceHealthStage,
        val outcome: SourceHealthOutcome,
        val message: String,
        val occurredAt: Long,
    )

    private val pending = ConcurrentHashMap<Key, Long>()
    private val pendingFailures = ConcurrentLinkedQueue<PendingFailure>()
    private val lastNetworkFailureBySource = ConcurrentHashMap<Long, Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastPruneAt = 0L

    private val started by lazy {
        scope.launch {
            while (true) {
                delay(FLUSH_INTERVAL)
                flush()
            }
        }
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { flush() } })
        true
    }

    fun today(): Int = LocalDate.now(ZoneOffset.UTC).toEpochDay().toInt()

    /**
     * Runs [block] (a call to [source]) and records how it went.
     *
     * Nothing is recorded for the local source or for sources whose extension is missing, since
     * neither says anything about a scan. Cancelled calls (e.g. the user left the page) aren't recorded either.
     *
     * @param isEmpty marks a call that worked, but returned nothing, as a failure
     * @param recordSuccess when false only failures are recorded, for steps that are part of another one
     */
    suspend fun <T> measure(
        source: Source,
        stage: SourceHealthStage,
        mangaId: Int? = null,
        recordSuccess: Boolean = true,
        isEmpty: (T) -> Boolean = { false },
        block: suspend () -> T,
    ): T {
        if (source is StubSource || source.id == LocalSource.ID) return block()

        val start = System.nanoTime()
        val result =
            try {
                block()
            } catch (e: TimeoutCancellationException) {
                record(source.id, stage, SourceHealthOutcome.TIMEOUT, elapsedMs(start), mangaId, e.message)
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                record(source.id, stage, SourceHealthClassifier.classify(e), elapsedMs(start), mangaId, describe(e))
                throw e
            }

        if (isEmpty(result)) {
            record(source.id, stage, SourceHealthOutcome.EMPTY, elapsedMs(start), mangaId, "Returned nothing")
        } else if (recordSuccess) {
            record(source.id, stage, SourceHealthOutcome.OK, elapsedMs(start), mangaId, null)
        }
        return result
    }

    fun record(
        sourceId: Long,
        stage: SourceHealthStage,
        outcome: SourceHealthOutcome,
        latencyMs: Long,
        mangaId: Int? = null,
        message: String? = null,
        now: Long = System.currentTimeMillis(),
    ) {
        try {
            started

            val actualOutcome =
                if (outcome == SourceHealthOutcome.NETWORK && isLocalNetworkIssue(sourceId, now)) {
                    SourceHealthOutcome.LOCAL_NETWORK
                } else {
                    outcome
                }

            val key = Key(sourceId, today(), stage, actualOutcome, SourceHealthLatency.bucketOf(latencyMs))
            pending.merge(key, 1L, Long::plus)

            if (actualOutcome != SourceHealthOutcome.OK && actualOutcome != SourceHealthOutcome.LOCAL_NETWORK) {
                pendingFailures +=
                    PendingFailure(
                        sourceId = sourceId,
                        mangaId = mangaId,
                        stage = stage,
                        outcome = actualOutcome,
                        message = message.orEmpty().take(MAX_MESSAGE_CHARS),
                        occurredAt = Instant.ofEpochMilli(now).epochSecond,
                    )
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to record source health" }
        }
    }

    private fun isLocalNetworkIssue(
        sourceId: Long,
        now: Long,
    ): Boolean {
        lastNetworkFailureBySource[sourceId] = now
        lastNetworkFailureBySource.entries.removeIf { now - it.value > LOCAL_NETWORK_WINDOW_MS }
        return lastNetworkFailureBySource.size >= LOCAL_NETWORK_SOURCE_THRESHOLD
    }

    /**
     * Writes everything counted so far to the database.
     */
    @Synchronized
    fun flush() {
        val counts = pending.keys.toList().mapNotNull { key -> pending.remove(key)?.let { key to it } }
        val failures = generateSequence { pendingFailures.poll() }.toList()

        if (counts.isNotEmpty() || failures.isNotEmpty()) {
            try {
                transaction {
                    counts.forEach { (key, count) -> addCount(key, count) }
                    failures.forEach { addFailure(it) }
                    failures.map { it.sourceId }.distinct().forEach { trimFailures(it) }
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to persist source health, keeping it for the next attempt" }
                counts.forEach { (key, count) -> pending.merge(key, count, Long::plus) }
                pendingFailures.addAll(failures)
                return
            }
        }

        pruneIfNeeded()
    }

    private fun addCount(
        key: Key,
        count: Long,
    ) {
        val matches =
            (SourceHealthTable.sourceId eq key.sourceId) and
                (SourceHealthTable.day eq key.day) and
                (SourceHealthTable.stage eq key.stage.name) and
                (SourceHealthTable.outcome eq key.outcome.name) and
                (SourceHealthTable.bucket eq key.bucket)

        val updated =
            SourceHealthTable.update({ matches }) {
                it[SourceHealthTable.count] = SourceHealthTable.count + count
            }
        if (updated == 0) {
            SourceHealthTable.insert {
                it[sourceId] = key.sourceId
                it[day] = key.day
                it[stage] = key.stage.name
                it[outcome] = key.outcome.name
                it[bucket] = key.bucket
                it[SourceHealthTable.count] = count
            }
        }
    }

    private fun addFailure(failure: PendingFailure) {
        SourceHealthFailureTable.insert {
            it[sourceId] = failure.sourceId
            it[mangaId] = failure.mangaId
            it[stage] = failure.stage.name
            it[outcome] = failure.outcome.name
            it[message] = failure.message
            it[occurredAt] = failure.occurredAt
        }
    }

    private fun trimFailures(sourceId: Long) {
        val idsToRemove =
            SourceHealthFailureTable
                .select(SourceHealthFailureTable.id)
                .where { SourceHealthFailureTable.sourceId eq sourceId }
                .orderBy(SourceHealthFailureTable.id to SortOrder.DESC)
                .offset(MAX_FAILURES_PER_SOURCE.toLong())
                .map { it[SourceHealthFailureTable.id] }

        if (idsToRemove.isNotEmpty()) {
            SourceHealthFailureTable.deleteWhere { SourceHealthFailureTable.id inList idsToRemove }
        }
    }

    private fun pruneIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastPruneAt < PRUNE_INTERVAL.inWholeMilliseconds) return
        lastPruneAt = now

        try {
            transaction {
                val oldestDay = today() - RETENTION_DAYS
                SourceHealthTable.deleteWhere { SourceHealthTable.day less oldestDay }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to prune source health" }
        }
    }

    /**
     * Forgets everything known about a source, e.g. after it came back from an outage.
     */
    @Synchronized
    fun reset(sourceId: Long) {
        pending.keys.filter { it.sourceId == sourceId }.forEach { pending.remove(it) }
        pendingFailures.removeIf { it.sourceId == sourceId }
        lastNetworkFailureBySource.remove(sourceId)

        transaction {
            SourceHealthTable.deleteWhere { SourceHealthTable.sourceId eq sourceId }
            SourceHealthFailureTable.deleteWhere { SourceHealthFailureTable.sourceId eq sourceId }
        }
    }

    private fun elapsedMs(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

    private fun describe(throwable: Throwable): String = "${throwable.javaClass.simpleName}: ${throwable.message.orEmpty()}"
}
