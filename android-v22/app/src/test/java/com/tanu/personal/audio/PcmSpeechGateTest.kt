package com.tanu.personal.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PcmSpeechGateTest {
    @Test
    fun silenceIsRejected() {
        val file = tempPcm(ShortArray(16000 * 2))
        assertFalse(PcmSpeechGate.hasLikelySpeech(file))
        file.parentFile?.deleteRecursively()
    }

    @Test
    fun voicedSignalIsAccepted() {
        val samples = ShortArray(16000 * 2) { i -> if (i % 8 < 4) 5000 else -5000 }
        val file = tempPcm(samples)
        assertTrue(PcmSpeechGate.hasLikelySpeech(file))
        file.parentFile?.deleteRecursively()
    }

    @Test
    fun missingFileIsRejected() {
        val file = File("/definitely/not/a/tanu/chunk.pcm")
        assertFalse(PcmSpeechGate.hasLikelySpeech(file))
    }

    private fun tempPcm(samples: ShortArray): File {
        val dir = Files.createTempDirectory("tanu-gate-test").toFile()
        val file = File(dir, "audio.pcm")
        val bytes = ByteArray(samples.size * 2)
        var j = 0
        samples.forEach { sample ->
            bytes[j++] = (sample.toInt() and 0xff).toByte()
            bytes[j++] = ((sample.toInt() shr 8) and 0xff).toByte()
        }
        file.writeBytes(bytes)
        return file
    }
}
