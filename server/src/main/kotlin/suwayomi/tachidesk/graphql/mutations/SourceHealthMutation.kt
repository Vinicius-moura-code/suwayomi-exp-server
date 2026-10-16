@file:Suppress("unused")

package suwayomi.tachidesk.graphql.mutations

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.SourceHealthType
import suwayomi.tachidesk.manga.impl.sourcehealth.SourceHealthService

class SourceHealthMutation {
    data class SetSourceRatingInput(
        val clientMutationId: String? = null,
        val sourceId: Long,
        /** 1 - 5, null removes the rating */
        val rating: Int? = null,
        val note: String? = null,
    )

    data class SetSourceRatingPayload(
        val clientMutationId: String?,
        val sourceHealth: SourceHealthType?,
    )

    @RequireAuth
    fun setSourceRating(input: SetSourceRatingInput): SetSourceRatingPayload {
        val (clientMutationId, sourceId, rating, note) = input

        SourceHealthService.setRating(sourceId, rating, note)

        return SetSourceRatingPayload(clientMutationId, SourceHealthService.get(sourceId)?.let { SourceHealthType(it) })
    }

    data class ResetSourceHealthInput(
        val clientMutationId: String? = null,
        val sourceId: Long,
    )

    data class ResetSourceHealthPayload(
        val clientMutationId: String?,
        val sourceHealth: SourceHealthType?,
    )

    /**
     * Forgets everything measured about a source (the manual rating is kept),
     * e.g. after it came back from an outage.
     */
    @RequireAuth
    fun resetSourceHealth(input: ResetSourceHealthInput): ResetSourceHealthPayload {
        val (clientMutationId, sourceId) = input

        SourceHealthService.reset(sourceId)

        return ResetSourceHealthPayload(clientMutationId, SourceHealthService.get(sourceId)?.let { SourceHealthType(it) })
    }
}
