package suwayomi.tachidesk.manga.impl

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryMetaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createLibraryManga

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryPrivacyTest : ApplicationTest() {
    @Test
    fun `manga in any private category is private`() {
        val onlyPublic = createLibraryManga("Public only")
        val onlyPrivate = createLibraryManga("Private only")
        val both = createLibraryManga("Both")
        val noCategory = createLibraryManga("No category")

        val publicCategory = Category.createCategory("Public")
        val privateCategory = Category.createCategory("Private")
        CategoryPrivacy.updatePrivacy(privateCategory, isPrivate = true)

        CategoryManga.addMangaToCategory(onlyPublic, publicCategory)
        CategoryManga.addMangaToCategory(onlyPrivate, privateCategory)
        CategoryManga.addMangaToCategory(both, publicCategory)
        CategoryManga.addMangaToCategory(both, privateCategory)

        assertEquals(listOf(privateCategory), CategoryPrivacy.privateCategoryIds())
        assertEquals(setOf(onlyPrivate, both), CategoryPrivacy.privateMangaIds().toSet())
        assertFalse(noCategory in CategoryPrivacy.privateMangaIds())
    }

    @Test
    fun `no private manga when no category is private`() {
        val mangaId = createLibraryManga("Visible")
        val categoryId = Category.createCategory("Public")
        CategoryManga.addMangaToCategory(mangaId, categoryId)

        assertTrue(CategoryPrivacy.privateMangaIds().isEmpty())
    }

    @Test
    fun `privacy flags can be cleared again`() {
        val categoryId = Category.createCategory("Toggle")

        CategoryPrivacy.updatePrivacy(categoryId, isPrivate = true, showWhenLocked = true)
        assertEquals(listOf(categoryId), CategoryPrivacy.privateCategoryIds())

        CategoryPrivacy.updatePrivacy(categoryId, isPrivate = false)
        assertTrue(CategoryPrivacy.privateCategoryIds().isEmpty())
    }

    @Test
    fun `password lifecycle`() {
        assertFalse(CategoryPrivacy.hasPassword())

        assertTrue(CategoryPrivacy.setPassword(null, "first"))
        assertTrue(CategoryPrivacy.hasPassword())
        assertTrue(CategoryPrivacy.verifyPassword("first"))
        assertFalse(CategoryPrivacy.verifyPassword("wrong"))
        assertFalse(CategoryPrivacy.verifyPassword(""))

        assertFalse(CategoryPrivacy.setPassword("wrong", "second"), "changing requires the current password")
        assertFalse(CategoryPrivacy.setPassword(null, "second"), "changing requires the current password")
        assertTrue(CategoryPrivacy.verifyPassword("first"))

        assertTrue(CategoryPrivacy.setPassword("first", "second"))
        assertFalse(CategoryPrivacy.verifyPassword("first"))
        assertTrue(CategoryPrivacy.verifyPassword("second"))

        assertFalse(CategoryPrivacy.setPassword("wrong", null), "removing requires the current password")
        assertTrue(CategoryPrivacy.setPassword("second", " "))
        assertFalse(CategoryPrivacy.hasPassword())
    }

    @AfterEach
    internal fun tearDown() {
        if (CategoryPrivacy.hasPassword()) {
            // the password is kept outside of the database
            CategoryPrivacy.setPassword("second", null) || CategoryPrivacy.setPassword("first", null)
        }
        clearTables(
            ChapterTable,
            CategoryMangaTable,
            CategoryMetaTable,
            MangaTable,
            CategoryTable,
        )
    }
}
