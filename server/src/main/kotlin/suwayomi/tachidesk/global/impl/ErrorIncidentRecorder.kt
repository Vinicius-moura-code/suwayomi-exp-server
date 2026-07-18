package suwayomi.tachidesk.global.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.global.model.table.ErrorIncidentTable
import suwayomi.tachidesk.graphql.types.ErrorIncidentSource
import java.time.Instant

/**
 * Best-effort persistence of server exceptions for local observability.
 * Failures while recording must never affect the original request.
 */
object ErrorIncidentRecorder {
    private val logger = KotlinLogging.logger {}
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val MAX_STACK_CHARS = 32 * 1024

    fun record(
        throwable: Throwable,
        source: ErrorIncidentSource,
        context: Map<String, String> = emptyMap(),
    ) {
        scope.launch {
            try {
                persist(throwable, source, context)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to persist error incident" }
            }
        }
    }

    fun recordSync(
        throwable: Throwable,
        source: ErrorIncidentSource,
        context: Map<String, String> = emptyMap(),
    ) {
        try {
            persist(throwable, source, context)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to persist error incident" }
        }
    }

    private fun persist(
        throwable: Throwable,
        source: ErrorIncidentSource,
        context: Map<String, String>,
    ) {
        val root = ErrorFingerprint.rootCause(throwable)
        val fingerprint = ErrorFingerprint.of(throwable)
        val now = Instant.now().epochSecond
        val exceptionClass = root.javaClass.name
        val message = (root.message ?: "").take(2048)
        val stackTrace = throwable.stackTraceToString().take(MAX_STACK_CHARS)
        val contextJson = encodeContext(context)

        transaction {
            val existing =
                ErrorIncidentTable
                    .selectAll()
                    .where { ErrorIncidentTable.fingerprint eq fingerprint }
                    .firstOrNull()

            if (existing != null) {
                ErrorIncidentTable.update({ ErrorIncidentTable.fingerprint eq fingerprint }) {
                    it[ErrorIncidentTable.occurrenceCount] = existing[ErrorIncidentTable.occurrenceCount] + 1
                    it[ErrorIncidentTable.lastSeenAt] = now
                    it[ErrorIncidentTable.message] = message
                    it[ErrorIncidentTable.stackTrace] = stackTrace
                    it[ErrorIncidentTable.context] = contextJson
                    it[ErrorIncidentTable.incidentSource] = source.name
                }
            } else {
                ErrorIncidentTable.insert {
                    it[ErrorIncidentTable.fingerprint] = fingerprint
                    it[ErrorIncidentTable.exceptionClass] = exceptionClass
                    it[ErrorIncidentTable.message] = message
                    it[ErrorIncidentTable.stackTrace] = stackTrace
                    it[ErrorIncidentTable.incidentSource] = source.name
                    it[ErrorIncidentTable.context] = contextJson
                    it[ErrorIncidentTable.occurrenceCount] = 1
                    it[ErrorIncidentTable.firstSeenAt] = now
                    it[ErrorIncidentTable.lastSeenAt] = now
                }
            }
        }
    }

    private fun encodeContext(context: Map<String, String>): String {
        if (context.isEmpty()) return "{}"
        return JsonObject(context.mapValues { JsonPrimitive(it.value.take(512)) }).toString().take(4096)
    }
}
