package suwayomi.tachidesk.global.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ErrorFingerprintTest {
    @Test
    fun `same exception shape yields same fingerprint`() {
        val first = IllegalStateException("chapter 42 failed")
        val second = IllegalStateException("chapter 99 failed")
        // Digits are normalized, so these share a fingerprint
        assertEquals(ErrorFingerprint.of(first), ErrorFingerprint.of(second))
    }

    @Test
    fun `different exception classes yield different fingerprints`() {
        val a = IllegalStateException("boom")
        val b = IllegalArgumentException("boom")
        assertNotEquals(ErrorFingerprint.of(a), ErrorFingerprint.of(b))
    }

    @Test
    fun `different messages yield different fingerprints`() {
        val a = IllegalStateException("alpha")
        val b = IllegalStateException("beta")
        assertNotEquals(ErrorFingerprint.of(a), ErrorFingerprint.of(b))
    }

    @Test
    fun `normalizeMessage collapses digits`() {
        assertEquals("manga # missing", ErrorFingerprint.normalizeMessage("manga 123 missing"))
        assertEquals("", ErrorFingerprint.normalizeMessage(null))
        assertEquals("", ErrorFingerprint.normalizeMessage("   "))
    }
}
