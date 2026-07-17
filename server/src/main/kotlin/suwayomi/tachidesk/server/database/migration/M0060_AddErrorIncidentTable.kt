package suwayomi.tachidesk.server.database.migration

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

@Suppress("ClassName", "unused")
class M0060_AddErrorIncidentTable : AddTableMigration() {
    private class ErrorIncidentTable : IntIdTable() {
        val fingerprint = varchar("fingerprint", 64).uniqueIndex()
        val exceptionClass = varchar("exception_class", 512)
        val message = varchar("message", 2048)
        val stackTrace = text("stack_trace")
        val incidentSource = varchar("incident_source", 32)
        val context = varchar("context", 4096)
        val occurrenceCount = integer("occurrence_count").default(1)
        val firstSeenAt = long("first_seen_at")
        val lastSeenAt = long("last_seen_at")
    }

    override val tables: Array<Table>
        get() =
            arrayOf(
                ErrorIncidentTable(),
            )
}
