package ru.radiationx.data.updater

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApkVerifierTest {

    @Test
    fun matchesSha256_acceptsDifferentFormats() {
        val verifier = ApkVerifier(mockk<Context>(relaxed = true))
        val file = createTempFile(content = "hello")
        val expected = "2CF24DBA5FB0A30E26E83B2AC5B9E29E1B161E5C1FA7425E73043362938B9824"
        val expectedWithSeparators = "2c:f2:4d:ba:5f:b0:a3:0e:26:e8:3b:2a:c5:b9:e2:9e:1b:16:1e:5c:1f:a7:42:5e:73:04:33:62:93:8b:98:24"

        assertTrue(verifier.matchesSha256(file, expected))
        assertTrue(verifier.matchesSha256(file, expectedWithSeparators))
    }

    @Test
    fun matchesSha256_failsForWrongChecksum() {
        val verifier = ApkVerifier(mockk<Context>(relaxed = true))
        val file = createTempFile(content = "hello")

        assertFalse(verifier.matchesSha256(file, "deadbeef"))
    }

    private fun createTempFile(content: String): File {
        return kotlin.io.path.createTempFile("apk-verifier", ".tmp")
            .toFile()
            .apply {
                writeText(content)
                deleteOnExit()
            }
    }
}
