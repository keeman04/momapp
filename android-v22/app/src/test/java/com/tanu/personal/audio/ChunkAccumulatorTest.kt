package com.tanu.personal.audio

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class ChunkAccumulatorTest {
    private fun pcmSeconds(seconds: Int): ByteArray = ByteArray(seconds * 16000 * 2)

    @Test
    fun exactFullChunkDoesNotEmitDuplicateOverlapTail() {
        val dir = Files.createTempDirectory("tanu-chunk-test").toFile()
        val acc = ChunkAccumulator(dir, "meeting")
        val chunks = acc.add(pcmSeconds(20), 20 * 16000 * 2)
        assertEquals(1, chunks.size)
        assertEquals(0L, chunks[0].startMs)
        assertEquals(20_000L, chunks[0].endMs)
        assertNull("retained 1s overlap must not become a duplicate final chunk", acc.flush())
        dir.deleteRecursively()
    }

    @Test
    fun consecutiveChunksAdvanceByNineteenSecondsWithOneSecondOverlap() {
        val dir = Files.createTempDirectory("tanu-chunk-test").toFile()
        val acc = ChunkAccumulator(dir, "meeting")
        val chunks = acc.add(pcmSeconds(39), 39 * 16000 * 2)
        assertEquals(2, chunks.size)
        assertEquals(0L, chunks[0].startMs)
        assertEquals(20_000L, chunks[0].endMs)
        assertEquals(19_000L, chunks[1].startMs)
        assertEquals(39_000L, chunks[1].endMs)
        assertNull(acc.flush())
        dir.deleteRecursively()
    }

    @Test
    fun finalTailKeepsOverlapAndFreshAudio() {
        val dir = Files.createTempDirectory("tanu-chunk-test").toFile()
        val acc = ChunkAccumulator(dir, "meeting")
        val chunks = acc.add(pcmSeconds(25), 25 * 16000 * 2)
        assertEquals(1, chunks.size)
        val tail = acc.flush()
        assertNotNull(tail)
        assertEquals(19_000L, tail!!.startMs)
        assertEquals(25_000L, tail.endMs)
        dir.deleteRecursively()
    }

    @Test
    fun fourHourStreamKeepsMonotonicBoundedChunks() {
        val dir = Files.createTempDirectory("tanu-long-meeting-test").toFile()
        val sampleRate = 10
        val acc = ChunkAccumulator(dir, "long", seconds = 20, overlapSeconds = 1, sampleRate = sampleRate)
        val input = ByteArray(4 * 60 * 60 * sampleRate * 2)
        val emitted = mutableListOf<ChunkAccumulator.Chunk>()
        var offset = 0
        val feedSizes = intArrayOf(17, 53, 211, 409, 997)
        var feedIndex = 0
        while (offset < input.size) {
            val n = minOf(feedSizes[feedIndex++ % feedSizes.size], input.size - offset)
            emitted += acc.add(input.copyOfRange(offset, offset + n), n)
            offset += n
        }
        acc.flush()?.let(emitted::add)

        assertTrue(emitted.size > 700)
        assertEquals(emitted.indices.toList(), emitted.map { it.index })
        emitted.zipWithNext().forEach { (a, b) ->
            assertEquals(19_000L, b.startMs - a.startMs)
            assertTrue(b.endMs > b.startMs)
            assertTrue(b.file.length() <= 20L * sampleRate * 2)
        }
        assertTrue(emitted.last().endMs <= 4L * 60L * 60L * 1000L)
        dir.deleteRecursively()
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidPcmLength() {
        val dir = Files.createTempDirectory("tanu-chunk-test").toFile()
        ChunkAccumulator(dir, "meeting").add(ByteArray(8), 9)
    }
}
