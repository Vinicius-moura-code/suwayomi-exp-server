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
class M0061_AddSourceHealthTables : AddTableMigration() {
    private class SourceHealthTable : IntIdTable() {
        val sourceId = long("source_id")
        val day = integer("day")
        val stage = varchar("stage", 16)
        val outcome = varchar("outcome", 16)
        val bucket = integer("bucket")
        val count = long("count")

        init {
            uniqueIndex(sourceId, day, stage, outcome, bucket)
        }
    }

    private class SourceHealthFailureTable : IntIdTable() {
        val sourceId = long("source_id").index()
        val mangaId = integer("manga_id").nullable()
        val stage = varchar("stage", 16)
        val outcome = varchar("outcome", 16)
        val message = varchar("message", 512)
        val occurredAt = long("occurred_at")
    }

    override val tables: Array<Table>
        get() =
            arrayOf(
                SourceHealthTable(),
                SourceHealthFailureTable(),
            )
}
