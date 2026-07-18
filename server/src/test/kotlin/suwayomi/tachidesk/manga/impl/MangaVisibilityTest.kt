package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MangaVisibilityTest {
    @Test
    fun `isEnabled accepts true case-insensitively`() {
        assertTrue(MangaVisibility.isEnabled("true"))
        assertTrue(MangaVisibility.isEnabled("TRUE"))
        assertFalse(MangaVisibility.isEnabled("false"))
        assertFalse(MangaVisibility.isEnabled(null))
        assertFalse(MangaVisibility.isEnabled(""))
    }

    @Test
    fun `isEnabledInMeta reads hide keys`() {
        val meta =
            mapOf(
                MangaVisibility.HIDE_FROM_HISTORY to MangaVisibility.TRUE_VALUE,
                MangaVisibility.HIDE_FROM_UPDATES to "false",
            )
        assertTrue(MangaVisibility.isEnabledInMeta(meta, MangaVisibility.HIDE_FROM_HISTORY))
        assertFalse(MangaVisibility.isEnabledInMeta(meta, MangaVisibility.HIDE_FROM_UPDATES))
        assertFalse(MangaVisibility.isEnabledInMeta(emptyMap(), MangaVisibility.HIDE_FROM_HISTORY))
    }
}
