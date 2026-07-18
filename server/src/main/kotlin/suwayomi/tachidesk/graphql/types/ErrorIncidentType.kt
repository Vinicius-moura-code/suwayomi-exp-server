package suwayomi.tachidesk.graphql.types

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.ResultRow
import suwayomi.tachidesk.global.model.table.ErrorIncidentTable
import suwayomi.tachidesk.graphql.server.primitives.Node

data class ErrorIncidentType(
    val id: Int,
    val fingerprint: String,
    val exceptionClass: String,
    val message: String,
    val stackTrace: String,
    val source: ErrorIncidentSource,
    val context: String,
    val occurrenceCount: Int,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
) : Node {
    constructor(row: ResultRow) : this(
        id = row[ErrorIncidentTable.id].value,
        fingerprint = row[ErrorIncidentTable.fingerprint],
        exceptionClass = row[ErrorIncidentTable.exceptionClass],
        message = row[ErrorIncidentTable.message],
        stackTrace = row[ErrorIncidentTable.stackTrace],
        source =
            runCatching { ErrorIncidentSource.valueOf(row[ErrorIncidentTable.incidentSource]) }
                .getOrDefault(ErrorIncidentSource.OTHER),
        context = row[ErrorIncidentTable.context],
        occurrenceCount = row[ErrorIncidentTable.occurrenceCount],
        firstSeenAt = row[ErrorIncidentTable.firstSeenAt],
        lastSeenAt = row[ErrorIncidentTable.lastSeenAt],
    )
}
