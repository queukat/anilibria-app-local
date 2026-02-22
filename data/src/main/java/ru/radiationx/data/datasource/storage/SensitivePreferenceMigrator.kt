package ru.radiationx.data.datasource.storage

internal interface StringKeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

internal object SensitivePreferenceMigrator {
    fun migrateKeys(
        keys: Iterable<String>,
        source: StringKeyValueStore,
        target: StringKeyValueStore,
    ) {
        keys.forEach { key ->
            val value = source.getString(key) ?: return@forEach
            target.putString(key, value)
            source.remove(key)
        }
    }
}
