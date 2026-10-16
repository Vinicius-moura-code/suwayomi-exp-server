package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import suwayomi.tachidesk.manga.model.table.columns.truncatingVarchar

/**
 * Daily aggregates of how sources behaved while being used (see `SourceHealthRecorder`).
 *
 * One row per (source, day, stage, outcome, latency bucket) holding the number of calls.
 */
object SourceHealthTable : IntIdTable() {
    val sourceId = long("source_id")

    /** days since the epoch (UTC) */
    val day = integer("day")
    val stage = varchar("stage", 16)
    val outcome = varchar("outcome", 16)

    /** index of the latency bucket, see `SourceHealthLatency` */
    val bucket = integer("bucket")
    val count = long("count")

    init {
        uniqueIndex(sourceId, day, stage, outcome, bucket)
    }
}

/**
 * The most recent failures of a source, to explain a bad health verdict (capped per source).
 */
object SourceHealthFailureTable : IntIdTable() {
    val sourceId = long("source_id").index()
    val mangaId = integer("manga_id").nullable()
    val stage = varchar("stage", 16)
    val outcome = varchar("outcome", 16)
    val message = truncatingVarchar("message", 512)

    /** epoch seconds */
    val occurredAt = long("occurred_at")
}
