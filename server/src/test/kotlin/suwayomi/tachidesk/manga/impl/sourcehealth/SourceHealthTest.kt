package suwayomi.tachidesk.manga.impl.sourcehealth

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceHealthFailureTable
import suwayomi.tachidesk.manga.model.table.SourceHealthTable
import suwayomi.tachidesk.manga.model.table.SourceMetaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourceHealthTest : ApplicationTest() {
    private class FakeSource(
        override val id: Long,
    ) : Source {
        override val name = "Fake $id"
        override val supportsLatest = false

        override suspend fun getPopularManga(page: Int): MangasPage = TODO()

        override suspend fun getLatestUpdates(page: Int): MangasPage = TODO()

        override suspend fun getSearchManga(
            page: Int,
            query: String,
            filters: FilterList,
        ): MangasPage = TODO()

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = TODO()

        override suspend fun getPageList(chapter: SChapter): List<Page> = TODO()
    }

    private val touchedSources = mutableSetOf<Long>()

    private fun newSourceId(): Long = (1000L + touchedSources.size).also { touchedSources += it }

    private fun createSource(sourceId: Long) {
        transaction {
            val extensionId =
                ExtensionTable.insertAndGetId {
                    it[name] = "Extension $sourceId"
                    it[pkgName] = "pkg.$sourceId"
                    it[extensionLib] = "1.6"
                    it[versionName] = "1"
                    it[versionCode] = 1
                    it[lang] = "en"
                    it[contentWarning] = 0
                }
            SourceTable.insert {
                it[id] = EntityID(sourceId, SourceTable)
                it[name] = "Source $sourceId"
                it[lang] = "en"
                it[extension] = extensionId
            }
        }
    }

    private fun record(
        sourceId: Long,
        stage: SourceHealthStage,
        outcome: SourceHealthOutcome,
        times: Int = 1,
        latencyMs: Long = 100,
    ) = repeat(times) { SourceHealthRecorder.record(sourceId, stage, outcome, latencyMs) }

    private fun rows(
        stage: SourceHealthStage,
        outcome: SourceHealthOutcome,
        count: Long,
        bucket: Int = 0,
    ) = SourceHealthService.HealthRow(stage, outcome, bucket, count)

    // region pure calculations

    @Test
    fun `latency percentiles are interpolated inside the bucket`() {
        // bucket 2 is 500 - 1000 ms
        assertEquals(750, SourceHealthLatency.percentile(mapOf(2 to 10L), 0.5))
        assertEquals(975, SourceHealthLatency.percentile(mapOf(2 to 10L), 0.95))
        assertNull(SourceHealthLatency.percentile(emptyMap(), 0.5))

        // half of the calls are fast, half are slow
        val mixed = mapOf(0 to 10L, 6 to 10L)
        assertTrue(SourceHealthLatency.percentile(mixed, 0.5)!! <= 250)
        assertTrue(SourceHealthLatency.percentile(mixed, 0.95)!! in 5_000..8_000)
    }

    @Test
    fun `latency lands in the expected bucket`() {
        assertEquals(0, SourceHealthLatency.bucketOf(0))
        assertEquals(0, SourceHealthLatency.bucketOf(249))
        assertEquals(1, SourceHealthLatency.bucketOf(250))
        assertEquals(SourceHealthLatency.BUCKET_COUNT - 1, SourceHealthLatency.bucketOf(10 * 60_000))
    }

    @Test
    fun `errors are classified`() {
        assertEquals(SourceHealthOutcome.BLOCKED, SourceHealthClassifier.classify(HttpException(403)))
        assertEquals(SourceHealthOutcome.BLOCKED, SourceHealthClassifier.classify(HttpException(429)))
        assertEquals(SourceHealthOutcome.NOT_FOUND, SourceHealthClassifier.classify(HttpException(404)))
        assertEquals(SourceHealthOutcome.SERVER_ERROR, SourceHealthClassifier.classify(HttpException(503)))
        assertEquals(SourceHealthOutcome.TIMEOUT, SourceHealthClassifier.classify(SocketTimeoutException("timeout")))
        assertEquals(SourceHealthOutcome.NETWORK, SourceHealthClassifier.classify(UnknownHostException("example.org")))
        assertEquals(SourceHealthOutcome.BLOCKED, SourceHealthClassifier.classify(IOException("Failed to bypass Cloudflare")))
        assertEquals(SourceHealthOutcome.ERROR, SourceHealthClassifier.classify(IllegalStateException("can't parse")))

        // the cause decides, not the wrapper
        assertEquals(SourceHealthOutcome.NOT_FOUND, SourceHealthClassifier.classify(RuntimeException("wrapped", HttpException(404))))
    }

    @Test
    fun `stage stats ignore the local network and keep latency of successes only`() {
        val stats =
            SourceHealthService.stageStats(
                SourceHealthStage.CHAPTERS,
                listOf(
                    rows(SourceHealthStage.CHAPTERS, SourceHealthOutcome.OK, 8, bucket = 2),
                    rows(SourceHealthStage.CHAPTERS, SourceHealthOutcome.BLOCKED, 2, bucket = 8),
                    rows(SourceHealthStage.CHAPTERS, SourceHealthOutcome.LOCAL_NETWORK, 5),
                ),
            )

        assertEquals(10, stats.attempts)
        assertEquals(8, stats.successes)
        assertEquals(2, stats.failures)
        assertEquals(0.8, stats.deliveryRate!!, 0.0001)
        assertEquals(mapOf(SourceHealthOutcome.BLOCKED to 2L), stats.failuresByOutcome)
        assertTrue(stats.p95Ms!! < 1_000, "the slow failures must not count as latency")
    }

    private fun stage(
        stage: SourceHealthStage,
        ok: Long,
        failed: Long = 0,
        bucket: Int = 0,
    ) = SourceHealthService.stageStats(
        stage,
        listOf(
            rows(stage, SourceHealthOutcome.OK, ok, bucket),
            rows(stage, SourceHealthOutcome.ERROR, failed, bucket),
        ),
    )

    @Test
    fun `verdict needs enough data`() {
        val judgement = SourceHealthService.judge(listOf(stage(SourceHealthStage.CHAPTERS, ok = 3)))
        assertEquals(SourceHealthVerdict.INSUFFICIENT_DATA, judgement.verdict)
        assertNull(judgement.deliveryRate)

        // listing and thumbnails don't decide the verdict
        val listingOnly = SourceHealthService.judge(listOf(stage(SourceHealthStage.LISTING, ok = 50)))
        assertEquals(SourceHealthVerdict.INSUFFICIENT_DATA, listingOnly.verdict)
    }

    @Test
    fun `verdict is good when everything is delivered quickly`() {
        val judgement =
            SourceHealthService.judge(
                listOf(
                    stage(SourceHealthStage.DETAILS, ok = 10),
                    stage(SourceHealthStage.CHAPTERS, ok = 9, failed = 1),
                    stage(SourceHealthStage.PAGES, ok = 10),
                ),
            )

        assertEquals(SourceHealthVerdict.GOOD, judgement.verdict)
        assertEquals(SourceHealthStage.CHAPTERS, judgement.weakestStage)
    }

    @Test
    fun `verdict is poor when it fails often or is too slow`() {
        val failing =
            SourceHealthService.judge(
                listOf(stage(SourceHealthStage.DETAILS, ok = 5, failed = 5), stage(SourceHealthStage.CHAPTERS, ok = 6, failed = 4)),
            )
        assertEquals(SourceHealthVerdict.POOR, failing.verdict)

        // bucket 7 is 8 - 12 s
        val slow = SourceHealthService.judge(listOf(stage(SourceHealthStage.CHAPTERS, ok = 10, bucket = 7)))
        assertEquals(SourceHealthVerdict.POOR, slow.verdict)

        // bucket 5 is 3 - 5 s
        val meh = SourceHealthService.judge(listOf(stage(SourceHealthStage.CHAPTERS, ok = 10, bucket = 5)))
        assertEquals(SourceHealthVerdict.FAIR, meh.verdict)
    }

    @Test
    fun `a lot of working images can't hide broken chapters`() {
        val judgement =
            SourceHealthService.judge(
                listOf(
                    stage(SourceHealthStage.DETAILS, ok = 10),
                    stage(SourceHealthStage.CHAPTERS, ok = 5, failed = 5),
                    stage(SourceHealthStage.IMAGE, ok = 1_000),
                ),
            )

        assertEquals(SourceHealthVerdict.FAIR, judgement.verdict)
        assertEquals(SourceHealthStage.CHAPTERS, judgement.weakestStage)
    }

    // endregion

    // region recording

    @Test
    fun `measure records the outcome of the call and rethrows failures`() =
        runBlocking {
            val source = FakeSource(newSourceId())
            createSource(source.id)

            assertEquals("fine", SourceHealthRecorder.measure(source, SourceHealthStage.DETAILS) { "fine" })
            assertThrows(HttpException::class.java) {
                runBlocking { SourceHealthRecorder.measure<String>(source, SourceHealthStage.DETAILS) { throw HttpException(404) } }
            }
            SourceHealthRecorder.measure(source, SourceHealthStage.PAGES, isEmpty = { it.isEmpty() }) { emptyList<Page>() }

            val health = SourceHealthService.get(source.id)!!
            val details = health.stages.first { it.stage == SourceHealthStage.DETAILS }
            assertEquals(2, details.attempts)
            assertEquals(mapOf(SourceHealthOutcome.NOT_FOUND to 1L), details.failuresByOutcome)

            val pages = health.stages.first { it.stage == SourceHealthStage.PAGES }
            assertEquals(mapOf(SourceHealthOutcome.EMPTY to 1L), pages.failuresByOutcome)
            Unit
        }

    @Test
    fun `failures only mode doesn't count successes`() =
        runBlocking {
            val source = FakeSource(newSourceId())
            createSource(source.id)

            SourceHealthRecorder.measure(source, SourceHealthStage.IMAGE, recordSuccess = false) { "url" }
            assertNull(SourceHealthService.get(source.id)!!.stages.firstOrNull())
            Unit
        }

    @Test
    fun `cancelled calls are not recorded`() =
        runBlocking {
            val source = FakeSource(newSourceId())
            createSource(source.id)

            assertThrows(CancellationException::class.java) {
                runBlocking {
                    SourceHealthRecorder.measure<String>(source, SourceHealthStage.DETAILS) { throw CancellationException("left") }
                }
            }

            assertTrue(SourceHealthService.get(source.id)!!.stages.isEmpty())
            Unit
        }

    @Test
    fun `counts are summed per day and survive several flushes`() {
        val sourceId = newSourceId()
        createSource(sourceId)

        record(sourceId, SourceHealthStage.CHAPTERS, SourceHealthOutcome.OK, times = 3)
        SourceHealthRecorder.flush()
        record(sourceId, SourceHealthStage.CHAPTERS, SourceHealthOutcome.OK, times = 2)
        record(sourceId, SourceHealthStage.CHAPTERS, SourceHealthOutcome.TIMEOUT)

        val chapters = SourceHealthService.get(sourceId)!!.stages.single()
        assertEquals(6, chapters.attempts)
        assertEquals(5, chapters.successes)

        val trend = SourceHealthService.trend(sourceId)
        assertEquals(1, trend.size)
        assertEquals(6, trend.single().attempts)
        assertEquals(1, trend.single().failures)
    }

    @Test
    fun `trend without a source adds up all the sources`() {
        val first = newSourceId()
        val second = newSourceId()
        createSource(first)
        createSource(second)

        record(first, SourceHealthStage.CHAPTERS, SourceHealthOutcome.OK, times = 3)
        record(second, SourceHealthStage.PAGES, SourceHealthOutcome.OK, times = 2)
        record(second, SourceHealthStage.PAGES, SourceHealthOutcome.BLOCKED)

        val trend = SourceHealthService.trend(null)
        assertEquals(1, trend.size)
        assertEquals(6, trend.single().attempts)
        assertEquals(1, trend.single().failures)
        assertEquals(3, SourceHealthService.trend(first).single().attempts)
    }

    @Test
    fun `failures are kept with the manga they happened on`() {
        val sourceId = newSourceId()
        createSource(sourceId)
        val mangaId = createLibraryManga("Ghost manga")

        SourceHealthRecorder.record(sourceId, SourceHealthStage.DETAILS, SourceHealthOutcome.NOT_FOUND, 50, mangaId, "HTTP error 404")
        SourceHealthRecorder.record(sourceId, SourceHealthStage.DETAILS, SourceHealthOutcome.OK, 50, mangaId)

        val failures = SourceHealthService.failures(sourceId)
        assertEquals(1, failures.size, "successes aren't kept")
        assertEquals("Ghost manga", failures.single().mangaTitle)
        assertEquals(SourceHealthOutcome.NOT_FOUND, failures.single().outcome)

        assertEquals(1, SourceHealthService.failures(sourceId, mangaId).size)
        assertEquals(0, SourceHealthService.failures(sourceId, mangaId + 1).size)
    }

    @Test
    fun `connection failures of several sources at once are blamed on the local network`() {
        val first = newSourceId()
        val second = newSourceId()
        val third = newSourceId()
        listOf(first, second, third).forEach { createSource(it) }

        val now = System.currentTimeMillis()
        listOf(first, second, third).forEach {
            SourceHealthRecorder.record(it, SourceHealthStage.DETAILS, SourceHealthOutcome.NETWORK, 10, now = now)
        }
        // the third one made the pattern obvious, so it isn't held against the source
        val thirdStats = SourceHealthService.get(third)!!
        assertTrue(thirdStats.stages.isEmpty(), "ignored attempts aren't counted")

        // the first two were recorded before the pattern was visible
        val firstStage = SourceHealthService.get(first)!!.stages.single()
        assertEquals(1, firstStage.failures)
    }

    @Test
    fun `a single source failing to connect is held against it`() {
        val sourceId = newSourceId()
        createSource(sourceId)

        SourceHealthRecorder.record(sourceId, SourceHealthStage.DETAILS, SourceHealthOutcome.NETWORK, 10)

        val stage = SourceHealthService.get(sourceId)!!.stages.single()
        assertEquals(1, stage.failures)
    }

    @Test
    fun `reset forgets what was measured but keeps the rating`() {
        val sourceId = newSourceId()
        createSource(sourceId)
        record(sourceId, SourceHealthStage.CHAPTERS, SourceHealthOutcome.ERROR, times = 3)
        SourceHealthService.setRating(sourceId, 4, "good scan")
        SourceHealthRecorder.record(sourceId, SourceHealthStage.CHAPTERS, SourceHealthOutcome.ERROR, 10, message = "boom")

        SourceHealthService.reset(sourceId)

        val health = SourceHealthService.get(sourceId)!!
        assertTrue(health.stages.isEmpty())
        assertTrue(SourceHealthService.failures(sourceId).isEmpty())
        assertEquals(4, health.rating)
    }

    // endregion

    // region rating and usage

    @Test
    fun `rating can be set, changed and removed`() {
        val sourceId = newSourceId()
        createSource(sourceId)

        SourceHealthService.setRating(sourceId, 5, "  best  ")
        SourceHealthService.get(sourceId)!!.let {
            assertEquals(5, it.rating)
            assertEquals("best", it.ratingNote)
        }

        SourceHealthService.setRating(sourceId, 2, null)
        SourceHealthService.get(sourceId)!!.let {
            assertEquals(2, it.rating)
            assertNull(it.ratingNote)
        }

        SourceHealthService.setRating(sourceId, null, null)
        assertNull(SourceHealthService.get(sourceId)!!.rating)

        assertThrows(IllegalArgumentException::class.java) { SourceHealthService.setRating(sourceId, 6, null) }
        assertThrows(IllegalArgumentException::class.java) { SourceHealthService.setRating(sourceId, 0, null) }
    }

    @Test
    fun `list only has sources that were used or rated`() {
        val measured = newSourceId()
        val rated = newSourceId()
        val read = newSourceId()
        val untouched = newSourceId()
        listOf(measured, rated, read, untouched).forEach { createSource(it) }

        record(measured, SourceHealthStage.DETAILS, SourceHealthOutcome.OK)
        SourceHealthService.setRating(rated, 3, null)
        val mangaId = createLibraryManga("Read manga")
        transaction {
            MangaTable.update({ MangaTable.id eq mangaId }) { it[sourceReference] = read }
        }
        createChapters(mangaId, amount = 2, read = true)
        transaction {
            ChapterTable.update({ ChapterTable.manga eq mangaId }) { it[lastReadAt] = Instant.now().epochSecond }
        }

        val listed = SourceHealthService.list().map { it.sourceId }.toSet()
        assertEquals(setOf(measured, rated, read), listed)
        assertFalse(untouched in listed)

        val usage = SourceHealthService.list().first { it.sourceId == read }.usage
        assertEquals(2, usage.chaptersRead)
        assertEquals(1, usage.mangasRead)
        assertNotNull(usage.lastReadAt)
    }

    // endregion

    @AfterEach
    internal fun tearDown() {
        touchedSources.forEach { SourceHealthRecorder.reset(it) }
        touchedSources.clear()
        clearTables(
            SourceHealthTable,
            SourceHealthFailureTable,
            SourceMetaTable,
            ChapterTable,
            MangaTable,
            SourceTable,
            ExtensionTable,
        )
    }
}
