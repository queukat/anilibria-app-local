package ru.radiationx.data.datasource.remote.aniliberty.moshi

import com.squareup.moshi.Moshi

object AniLibertyMoshi {
    fun configure(base: Moshi): Moshi {
        return base.newBuilder()
            .add(AniLibertyValueAdapters)
            .add(AniLibertyTupleAdapterFactory)
            .build()
    }
}
