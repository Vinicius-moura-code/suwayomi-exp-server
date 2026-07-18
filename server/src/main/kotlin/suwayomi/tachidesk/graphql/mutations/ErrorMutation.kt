@file:Suppress("unused")

package suwayomi.tachidesk.graphql.mutations

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.global.impl.ErrorIncidentService
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.ErrorIncidentType

class ErrorMutation {
    data class DeleteErrorIncidentInput(
        val clientMutationId: String? = null,
        val id: Int,
    )

    data class DeleteErrorIncidentPayload(
        val clientMutationId: String?,
        val errorIncident: ErrorIncidentType?,
    )

    @RequireAuth
    fun deleteErrorIncident(input: DeleteErrorIncidentInput): DeleteErrorIncidentPayload {
        val deleted = ErrorIncidentService.deleteById(input.id)
        return DeleteErrorIncidentPayload(input.clientMutationId, deleted)
    }

    data class ClearErrorIncidentsInput(
        val clientMutationId: String? = null,
    )

    data class ClearErrorIncidentsPayload(
        val clientMutationId: String?,
        val deletedCount: Int,
    )

    @RequireAuth
    fun clearErrorIncidents(input: ClearErrorIncidentsInput): ClearErrorIncidentsPayload {
        val deletedCount = ErrorIncidentService.clearAll()
        return ClearErrorIncidentsPayload(input.clientMutationId, deletedCount)
    }
}
