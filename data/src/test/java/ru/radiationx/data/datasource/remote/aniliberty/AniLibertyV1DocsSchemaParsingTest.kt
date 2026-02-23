package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyAuthTokenResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyOtpGetResponse
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyViewTimecode
import ru.radiationx.data.datasource.remote.aniliberty.moshi.AniLibertyMoshi

class AniLibertyV1DocsSchemaParsingTest {

    private val moshi = AniLibertyMoshi.configure(Moshi.Builder().build())

    @Test
    fun authTokenResponse_parsesDocsExampleShape() {
        val json = """{"token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example"}"""
        val adapter = moshi.adapter(AniLibertyAuthTokenResponse::class.java)

        val parsed = adapter.fromJson(json)

        assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.example", parsed?.token)
    }

    @Test
    fun otpGetResponse_parsesDocsExampleShape() {
        val json = """
            {
              "otp": {
                "code": "058701",
                "user_id": 1337,
                "device_id": "n702175b-fa52-5251-a39z-d1f4af0w1cak",
                "expired_at": "2021-09-28T19:40:26+00:00"
              },
              "remaining_time": 120
            }
        """.trimIndent()
        val adapter = moshi.adapter(AniLibertyOtpGetResponse::class.java)

        val parsed = adapter.fromJson(json)

        assertEquals("058701", parsed?.otp?.code)
        assertEquals(1337, parsed?.otp?.userId)
        assertEquals(120.0, parsed?.remainingTime)
    }

    @Test
    fun userViewTimecodes_parsesDocsTupleExample() {
        val json = """[["68d4d5c5-e3d5-419f-a21c-c511b6b251f5",743,true]]"""

        val parsed: List<AniLibertyViewTimecode> = json.fetchListOrNestedList(moshi)

        assertEquals(1, parsed.size)
        assertEquals("68d4d5c5-e3d5-419f-a21c-c511b6b251f5", parsed.first().releaseEpisodeId.value)
        assertEquals(743.0, parsed.first().time, 0.0001)
        assertTrue(parsed.first().isWatched)
    }
}
