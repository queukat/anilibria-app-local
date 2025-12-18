package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Модель релиза под AniLiberty v1
 * соответствует components.schemas.models.anime.releases.v1.release
 */

@JsonClass(generateAdapter = true)
data class AniLibertyRelease(
    @Json(name = "id") val id: Int?,
    @Json(name = "alias") val alias: String?,
    @Json(name = "name") val name: Name?,
    @Json(name = "type") val type: Type?,
    @Json(name = "year") val year: Int?,
    @Json(name = "season") val season: Season?,
    @Json(name = "poster") val poster: Poster?,
    @Json(name = "fresh_at") val freshAt: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "is_ongoing") val isOngoing: Boolean?,
    @Json(name = "age_rating") val ageRating: AgeRating?,
    @Json(name = "publish_day") val publishDay: PublishDay?,
    @Json(name = "description") val description: String?,
    @Json(name = "notification") val notification: String?,
    @Json(name = "episodes_total") val episodesTotal: Int?,
    @Json(name = "external_player") val externalPlayer: String?,
    @Json(name = "is_in_production") val isInProduction: Boolean?,
    @Json(name = "is_blocked_by_geo") val isBlockedByGeo: Boolean?,
    @Json(name = "is_blocked_by_copyrights") val isBlockedByCopyrights: Boolean?,
    @Json(name = "added_in_users_favorites") val addedInUsersFavorites: Int?,
    @Json(name = "average_duration_of_episode") val averageDurationOfEpisode: Int?,
    @Json(name = "added_in_planned_collection") val plannedCount: Int?,
    @Json(name = "added_in_watched_collection") val watchedCount: Int?,
    @Json(name = "added_in_watching_collection") val watchingCount: Int?,
    @Json(name = "added_in_postponed_collection") val postponedCount: Int?,
    @Json(name = "added_in_abandoned_collection") val abandonedCount: Int?,

    // Expanded fields (present in multiple v1 responses)
    @Json(name = "genres") val genres: List<AniLibertyGenre>?,
    @Json(name = "members") val members: List<AniLibertyReleaseMember>?,
    @Json(name = "episodes") val episodes: List<AniLibertyEpisode>?,
    @Json(name = "torrents") val torrents: List<AniLibertyTorrent>?,
    @Json(name = "sponsor") val sponsor: AniLibertySponsor?,
    @Json(name = "latest_episode") val latestEpisode: AniLibertyEpisode?,

)

/**
 * Названия релиза
 */
@JsonClass(generateAdapter = true)
data class Name(
    @Json(name = "main") val main: String?,
    @Json(name = "english") val english: String?,
    @Json(name = "alternative") val alternative: String?,
)

/**
 * Тип (ТВ, OVA, фильм и т.п.)
 */
@JsonClass(generateAdapter = true)
data class Type(
    @Json(name = "value") val value: String?,         // enums.anime.releases.release.type
    @Json(name = "description") val description: String?,
)

/**
 * Сезон выхода (зима/весна/лето/осень)
 */
@JsonClass(generateAdapter = true)
data class Season(
    @Json(name = "value") val value: String?,         // enums.anime.releases.release.season
    @Json(name = "description") val description: String?,
)

/**
 * Возрастной рейтинг
 */
@JsonClass(generateAdapter = true)
data class AgeRating(
    @Json(name = "value") val value: String?,         // enums.anime.releases.release.ageRating
    @Json(name = "label") val label: String?,
    @Json(name = "is_adult") val isAdult: Boolean?,
    @Json(name = "description") val description: String?,
)

/**
 * День выхода
 */
@JsonClass(generateAdapter = true)
data class PublishDay(
    @Json(name = "value") val value: Int?,   // было String?
    @Json(name = "description") val description: String?,
)


/**
 * Базовое изображение
 */
@JsonClass(generateAdapter = true)
data class PosterImage(
    @Json(name = "preview") val preview: String?,
    @Json(name = "thumbnail") val thumbnail: String?,
)

/**
 * Постер с оптимизированной версией
 */
@JsonClass(generateAdapter = true)
data class Poster(
    @Json(name = "preview") val preview: String?,
    @Json(name = "thumbnail") val thumbnail: String?,
    @Json(name = "optimized") val optimized: PosterImage?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyImage(
    @Json(name = "preview") val preview: String?,
    @Json(name = "thumbnail") val thumbnail: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyImageWithOptimized(
    @Json(name = "preview") val preview: String?,
    @Json(name = "thumbnail") val thumbnail: String?,
    @Json(name = "optimized") val optimized: AniLibertyImage?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyGenre(
    @Json(name = "id") val id: Int?,
    @Json(name = "name") val name: String?,
    @Json(name = "image") val image: AniLibertyImageWithOptimized?,
    @Json(name = "total_releases") val totalReleases: Int?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyReleaseMemberRole(
    @Json(name = "value") val value: String?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyReleaseMemberUser(
    @Json(name = "id") val id: Int?,                  // было Double?
    @Json(name = "avatar") val avatar: AniLibertyImageWithOptimized?,
)


@JsonClass(generateAdapter = true)
data class AniLibertyReleaseMember(
    @Json(name = "id") val id: String?,
    @Json(name = "role") val role: AniLibertyReleaseMemberRole?,
    @Json(name = "user") val user: AniLibertyReleaseMemberUser?,
    @Json(name = "nickname") val nickname: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyEpisodeSkip(
    @Json(name = "start") val start: Double?,
    @Json(name = "stop") val stop: Double?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyEpisode(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "ordinal") val ordinal: Int?,        // было Double?
    @Json(name = "ending") val ending: AniLibertyEpisodeSkip?,
    @Json(name = "opening") val opening: AniLibertyEpisodeSkip?,
    @Json(name = "preview") val preview: AniLibertyImageWithOptimized?,
    @Json(name = "hls_480") val hls480: String?,
    @Json(name = "hls_720") val hls720: String?,
    @Json(name = "hls_1080") val hls1080: String?,
    @Json(name = "duration") val duration: Int?,
    @Json(name = "rutube_id") val rutubeId: String?,
    @Json(name = "youtube_id") val youtubeId: String?,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "sort_order") val sortOrder: Int?,   // было Double?
    @Json(name = "release_id") val releaseId: Int?,   // было Double?
    @Json(name = "name_english") val nameEnglish: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyTorrentType(
    @Json(name = "value") val value: String?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyTorrentQuality(
    @Json(name = "value") val value: String?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyTorrentCodec(
    @Json(name = "value") val value: String?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyTorrentColor(
    @Json(name = "value") val value: String?,
    @Json(name = "description") val description: String?,
)

@JsonClass(generateAdapter = true)
data class AniLibertyTorrent(
    @Json(name = "id") val id: Int?,
    @Json(name = "hash") val hash: String?,
    @Json(name = "size") val size: Long?,   // было Int, ловили JsonDataException: Expected an int but was 16269517466 at path $.torrents[0].size
    @Json(name = "type") val type: AniLibertyTorrentType?,
    @Json(name = "color") val color: AniLibertyTorrentColor?,
    @Json(name = "codec") val codec: AniLibertyTorrentCodec?,
    @Json(name = "label") val label: String?,
    @Json(name = "quality") val quality: AniLibertyTorrentQuality?,
    @Json(name = "magnet") val magnet: String?,
    @Json(name = "filename") val filename: String?,
    @Json(name = "seeders") val seeders: Int?,
    @Json(name = "bitrate") val bitrate: Int?,
    @Json(name = "leechers") val leechers: Int?,
    @Json(name = "sort_order") val sortOrder: Int?,
    @Json(name = "updated_at") val updatedAt: String?,
    @Json(name = "is_hardsub") val isHardsub: Boolean?,
    @Json(name = "description") val description: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "completed_times") val completedTimes: Int?,
)

@JsonClass(generateAdapter = true)
data class AniLibertySponsor(
    @Json(name = "id") val id: String?,
    @Json(name = "title") val title: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "url_title") val urlTitle: String?,
    @Json(name = "url") val url: String?,
)
