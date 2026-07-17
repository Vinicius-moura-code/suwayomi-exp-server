package suwayomi.tachidesk.global.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.model.table.ErrorIncidentTable
import suwayomi.tachidesk.graphql.types.ErrorIncidentSource
import suwayomi.tachidesk.graphql.types.ErrorIncidentType

object ErrorIncidentService {
    enum class OrderBy {
        LAST_SEEN_AT,
        OCCURRENCE_COUNT,
        FIRST_SEEN_AT,
    }

    fun list(
        source: ErrorIncidentSource? = null,
        orderBy: OrderBy = OrderBy.LAST_SEEN_AT,
        ascending: Boolean = false,
        limit: Int = 50,
    ): List<ErrorIncidentType> =
        transaction {
            val query = ErrorIncidentTable.selectAll()
            if (source != null) {
                query.andWhere { ErrorIncidentTable.incidentSource eq source.name }
            }

            val column =
                when (orderBy) {
                    OrderBy.LAST_SEEN_AT -> ErrorIncidentTable.lastSeenAt
                    OrderBy.OCCURRENCE_COUNT -> ErrorIncidentTable.occurrenceCount
                    OrderBy.FIRST_SEEN_AT -> ErrorIncidentTable.firstSeenAt
                }
            val sort = if (ascending) SortOrder.ASC else SortOrder.DESC
            query
                .orderBy(column to sort)
                .limit(limit.coerceIn(1, 500))
                .map { ErrorIncidentType(it) }
        }

    fun getById(id: Int): ErrorIncidentType? =
        transaction {
            ErrorIncidentTable
                .selectAll()
                .where { ErrorIncidentTable.id eq id }
                .firstOrNull()
                ?.let { ErrorIncidentType(it) }
        }

    fun deleteById(id: Int): ErrorIncidentType? =
        transaction {
            val existing =
                ErrorIncidentTable
                    .selectAll()
                    .where { ErrorIncidentTable.id eq id }
                    .firstOrNull()
                    ?.let { ErrorIncidentType(it) }
                    ?: return@transaction null
            ErrorIncidentTable.deleteWhere { ErrorIncidentTable.id eq id }
            existing
        }

    fun clearAll(): Int =
        transaction {
            ErrorIncidentTable.deleteAll()
        }
}
