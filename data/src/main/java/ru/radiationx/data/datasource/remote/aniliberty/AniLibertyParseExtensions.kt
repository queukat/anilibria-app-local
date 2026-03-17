package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

inline fun <reified T> String.fetchListOrNestedList(moshi: Moshi): List<T> {
    val listType = Types.newParameterizedType(List::class.java, T::class.java)
    val listAdapter = moshi.adapter<List<T>>(listType)

    val direct = runCatching { listAdapter.fromJson(this) }.getOrNull()
    if (direct != null) return direct

    val nestedType = Types.newParameterizedType(List::class.java, listType)
    val nestedAdapter = moshi.adapter<List<List<T>>>(nestedType)
    val nested = nestedAdapter.fromJson(this) ?: emptyList()
    return nested.flatten()
}

object AniLibertyJsonListParser {
    inline fun <reified T> fetchListOrNestedList(
        json: String,
        moshi: Moshi,
    ): List<T> = json.fetchListOrNestedList(moshi)
}
