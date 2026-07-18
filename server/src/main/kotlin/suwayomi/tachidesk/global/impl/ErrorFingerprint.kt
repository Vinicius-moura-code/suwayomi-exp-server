package suwayomi.tachidesk.global.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import java.security.MessageDigest

object ErrorFingerprint {
    private const val MAX_MESSAGE_LEN = 500
    private const val TOP_FRAMES = 8
    private val digitRegex = Regex("\\d+")

    fun of(throwable: Throwable): String {
        val root = rootCause(throwable)
        val className = root.javaClass.name
        val message = normalizeMessage(root.message)
        val frames =
            root.stackTrace
                .take(TOP_FRAMES)
                .joinToString("\n") { "${it.className}.${it.methodName}" }
        return sha256Hex("$className\n$message\n$frames")
    }

    fun normalizeMessage(message: String?): String {
        if (message.isNullOrBlank()) return ""
        return message
            .trim()
            .replace(digitRegex, "#")
            .take(MAX_MESSAGE_LEN)
    }

    fun rootCause(throwable: Throwable): Throwable {
        var current = throwable
        val seen = mutableSetOf<Throwable>()
        while (current.cause != null && current.cause !== current && seen.add(current)) {
            current = current.cause!!
        }
        return current
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
