package suwayomi.tachidesk.global.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import suwayomi.tachidesk.manga.model.table.columns.truncatingVarchar
import suwayomi.tachidesk.manga.model.table.columns.unlimitedVarchar

/**
 * Aggregated server errors for local observability (fingerprint + occurrence count).
 */
object ErrorIncidentTable : IntIdTable() {
    val fingerprint = varchar("fingerprint", 64).uniqueIndex()
    val exceptionClass = truncatingVarchar("exception_class", 512)
    val message = truncatingVarchar("message", 2048)
    val stackTrace = unlimitedVarchar("stack_trace")
    val incidentSource = truncatingVarchar("incident_source", 32)
    val context = truncatingVarchar("context", 4096)
    val occurrenceCount = integer("occurrence_count").default(1)
    val firstSeenAt = long("first_seen_at")
    val lastSeenAt = long("last_seen_at")
}
