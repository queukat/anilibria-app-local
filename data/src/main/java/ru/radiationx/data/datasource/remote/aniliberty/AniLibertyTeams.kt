package ru.radiationx.data.datasource.remote.aniliberty

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JvmInline
value class AniLibertyTeamId(val value: String) {
    init {
        require(value.isNotBlank()) { "AniLibertyTeamId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class AniLibertyTeamRoleId(val value: String) {
    init {
        require(value.isNotBlank()) { "AniLibertyTeamRoleId must not be blank" }
    }

    override fun toString(): String = value
}

@JvmInline
value class AniLibertyTeamUserId(val value: String) {
    init {
        require(value.isNotBlank()) { "AniLibertyTeamUserId must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * Swagger: models.teams.v1.team
 */
@JsonClass(generateAdapter = true)
data class AniLibertyTeam(
    @Json(name = "id") val id: AniLibertyTeamId?,
    @Json(name = "title") val title: String?,
    @Json(name = "sort_order") val sortOrder: Int?,
    @Json(name = "description") val description: String?,
)

/**
 * Swagger: models.teams.v1.team.role
 */
@JsonClass(generateAdapter = true)
data class AniLibertyTeamRole(
    @Json(name = "id") val id: AniLibertyTeamRoleId?,
    @Json(name = "title") val title: String?,
    @Json(name = "color") val color: String?,
    @Json(name = "sort_order") val sortOrder: Int?,
)

/**
 * Swagger: models.teams.v1.team.user
 */
@JsonClass(generateAdapter = true)
data class AniLibertyTeamUser(
    @Json(name = "id") val id: AniLibertyTeamUserId?,
    @Json(name = "nickname") val nickname: String?,
    @Json(name = "is_intern") val isIntern: Boolean?,
    @Json(name = "sort_order") val sortOrder: Int?,
    @Json(name = "is_vacation") val isVacation: Boolean?,
)

/**
 * Swagger: models.teams.v1.team.user.account
 */
@JsonClass(generateAdapter = true)
data class AniLibertyTeamUserAccount(
    @Json(name = "id") val id: Int?,
    @Json(name = "nickname") val nickname: String?,
    @Json(name = "avatar") val avatar: AniLibertyImageWithOptimized?,
)

/**
 * Swagger: responses.api.v1.teams.users items
 *
 * allOf:
 * - team.user
 * - { team }
 * - { user account }
 * - { roles }
 */
@JsonClass(generateAdapter = true)
data class AniLibertyTeamUserItem(
    @Json(name = "id") val id: AniLibertyTeamUserId?,
    @Json(name = "nickname") val nickname: String?,
    @Json(name = "is_intern") val isIntern: Boolean?,
    @Json(name = "sort_order") val sortOrder: Int?,
    @Json(name = "is_vacation") val isVacation: Boolean?,
    @Json(name = "team") val team: AniLibertyTeam?,
    @Json(name = "user") val user: AniLibertyTeamUserAccount?,
    @Json(name = "roles") val roles: List<AniLibertyTeamRole>?,
)
