package ru.radiationx.data.di

import ru.radiationx.data.contracts.tv.TvPlayerFacade
import ru.radiationx.data.contracts.tv.TvProfileFacade
import ru.radiationx.data.contracts.tv.TvWatchingFacade
import ru.radiationx.data.contracts.tv.impl.TvPlayerFacadeImpl
import ru.radiationx.data.contracts.tv.impl.TvProfileFacadeImpl
import ru.radiationx.data.contracts.tv.impl.TvWatchingFacadeImpl
import ru.radiationx.data.interactors.tv.TvContentUseCase
import ru.radiationx.data.interactors.tv.TvContentUseCaseImpl
import ru.radiationx.data.interactors.tv.TvDetailHeaderUseCase
import ru.radiationx.data.interactors.tv.TvDetailHeaderUseCaseImpl
import ru.radiationx.data.interactors.tv.TvFavoritesUseCase
import ru.radiationx.data.interactors.tv.TvFavoritesUseCaseImpl
import ru.radiationx.data.interactors.tv.TvReleaseUseCase
import ru.radiationx.data.interactors.tv.TvReleaseUseCaseImpl
import ru.radiationx.data.interactors.tv.TvSearchUseCase
import ru.radiationx.data.interactors.tv.TvSearchUseCaseImpl
import ru.radiationx.data.interactors.tv.TvSessionUseCase
import ru.radiationx.data.interactors.tv.TvSessionUseCaseImpl
import ru.radiationx.data.interactors.tv.TvSuggestionsUseCase
import ru.radiationx.data.interactors.tv.TvSuggestionsUseCaseImpl
import ru.radiationx.data.interactors.tv.TvUpdateUseCase
import ru.radiationx.data.interactors.tv.TvUpdateUseCaseImpl
import ru.radiationx.quill.QuillModule

class DataTvModule : QuillModule() {

    init {
        singleImpl<TvContentUseCase, TvContentUseCaseImpl>()
        singleImpl<TvFavoritesUseCase, TvFavoritesUseCaseImpl>()
        singleImpl<TvReleaseUseCase, TvReleaseUseCaseImpl>()
        singleImpl<TvSessionUseCase, TvSessionUseCaseImpl>()
        singleImpl<TvSearchUseCase, TvSearchUseCaseImpl>()
        singleImpl<TvUpdateUseCase, TvUpdateUseCaseImpl>()
        singleImpl<TvSuggestionsUseCase, TvSuggestionsUseCaseImpl>()
        singleImpl<TvDetailHeaderUseCase, TvDetailHeaderUseCaseImpl>()
        singleImpl<TvPlayerFacade, TvPlayerFacadeImpl>()
        singleImpl<TvWatchingFacade, TvWatchingFacadeImpl>()
        singleImpl<TvProfileFacade, TvProfileFacadeImpl>()
    }
}
