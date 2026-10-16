package suwayomi.tachidesk.graphql.queries

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.SourceHealthDayType
import suwayomi.tachidesk.graphql.types.SourceHealthFailureType
import suwayomi.tachidesk.graphql.types.SourceHealthType
import suwayomi.tachidesk.manga.impl.sourcehealth.SourceHealthService

class SourceHealthQuery {
    /**
     * How the sources that were used (or rated) behaved in the last [days] days.
     * Sources nobody touched in that time aren't listed.
     */
    @RequireAuth
    fun sourcesHealth(days: Int? = null): List<SourceHealthType> =
        SourceHealthService.list(days ?: SourceHealthService.DEFAULT_WINDOW_DAYS).map { SourceHealthType(it) }

    @RequireAuth
    fun sourceHealth(
        sourceId: Long,
        days: Int? = null,
    ): SourceHealthType? = SourceHealthService.get(sourceId, days ?: SourceHealthService.DEFAULT_WINDOW_DAYS)?.let { SourceHealthType(it) }

    /**
     * Calls and failures per day of a source.
     */
    @RequireAuth
    fun sourceHealthTrend(
        sourceId: Long,
        days: Int? = null,
    ): List<SourceHealthDayType> =
        SourceHealthService
            .trend(sourceId, days ?: SourceHealthService.DEFAULT_WINDOW_DAYS)
            .map { SourceHealthDayType(it.day.toString(), it.attempts.toInt(), it.failures.toInt()) }

    /**
     * The calls and failures per day of all the sources together.
     */
    @RequireAuth
    fun sourcesHealthTrend(days: Int? = null): List<SourceHealthDayType> =
        SourceHealthService
            .trend(null, days ?: SourceHealthService.DEFAULT_WINDOW_DAYS)
            .map { SourceHealthDayType(it.day.toString(), it.attempts.toInt(), it.failures.toInt()) }

    /**
     * The latest failures of a source, optionally only the ones of a manga.
     */
    @RequireAuth
    fun sourceHealthFailures(
        sourceId: Long,
        mangaId: Int? = null,
        limit: Int? = null,
    ): List<SourceHealthFailureType> = SourceHealthService.failures(sourceId, mangaId, limit ?: 20).map { SourceHealthFailureType(it) }
}
