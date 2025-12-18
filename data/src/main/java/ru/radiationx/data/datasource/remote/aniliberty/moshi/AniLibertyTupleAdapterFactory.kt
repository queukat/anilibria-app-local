package ru.radiationx.data.datasource.remote.aniliberty.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionIdItem
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyViewTimecode
import java.lang.reflect.Type

object AniLibertyTupleAdapterFactory : JsonAdapter.Factory {
    override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (annotations.isNotEmpty()) return null
        val raw = Types.getRawType(type)

        return when (raw) {
            AniLibertyViewTimecode::class.java -> AniLibertyViewTimecodeJsonAdapter()
            AniLibertyCollectionIdItem::class.java -> AniLibertyCollectionIdItemJsonAdapter()
            else -> null
        }
    }
}

private class AniLibertyViewTimecodeJsonAdapter : JsonAdapter<AniLibertyViewTimecode>() {

    override fun fromJson(reader: JsonReader): AniLibertyViewTimecode {
        reader.beginArray()

        val episodeId = reader.nextString()
        val time = reader.nextDouble()
        val isWatched = reader.nextBoolean()

        // если сервер внезапно пришлет больше значений, пропустим их
        while (reader.hasNext()) {
            reader.skipValue()
        }

        reader.endArray()
        return AniLibertyViewTimecode(
            releaseEpisodeId = episodeId,
            time = time,
            isWatched = isWatched,
        )
    }

    override fun toJson(writer: JsonWriter, value: AniLibertyViewTimecode?) {
        if (value == null) throw JsonDataException("AniLibertyViewTimecode was null")
        writer.beginArray()
        writer.value(value.releaseEpisodeId)
        writer.value(value.time)
        writer.value(value.isWatched)
        writer.endArray()
    }
}

private class AniLibertyCollectionIdItemJsonAdapter : JsonAdapter<AniLibertyCollectionIdItem>() {

    override fun fromJson(reader: JsonReader): AniLibertyCollectionIdItem {
        reader.beginArray()

        // в спеках number, поэтому читаем double и приводим к int
        val releaseId = reader.nextDouble().toInt()
        val type = reader.nextString()

        while (reader.hasNext()) {
            reader.skipValue()
        }

        reader.endArray()
        return AniLibertyCollectionIdItem(
            releaseId = releaseId,
            typeOfCollection = type,
        )
    }

    override fun toJson(writer: JsonWriter, value: AniLibertyCollectionIdItem?) {
        if (value == null) throw JsonDataException("AniLibertyCollectionIdItem was null")
        writer.beginArray()
        writer.value(value.releaseId)
        writer.value(value.typeOfCollection)
        writer.endArray()
    }
}
