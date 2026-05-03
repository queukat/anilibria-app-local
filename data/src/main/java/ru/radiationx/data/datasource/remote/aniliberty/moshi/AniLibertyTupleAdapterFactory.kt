package ru.radiationx.data.datasource.remote.aniliberty.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyCollectionType
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseEpisodeId
import ru.radiationx.data.datasource.remote.aniliberty.AniLibertyReleaseId
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyCollectionIdItem
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyEpisodeTimecode
import ru.radiationx.data.datasource.remote.aniliberty.dto.AniLibertyReleaseEpisodeTimecode
import java.lang.reflect.Type
import kotlin.math.roundToInt

object AniLibertyTupleAdapterFactory : JsonAdapter.Factory {
    override fun create(
        type: Type,
        annotations: Set<Annotation>,
        moshi: Moshi,
    ): JsonAdapter<*>? {
        if (annotations.isNotEmpty()) return null
        val raw = Types.getRawType(type)

        return when (raw) {
            AniLibertyReleaseEpisodeTimecode::class.java -> AniLibertyReleaseEpisodeTimecodeJsonAdapter()
            AniLibertyEpisodeTimecode::class.java -> AniLibertyEpisodeTimecodeJsonAdapter()
            AniLibertyCollectionIdItem::class.java -> AniLibertyCollectionIdItemJsonAdapter()
            else -> null
        }
    }
}

private fun JsonReader.readIntFlexible(fieldName: String): Int {
    return when (peek()) {
        JsonReader.Token.NUMBER -> {
            val d = nextDouble()
            val i = d.roundToInt()
            if (d != i.toDouble()) throw JsonDataException("$fieldName: expected integer-like number, got $d")
            i
        }
        JsonReader.Token.STRING -> {
            val s = nextString()
            s.toIntOrNull() ?: throw JsonDataException("$fieldName: can't parse int from '$s'")
        }
        else -> throw JsonDataException("$fieldName: expected number or string, was ${peek()}")
    }
}

private class AniLibertyReleaseEpisodeTimecodeJsonAdapter : JsonAdapter<AniLibertyReleaseEpisodeTimecode>() {
    override fun fromJson(reader: JsonReader): AniLibertyReleaseEpisodeTimecode {
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_ARRAY -> fromTuple(reader)
            JsonReader.Token.BEGIN_OBJECT -> fromObject(reader)
            else -> throw JsonDataException("AniLibertyReleaseEpisodeTimecode: expected array or object, was ${reader.peek()}")
        }
    }

    private fun fromTuple(reader: JsonReader): AniLibertyReleaseEpisodeTimecode {
        reader.beginArray()

        if (!reader.hasNext()) throw JsonDataException("AniLibertyReleaseEpisodeTimecode: empty tuple")
        val episodeId = reader.nextString()

        if (!reader.hasNext()) throw JsonDataException("AniLibertyReleaseEpisodeTimecode: missing time")
        val time = reader.nextDouble()

        if (!reader.hasNext()) throw JsonDataException("AniLibertyReleaseEpisodeTimecode: missing isWatched")
        val isWatched = reader.nextBoolean()

        while (reader.hasNext()) reader.skipValue()
        reader.endArray()

        return AniLibertyReleaseEpisodeTimecode(
            releaseEpisodeId = AniLibertyReleaseEpisodeId(episodeId),
            time = time,
            isWatched = isWatched,
        )
    }

    private fun fromObject(reader: JsonReader): AniLibertyReleaseEpisodeTimecode {
        reader.beginObject()

        var episodeId: String? = null
        var time: Double? = null
        var isWatched: Boolean? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "release_episode_id", "releaseEpisodeId" -> episodeId = reader.nextString()
                "time" -> time = reader.nextDouble()
                "is_watched", "isWatched" -> isWatched = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }

        reader.endObject()

        val safeEpisodeId = episodeId ?: throw JsonDataException("AniLibertyReleaseEpisodeTimecode: missing release_episode_id")
        val safeTime = time ?: throw JsonDataException("AniLibertyReleaseEpisodeTimecode: missing time")
        val safeIsWatched = isWatched ?: throw JsonDataException("AniLibertyReleaseEpisodeTimecode: missing is_watched")

        return AniLibertyReleaseEpisodeTimecode(
            releaseEpisodeId = AniLibertyReleaseEpisodeId(safeEpisodeId),
            time = safeTime,
            isWatched = safeIsWatched,
        )
    }

    override fun toJson(
        writer: JsonWriter,
        value: AniLibertyReleaseEpisodeTimecode?,
    ) {
        if (value == null) throw JsonDataException("AniLibertyReleaseEpisodeTimecode was null")
        writer.beginArray()
        writer.value(value.releaseEpisodeId.value)
        writer.value(value.time)
        writer.value(value.isWatched)
        writer.endArray()
    }
}

private class AniLibertyEpisodeTimecodeJsonAdapter : JsonAdapter<AniLibertyEpisodeTimecode>() {
    override fun fromJson(reader: JsonReader): AniLibertyEpisodeTimecode {
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_ARRAY -> fromTuple(reader)
            JsonReader.Token.BEGIN_OBJECT -> fromObject(reader)
            else -> throw JsonDataException("AniLibertyEpisodeTimecode: expected array or object, was ${reader.peek()}")
        }
    }

    private fun fromTuple(reader: JsonReader): AniLibertyEpisodeTimecode {
        reader.beginArray()
        if (!reader.hasNext()) throw JsonDataException("AniLibertyEpisodeTimecode: empty tuple")

        val firstToken = reader.peek()
        val time =
            when (firstToken) {
                JsonReader.Token.STRING -> {
                    reader.skipValue()
                    if (!reader.hasNext()) throw JsonDataException("AniLibertyEpisodeTimecode: missing time after id")
                    reader.nextDouble()
                }
                JsonReader.Token.NUMBER -> reader.nextDouble()
                else -> throw JsonDataException("AniLibertyEpisodeTimecode: unexpected token for first item: $firstToken")
            }

        if (!reader.hasNext()) throw JsonDataException("AniLibertyEpisodeTimecode: missing isWatched")
        val isWatched = reader.nextBoolean()

        while (reader.hasNext()) reader.skipValue()
        reader.endArray()

        return AniLibertyEpisodeTimecode(time = time, isWatched = isWatched)
    }

    private fun fromObject(reader: JsonReader): AniLibertyEpisodeTimecode {
        reader.beginObject()

        var time: Double? = null
        var isWatched: Boolean? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "time" -> time = reader.nextDouble()
                "is_watched", "isWatched" -> isWatched = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }

        reader.endObject()

        val safeTime = time ?: throw JsonDataException("AniLibertyEpisodeTimecode: missing time")
        val safeIsWatched = isWatched ?: throw JsonDataException("AniLibertyEpisodeTimecode: missing is_watched")

        return AniLibertyEpisodeTimecode(time = safeTime, isWatched = safeIsWatched)
    }

    override fun toJson(
        writer: JsonWriter,
        value: AniLibertyEpisodeTimecode?,
    ) {
        if (value == null) throw JsonDataException("AniLibertyEpisodeTimecode was null")
        writer.beginArray()
        writer.value(value.time)
        writer.value(value.isWatched)
        writer.endArray()
    }
}

private class AniLibertyCollectionIdItemJsonAdapter : JsonAdapter<AniLibertyCollectionIdItem>() {
    override fun fromJson(reader: JsonReader): AniLibertyCollectionIdItem {
        reader.beginArray()

        if (!reader.hasNext()) throw JsonDataException("AniLibertyCollectionIdItem: empty tuple")
        val releaseIdInt = reader.readIntFlexible("AniLibertyCollectionIdItem.releaseId")

        if (!reader.hasNext()) throw JsonDataException("AniLibertyCollectionIdItem: missing type")
        val type = reader.nextString()

        while (reader.hasNext()) reader.skipValue()
        reader.endArray()

        return AniLibertyCollectionIdItem(
            releaseId = AniLibertyReleaseId(releaseIdInt),
            typeOfCollection = AniLibertyCollectionType(type),
        )
    }

    override fun toJson(
        writer: JsonWriter,
        value: AniLibertyCollectionIdItem?,
    ) {
        if (value == null) throw JsonDataException("AniLibertyCollectionIdItem was null")
        writer.beginArray()
        writer.value(value.releaseId.value)
        writer.value(value.typeOfCollection.value)
        writer.endArray()
    }
}
