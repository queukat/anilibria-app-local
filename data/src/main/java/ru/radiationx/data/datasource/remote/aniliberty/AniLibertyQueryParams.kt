package ru.radiationx.data.datasource.remote.aniliberty

internal class AniLibertyQueryParams private constructor(
    private val args: LinkedHashMap<String, String>,
) {
    fun toMap(): Map<String, String> = args

    class Builder {
        private val args: LinkedHashMap<String, String> = linkedMapOf()

        fun put(key: String, value: String) {
            args[key] = value
        }

        fun putIfNotBlank(key: String, value: String?) {
            if (!value.isNullOrBlank()) args[key] = value
        }

        fun putIfPositive(key: String, value: Int?) {
            if (value != null && value > 0) args[key] = value.toString()
        }

        fun putIfPositive(key: String, value: Long?) {
            if (value != null && value > 0L) args[key] = value.toString()
        }

        fun putCsv(key: String, items: List<Int>?) {
            if (!items.isNullOrEmpty()) args[key] = items.joinToString(",")
        }

        fun <T> putCsv(key: String, items: List<T>?, mapper: (T) -> String) {
            if (!items.isNullOrEmpty()) args[key] = items.joinToString(",") { mapper(it) }
        }

        fun applyFields(fields: AniLibertyFieldSpec?) {
            putIfNotBlank("include", fields?.includeParam())
            putIfNotBlank("exclude", fields?.excludeParam())
        }

        fun build(): AniLibertyQueryParams = AniLibertyQueryParams(args)
    }

    companion object {
        inline fun build(block: Builder.() -> Unit): Map<String, String> =
            Builder().apply(block).build().toMap()
    }
}
