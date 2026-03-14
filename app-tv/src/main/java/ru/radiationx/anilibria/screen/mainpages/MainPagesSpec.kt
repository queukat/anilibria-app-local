package ru.radiationx.anilibria.screen.mainpages

internal object MainPagesSpec {
    const val ID_MAIN = 1L
    const val ID_MY = 2L
    const val ID_FAVORITES = 8L
    const val ID_PROFILE = 7L

    val ids = listOf(
        ID_MAIN,
        ID_MY,
        ID_FAVORITES,
        ID_PROFILE,
    )

    val titles = mapOf(
        ID_MAIN to "Главная",
        ID_MY to "Я смотрю",
        ID_FAVORITES to "Избранное",
        ID_PROFILE to "Профиль",
    )
}
