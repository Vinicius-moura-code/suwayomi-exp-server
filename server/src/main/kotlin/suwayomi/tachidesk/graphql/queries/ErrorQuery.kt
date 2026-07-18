package suwayomi.tachidesk.graphql.queries

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import suwayomi.tachidesk.global.impl.ErrorIncidentService
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.ErrorIncidentSource
import suwayomi.tachidesk.graphql.types.ErrorIncidentType

class ErrorQuery {
    @RequireAuth
    fun errorIncident(id: Int): ErrorIncidentType? = ErrorIncidentService.getById(id)

    @RequireAuth
    fun errorIncidents(
        source: ErrorIncidentSource? = null,
        orderBy: ErrorIncidentService.OrderBy? = null,
        ascending: Boolean? = null,
        limit: Int? = null,
    ): List<ErrorIncidentType> =
        ErrorIncidentService.list(
            source = source,
            orderBy = orderBy ?: ErrorIncidentService.OrderBy.LAST_SEEN_AT,
            ascending = ascending ?: false,
            limit = limit ?: 50,
        )
}
