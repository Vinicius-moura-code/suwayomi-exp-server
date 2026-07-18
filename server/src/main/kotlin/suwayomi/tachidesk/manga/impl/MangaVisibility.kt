package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.model.table.MangaMetaTable

/**
 * Client display prefs stored in [MangaMetaTable] (no dedicated columns).
 *
 * Keys:
 * - [HIDE_FROM_HISTORY] — omit manga from History (`chapters` with [excludeHiddenFromHistory])
 * - [HIDE_FROM_UPDATES] — omit manga from Updates feed
 */
object MangaVisibility {
    const val HIDE_FROM_HISTORY = "suwayomi.hideFromHistory"
    const val HIDE_FROM_UPDATES = "suwayomi.hideFromUpdates"
    const val TRUE_VALUE = "true"

    fun isEnabled(value: String?): Boolean = value.equals(TRUE_VALUE, ignoreCase = true)

    fun isEnabledInMeta(
        meta: Map<String, String>,
        key: String,
    ): Boolean = isEnabled(meta[key])

    fun hideFromHistory(mangaId: Int): Boolean = isEnabledInMeta(Manga.getMangaMetaMap(mangaId), HIDE_FROM_HISTORY)

    fun hideFromUpdates(mangaId: Int): Boolean = isEnabledInMeta(Manga.getMangaMetaMap(mangaId), HIDE_FROM_UPDATES)

    fun mangaIdsWithFlag(key: String): List<Int> =
        transaction {
            MangaMetaTable
                .select(MangaMetaTable.ref)
                .where { (MangaMetaTable.key eq key) and (MangaMetaTable.value eq TRUE_VALUE) }
                .map { it[MangaMetaTable.ref].value }
        }

    /**
     * @param hideFromHistory null = leave unchanged; true = set meta; false = clear meta
     * @param hideFromUpdates null = leave unchanged; true = set meta; false = clear meta
     */
    fun updateVisibility(
        mangaId: Int,
        hideFromHistory: Boolean? = null,
        hideFromUpdates: Boolean? = null,
    ) {
        hideFromHistory?.let { setFlag(mangaId, HIDE_FROM_HISTORY, it) }
        hideFromUpdates?.let { setFlag(mangaId, HIDE_FROM_UPDATES, it) }
    }

    private fun setFlag(
        mangaId: Int,
        key: String,
        enabled: Boolean,
    ) {
        if (enabled) {
            Manga.modifyMangaMeta(mangaId, key, TRUE_VALUE)
        } else {
            transaction {
                MangaMetaTable.deleteWhere {
                    (MangaMetaTable.ref eq mangaId) and (MangaMetaTable.key eq key)
                }
            }
        }
    }
}
