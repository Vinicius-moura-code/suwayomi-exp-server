package suwayomi.tachidesk.graphql.types

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.manga.impl.sourcehealth.SourceHealthOutcome
import suwayomi.tachidesk.manga.impl.sourcehealth.SourceHealthService
import suwayomi.tachidesk.manga.impl.sourcehealth.SourceHealthStage
import suwayomi.tachidesk.manga.impl.sourcehealth.SourceHealthVerdict

data class SourceHealthOutcomeCountType(
    val outcome: SourceHealthOutcome,
    val count: Int,
)

data class SourceHealthStageType(
    val stage: SourceHealthStage,
    val attempts: Int,
    val successes: Int,
    val failures: Int,
    /** 0.0 - 1.0 */
    val deliveryRate: Double?,
    /** typical (median) time of a successful call, in ms */
    val p50Ms: Int?,
    /** the slow end (95th percentile) of the successful calls, in ms */
    val p95Ms: Int?,
    val failuresByOutcome: List<SourceHealthOutcomeCountType>,
)

data class SourceHealthUsageType(
    val chaptersRead: Int,
    val mangasRead: Int,
    /** epoch seconds of the last time something was read from the source (any time, not only in the window) */
    val lastReadAt: Long?,
)

data class SourceHealthType(
    val sourceId: Long,
    val name: String,
    val lang: String,
    val iconUrl: String?,
    val windowDays: Int,
    val verdict: SourceHealthVerdict,
    /** average success rate of the steps that decide if a source delivers (0.0 - 1.0), null without enough data */
    val deliveryRate: Double?,
    /** the slowest typical (median) step, in ms */
    val typicalLoadMs: Int?,
    /** the step that fails the most */
    val weakestStage: SourceHealthStage?,
    val attempts: Int,
    val stages: List<SourceHealthStageType>,
    val usage: SourceHealthUsageType,
    /** the manual rating, 1 - 5 */
    val rating: Int?,
    val ratingNote: String?,
) {
    constructor(health: SourceHealthService.SourceHealth) : this(
        sourceId = health.sourceId,
        name = health.name,
        lang = health.lang,
        iconUrl = health.iconUrl,
        windowDays = health.windowDays,
        verdict = health.judgement.verdict,
        deliveryRate = health.judgement.deliveryRate,
        typicalLoadMs = health.judgement.typicalLoadMs?.toInt(),
        weakestStage = health.judgement.weakestStage,
        attempts = health.attempts.toInt(),
        stages =
            health.stages.map { stage ->
                SourceHealthStageType(
                    stage = stage.stage,
                    attempts = stage.attempts.toInt(),
                    successes = stage.successes.toInt(),
                    failures = stage.failures.toInt(),
                    deliveryRate = stage.deliveryRate,
                    p50Ms = stage.p50Ms?.toInt(),
                    p95Ms = stage.p95Ms?.toInt(),
                    failuresByOutcome =
                        stage.failuresByOutcome
                            .map { SourceHealthOutcomeCountType(it.key, it.value.toInt()) }
                            .sortedByDescending { it.count },
                )
            },
        usage =
            SourceHealthUsageType(
                chaptersRead = health.usage.chaptersRead,
                mangasRead = health.usage.mangasRead,
                lastReadAt = health.usage.lastReadAt,
            ),
        rating = health.rating,
        ratingNote = health.ratingNote,
    )
}

data class SourceHealthDayType(
    /** ISO date (UTC), e.g. 2026-10-15 */
    val day: String,
    val attempts: Int,
    val failures: Int,
)

data class SourceHealthFailureType(
    val id: Int,
    val sourceId: Long,
    val mangaId: Int?,
    val mangaTitle: String?,
    val stage: SourceHealthStage,
    val outcome: SourceHealthOutcome,
    val message: String,
    /** epoch seconds */
    val occurredAt: Long,
) {
    constructor(entry: SourceHealthService.FailureEntry) : this(
        id = entry.id,
        sourceId = entry.sourceId,
        mangaId = entry.mangaId,
        mangaTitle = entry.mangaTitle,
        stage = entry.stage,
        outcome = entry.outcome,
        message = entry.message,
        occurredAt = entry.occurredAt,
    )
}
