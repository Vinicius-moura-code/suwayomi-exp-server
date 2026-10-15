package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import android.app.Application
import android.content.Context
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryMetaTable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Private categories: the flag lives in [CategoryMetaTable] ([PRIVATE]); the global unlock password is stored
 * hashed in server preferences, never in meta, since meta is readable and writable by every client.
 *
 * Manga in at least one private category is excluded from History and Updates, regardless of other categories.
 */
object CategoryPrivacy {
    const val PRIVATE = "suwayomi.private"
    const val SHOW_WHEN_LOCKED = "suwayomi.privateShowWhenLocked"
    const val TRUE_VALUE = "true"

    private const val PASSWORD_HASH_KEY = "password_hash"
    private const val HASH_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val HASH_ITERATIONS = 210_000
    private const val HASH_KEY_LENGTH = 256
    private const val SALT_LENGTH = 16

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("category_privacy", Context.MODE_PRIVATE)
    }

    fun isEnabled(value: String?): Boolean = value.equals(TRUE_VALUE, ignoreCase = true)

    /**
     * @param isPrivate null = leave unchanged; true = set meta; false = clear meta
     * @param showWhenLocked null = leave unchanged; true = set meta; false = clear meta
     */
    fun updatePrivacy(
        categoryId: Int,
        isPrivate: Boolean? = null,
        showWhenLocked: Boolean? = null,
    ) {
        isPrivate?.let { setFlag(categoryId, PRIVATE, it) }
        showWhenLocked?.let { setFlag(categoryId, SHOW_WHEN_LOCKED, it) }
    }

    private fun setFlag(
        categoryId: Int,
        key: String,
        enabled: Boolean,
    ) {
        if (enabled) {
            Category.modifyMeta(categoryId, key, TRUE_VALUE)
        } else {
            transaction {
                CategoryMetaTable.deleteWhere {
                    (CategoryMetaTable.ref eq categoryId) and (CategoryMetaTable.key eq key)
                }
            }
        }
    }

    fun privateCategoryIds(): List<Int> =
        transaction {
            CategoryMetaTable
                .select(CategoryMetaTable.ref)
                .where { (CategoryMetaTable.key eq PRIVATE) and (CategoryMetaTable.value eq TRUE_VALUE) }
                .map { it[CategoryMetaTable.ref].value }
        }

    fun privateMangaIds(): List<Int> {
        val categoryIds = privateCategoryIds()
        if (categoryIds.isEmpty()) {
            return emptyList()
        }

        return transaction {
            CategoryMangaTable
                .select(CategoryMangaTable.manga)
                .where { CategoryMangaTable.category inList categoryIds }
                .withDistinct()
                .map { it[CategoryMangaTable.manga].value }
        }
    }

    fun hasPassword(): Boolean = preferences.getString(PASSWORD_HASH_KEY, null) != null

    fun verifyPassword(password: String): Boolean {
        val stored = preferences.getString(PASSWORD_HASH_KEY, null) ?: return false
        val parts = stored.split('$')
        if (parts.size != 3) {
            return false
        }

        val iterations = parts[0].toIntOrNull() ?: return false
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[1])
        val expected = decoder.decode(parts[2])

        return MessageDigest.isEqual(expected, hash(password, salt, iterations))
    }

    /**
     * @param currentPassword required when a password is already set
     * @param newPassword null or blank removes the password
     * @return false when [currentPassword] does not match
     */
    fun setPassword(
        currentPassword: String?,
        newPassword: String?,
    ): Boolean {
        if (hasPassword() && !verifyPassword(currentPassword.orEmpty())) {
            return false
        }

        if (newPassword.isNullOrBlank()) {
            preferences.edit().remove(PASSWORD_HASH_KEY).apply()
            return true
        }

        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val encoder = Base64.getEncoder()
        val encoded =
            listOf(
                HASH_ITERATIONS.toString(),
                encoder.encodeToString(salt),
                encoder.encodeToString(hash(newPassword, salt, HASH_ITERATIONS)),
            ).joinToString("$")
        preferences.edit().putString(PASSWORD_HASH_KEY, encoded).apply()

        return true
    }

    private fun hash(
        password: String,
        salt: ByteArray,
        iterations: Int,
    ): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, HASH_KEY_LENGTH)
        try {
            return SecretKeyFactory.getInstance(HASH_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
