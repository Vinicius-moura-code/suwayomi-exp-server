package suwayomi.tachidesk.manga.impl.sourcehealth

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.source.local.LocalSource
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.alias
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.Source
import suwayomi.tachidesk.manga.impl.extension.Extension.proxyExtensionIconUrl
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceHealthFailureTable
import suwayomi.tachidesk.manga.model.table.SourceHealthTable
import suwayomi.tachidesk.manga.model.table.SourceMetaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import java.time.Instant
import java.time.LocalDate

/**
 * Turns the raw numbers recorded by [SourceHealthRecorder] (plus data the server already has, like what was read)
 * into something a person can use to decide whether a source is worth following a manga on.
 */
object SourceHealthService {
    const val DEFAULT_WINDOW_DAYS = 30
    const val MAX_WINDOW_DAYS = SourceHealthRecorder.RETENTION_DAYS

    const val RATING_KEY = "suwayomi.rating"
    const val RATING_NOTE_KEY = "suwayomi.ratingNote"
    const val MIN_RATING = 1
    const val MAX_RATING = 5
    private const val MAX_NOTE_CHARS = 1000

    /** a step with less attempts than this isn't enough to judge a source on */
    const val MIN_STAGE_ATTEMPTS = 5

    const val GOOD_DELIVERY_RATE = 0.9
    const val POOR_DELIVERY_RATE = 0.7
    const val GOOD_LOAD_MS = 3_000L
    const val POOR_LOAD_MS = 8_000L

    data class HealthRow(
        val stage: SourceHealthStage,
        val outcome: SourceHealthOutcome,
        val bucket: Int,
        val count: Long,
    )

    data class StageStats(
        val stage: SourceHealthStage,
        val attempts: Long,
        val successes: Long,
        val failures: Long,
        val deliveryRate: Double?,
        val p50Ms: Long?,
        val p95Ms: Long?,
        val failuresByOutcome: Map<SourceHealthOutcome, Long>,
    )

    data class Judgement(
        val verdict: SourceHealthVerdict,
        val deliveryRate: Double?,
        val typicalLoadMs: Long?,
        val weakestStage: SourceHealthStage?,
    )

    data class Usage(
        val chaptersRead: Int,
        val mangasRead: Int,
        val lastReadAt: Long?,
    )

    data class SourceHealth(
        val sourceId: Long,
        val name: String,
        val lang: String,
        val iconUrl: String?,
        val windowDays: Int,
        val stages: List<StageStats>,
        val attempts: Long,
        val judgement: Judgement,
        val usage: Usage,
        val rating: Int?,
        val ratingNote: String?,
    )

    data class DayStats(
        val day: LocalDate,
        val attempts: Long,
        val failures: Long,
    )

    data class FailureEntry(
        val id: Int,
        val sourceId: Long,
        val mangaId: Int?,
        val mangaTitle: String?,
        val stage: SourceHealthStage,
        val outcome: SourceHealthOutcome,
        val message: String,
        val occurredAt: Long,
    )

    // region calculations

    fun stageStats(
        stage: SourceHealthStage,
        rows: List<HealthRow>,
    ): StageStats {
        val ok = rows.filter { it.outcome == SourceHealthOutcome.OK }
        val failed = rows.filter { it.outcome != SourceHealthOutcome.OK && it.outcome != SourceHealthOutcome.LOCAL_NETWORK }

        val successes = ok.sumOf { it.count }
        val failures = failed.sumOf { it.count }
        val attempts = successes + failures

        val latencyByBucket = ok.groupBy { it.bucket }.mapValues { (_, bucketRows) -> bucketRows.sumOf { it.count } }

        return StageStats(
            stage = stage,
            attempts = attempts,
            successes = successes,
            failures = failures,
            deliveryRate = if (attempts > 0) successes.toDouble() / attempts else null,
            p50Ms = SourceHealthLatency.percentile(latencyByBucket, 0.5),
            p95Ms = SourceHealthLatency.percentile(latencyByBucket, 0.95),
            failuresByOutcome = failed.groupBy { it.outcome }.mapValues { (_, outcomeRows) -> outcomeRows.sumOf { it.count } },
        )
    }

    /**
     * Rates a source on the steps that decide whether it delivers: "delivery" is the average success rate of those
     * steps (so a step with a lot of calls, like images, can't hide a broken one) and "load" the slowest typical step.
     */
    fun judge(stages: List<StageStats>): Judgement {
        val eligible = stages.filter { it.stage in SourceHealthStage.CORE && it.attempts >= MIN_STAGE_ATTEMPTS }
        if (eligible.isEmpty()) {
            return Judgement(SourceHealthVerdict.INSUFFICIENT_DATA, null, null, null)
        }

        val delivery = eligible.mapNotNull { it.deliveryRate }.average()
        val load = eligible.mapNotNull { it.p50Ms }.maxOrNull()
        val weakest = eligible.filter { (it.deliveryRate ?: 1.0) < 1.0 }.minByOrNull { it.deliveryRate ?: 1.0 }?.stage

        val verdict =
            when {
                delivery < POOR_DELIVERY_RATE || (load != null && load > POOR_LOAD_MS) -> SourceHealthVerdict.POOR
                delivery >= GOOD_DELIVERY_RATE && (load == null || load <= GOOD_LOAD_MS) -> SourceHealthVerdict.GOOD
                else -> SourceHealthVerdict.FAIR
            }

        return Judgement(verdict, delivery, load, weakest)
    }

    // endregion

    // region queries

    private fun firstDayOf(windowDays: Int): Int = SourceHealthRecorder.today() - (windowDays.coerceIn(1, MAX_WINDOW_DAYS) - 1)

    private fun healthRows(
        windowDays: Int,
        sourceId: Long? = null,
    ): Map<Long, List<HealthRow>> =
        transaction {
            val firstDay = firstDayOf(windowDays)
            SourceHealthTable
                .selectAll()
                .where {
                    if (sourceId != null) {
                        (SourceHealthTable.day greaterEq firstDay) and (SourceHealthTable.sourceId eq sourceId)
                    } else {
                        SourceHealthTable.day greaterEq firstDay
                    }
                }.mapNotNull { row ->
                    val stage = SourceHealthStage.entries.firstOrNull { it.name == row[SourceHealthTable.stage] }
                    val outcome = SourceHealthOutcome.entries.firstOrNull { it.name == row[SourceHealthTable.outcome] }
                    if (stage == null || outcome == null) return@mapNotNull null

                    row[SourceHealthTable.sourceId] to
                        HealthRow(stage, outcome, row[SourceHealthTable.bucket], row[SourceHealthTable.count])
                }.groupBy({ it.first }, { it.second })
        }

    private fun usageBySource(windowDays: Int): Map<Long, Usage> =
        transaction {
            val since = Instant.now().epochSecond - windowDays.coerceIn(1, MAX_WINDOW_DAYS) * SECONDS_PER_DAY
            val chapterCount = ChapterTable.id.count().alias("chapter_count")
            val mangaCount = MangaTable.id.countDistinct().alias("manga_count")

            val inWindow =
                (ChapterTable innerJoin MangaTable)
                    .select(MangaTable.sourceReference, chapterCount, mangaCount)
                    .where { ChapterTable.lastReadAt greaterEq since }
                    .groupBy(MangaTable.sourceReference)
                    .associate { it[MangaTable.sourceReference] to (it[chapterCount].toInt() to it[mangaCount].toInt()) }

            val lastReadAt = ChapterTable.lastReadAt.max().alias("last_read_at")
            val lastRead =
                (ChapterTable innerJoin MangaTable)
                    .select(MangaTable.sourceReference, lastReadAt)
                    .where { ChapterTable.lastReadAt greater 0L }
                    .groupBy(MangaTable.sourceReference)
                    .associate { it[MangaTable.sourceReference] to it[lastReadAt] }

            (inWindow.keys + lastRead.keys).associateWith { id ->
                Usage(
                    chaptersRead = inWindow[id]?.first ?: 0,
                    mangasRead = inWindow[id]?.second ?: 0,
                    lastReadAt = lastRead[id],
                )
            }
        }

    private fun ratedSourceIds(): Set<Long> =
        transaction {
            SourceMetaTable
                .select(SourceMetaTable.ref)
                .where { SourceMetaTable.key eq RATING_KEY }
                .map { it[SourceMetaTable.ref] }
                .toSet()
        }

    private fun build(
        sourceIds: Collection<Long>,
        windowDays: Int,
        healthBySource: Map<Long, List<HealthRow>>,
        usageBySource: Map<Long, Usage>,
    ): List<SourceHealth> {
        if (sourceIds.isEmpty()) return emptyList()

        val metaBySource = Source.getSourcesMetaMaps(sourceIds.toList())

        val sourceRows =
            transaction {
                (SourceTable innerJoin ExtensionTable)
                    .select(SourceTable.id, SourceTable.name, SourceTable.lang, ExtensionTable.pkgName)
                    .where { SourceTable.id inList sourceIds }
                    .toList()
            }

        return sourceRows.map { row ->
            val sourceId = row[SourceTable.id].value
            val stages =
                healthBySource[sourceId]
                    .orEmpty()
                    .groupBy { it.stage }
                    .map { (stage, rows) -> stageStats(stage, rows) }
                    .filter { it.attempts > 0 }
                    .sortedBy { it.stage.ordinal }

            val meta = metaBySource[sourceId].orEmpty()

            SourceHealth(
                sourceId = sourceId,
                name = row[SourceTable.name],
                lang = row[SourceTable.lang],
                iconUrl = proxyExtensionIconUrl(row[ExtensionTable.pkgName]),
                windowDays = windowDays,
                stages = stages,
                attempts = stages.sumOf { it.attempts },
                judgement = judge(stages),
                usage = usageBySource[sourceId] ?: Usage(0, 0, null),
                rating = meta[RATING_KEY]?.toIntOrNull(),
                ratingNote = meta[RATING_NOTE_KEY]?.takeIf { it.isNotBlank() },
            )
        }
    }

    /**
     * Every source that was actually used (or rated) in the window. Sources nobody touched are left out.
     */
    fun list(windowDays: Int = DEFAULT_WINDOW_DAYS): List<SourceHealth> {
        SourceHealthRecorder.flush()

        val health = healthRows(windowDays)
        val usage = usageBySource(windowDays)
        val usedInWindow = usage.filterValues { it.chaptersRead > 0 }.keys

        val ids = (health.keys + usedInWindow + ratedSourceIds()) - LocalSource.ID
        return build(ids, windowDays, health, usage).sortedBy { it.name.lowercase() }
    }

    fun get(
        sourceId: Long,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
    ): SourceHealth? {
        SourceHealthRecorder.flush()

        return build(
            sourceIds = listOf(sourceId),
            windowDays = windowDays,
            healthBySource = healthRows(windowDays, sourceId),
            usageBySource = usageBySource(windowDays),
        ).firstOrNull()
    }

    /**
     * The calls and failures per day of a source (or of all the sources, if [sourceId] is null).
     */
    fun trend(
        sourceId: Long?,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
    ): List<DayStats> {
        SourceHealthRecorder.flush()

        val firstDay = firstDayOf(windowDays)
        val rows =
            transaction {
                SourceHealthTable
                    .selectAll()
                    .where {
                        val inWindow = SourceHealthTable.day greaterEq firstDay
                        if (sourceId == null) inWindow else (SourceHealthTable.sourceId eq sourceId) and inWindow
                    }.filter { it[SourceHealthTable.outcome] != SourceHealthOutcome.LOCAL_NETWORK.name }
                    .map { Triple(it[SourceHealthTable.day], it[SourceHealthTable.outcome], it[SourceHealthTable.count]) }
            }

        return rows
            .groupBy { it.first }
            .toSortedMap()
            .map { (day, dayRows) ->
                DayStats(
                    day = LocalDate.ofEpochDay(day.toLong()),
                    attempts = dayRows.sumOf { it.third },
                    failures = dayRows.filter { it.second != SourceHealthOutcome.OK.name }.sumOf { it.third },
                )
            }
    }

    fun failures(
        sourceId: Long,
        mangaId: Int? = null,
        limit: Int = 20,
    ): List<FailureEntry> {
        SourceHealthRecorder.flush()

        return transaction {
            val rows =
                SourceHealthFailureTable
                    .selectAll()
                    .where {
                        if (mangaId != null) {
                            (SourceHealthFailureTable.sourceId eq sourceId) and (SourceHealthFailureTable.mangaId eq mangaId)
                        } else {
                            SourceHealthFailureTable.sourceId eq sourceId
                        }
                    }.orderBy(SourceHealthFailureTable.id to SortOrder.DESC)
                    .limit(limit.coerceIn(1, SourceHealthRecorder.MAX_FAILURES_PER_SOURCE))
                    .toList()

            val titles =
                MangaTable
                    .select(MangaTable.id, MangaTable.title)
                    .where { MangaTable.id inList rows.mapNotNull { it[SourceHealthFailureTable.mangaId] } }
                    .associate { it[MangaTable.id].value to it[MangaTable.title] }

            rows.mapNotNull { row ->
                val stage = SourceHealthStage.entries.firstOrNull { it.name == row[SourceHealthFailureTable.stage] }
                val outcome = SourceHealthOutcome.entries.firstOrNull { it.name == row[SourceHealthFailureTable.outcome] }
                if (stage == null || outcome == null) return@mapNotNull null

                FailureEntry(
                    id = row[SourceHealthFailureTable.id].value,
                    sourceId = row[SourceHealthFailureTable.sourceId],
                    mangaId = row[SourceHealthFailureTable.mangaId],
                    mangaTitle = row[SourceHealthFailureTable.mangaId]?.let { titles[it] },
                    stage = stage,
                    outcome = outcome,
                    message = row[SourceHealthFailureTable.message],
                    occurredAt = row[SourceHealthFailureTable.occurredAt],
                )
            }
        }
    }

    // endregion

    // region changes

    /**
     * Sets the manual rating of a source, a null [rating] removes it.
     */
    fun setRating(
        sourceId: Long,
        rating: Int?,
        note: String?,
    ) {
        require(rating == null || rating in MIN_RATING..MAX_RATING) { "rating must be between $MIN_RATING and $MAX_RATING" }

        if (rating == null) {
            transaction {
                SourceMetaTable.deleteWhere {
                    (SourceMetaTable.ref eq sourceId) and (SourceMetaTable.key inList listOf(RATING_KEY, RATING_NOTE_KEY))
                }
            }
            return
        }

        Source.modifySourceMetas(
            mapOf(
                sourceId to
                    mapOf(
                        RATING_KEY to rating.toString(),
                        RATING_NOTE_KEY to note.orEmpty().trim().take(MAX_NOTE_CHARS),
                    ),
            ),
        )
    }

    fun reset(sourceId: Long) = SourceHealthRecorder.reset(sourceId)

    // endregion

    private const val SECONDS_PER_DAY = 24 * 60 * 60L
}
