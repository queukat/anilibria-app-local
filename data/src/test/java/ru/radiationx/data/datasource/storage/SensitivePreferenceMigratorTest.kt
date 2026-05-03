package ru.radiationx.data.datasource.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensitivePreferenceMigratorTest {
    @Test
    fun migrateKeys_movesOnlyRequestedEntries_andRemovesFromSource() {
        val source =
            FakeStore(
                mutableMapOf(
                    "token" to "abc",
                    "cookie_PHPSESSID" to "cookie-value",
                    "other" to "keep",
                ),
            )
        val target = FakeStore(mutableMapOf())

        SensitivePreferenceMigrator.migrateKeys(
            keys = listOf("token", "cookie_PHPSESSID"),
            source = source,
            target = target,
        )

        assertEquals("abc", target.getString("token"))
        assertEquals("cookie-value", target.getString("cookie_PHPSESSID"))
        assertNull(source.getString("token"))
        assertNull(source.getString("cookie_PHPSESSID"))
        assertEquals("keep", source.getString("other"))
    }
}

private class FakeStore(
    private val storage: MutableMap<String, String>,
) : StringKeyValueStore {
    override fun getString(key: String): String? = storage[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        storage[key] = value
    }

    override fun remove(key: String) {
        storage.remove(key)
    }
}
