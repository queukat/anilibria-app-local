package ru.radiationx.anilibria.common

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.radiationx.anilibria.presentation.pagination.LoadMoreCardsComposer
import ru.radiationx.anilibria.presentation.pagination.TvCardsPaginator
import ru.radiationx.anilibria.presentation.pagination.TvPagingLoadResult
import ru.radiationx.anilibria.presentation.pagination.TvPagingState
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.shared.ktx.coroutines.AppDispatchers

abstract class BaseCardsViewModel : LifecycleViewModel() {
    /** Итоговые карточки для показа (LibriaCard, LinkCard, LoadingCard и т.д.) */
    protected val cardsDataMutable = MutableStateFlow<List<CardItem>>(emptyList())
    val cardsData: StateFlow<List<CardItem>> = cardsDataMutable.asStateFlow()

    /** Заголовок ряда. */
    protected val rowTitleMutable = MutableStateFlow("")
    val rowTitle: StateFlow<String> = rowTitleMutable.asStateFlow()

    /** С какой страницы начинаем загрузку. Обычно 1. */
    protected open val firstPage = 1

    /** Загружать ли автоматически при первом создании (onColdCreate). */
    protected open val loadOnCreate = true

    /**
     * Показывать ли «LoadingCard»/«Progress» при обновлении (onRefreshClick)?
     * Если `false`, то при обновлении карточки «Loading» не будет.
     */
    protected open val progressOnRefresh = true

    /**
     * Показывать ли промежуточный loading-state при догрузке следующей страницы.
     * Для TV-рядов с кнопкой "Загрузить еще" можно отключить, чтобы не терять фокус.
     */
    protected open val progressOnAppend = true

    /**
     * Нужно ли предотвращать «clear» списка при обновлении?
     * Если `true`, при обновлении мы не очищаем старые карточки, а только добавляем новые.
     */
    protected open val preventClearOnRefresh = false

    /** Текст, который хотим изначально. */
    open val defaultTitle: String = "Cards"

    /**
     * Карточка для «загрузить ещё».
     * Если она нужна — её вставляют в конец списка, когда есть следующая страница.
     */
    protected open val loadMoreCard = LinkCard("Загрузить еще")

    /** Карточка для отображения «в процессе загрузки». */
    protected open val loadingCard = LoadingCard("Загрузка данных")

    private var loaderDispatcher: CoroutineDispatcher = AppDispatchers.io
    private val paginator by lazy(LazyThreadSafetyMode.NONE) { createPaginator() }

    override fun onColdCreate() {
        super.onColdCreate()
        rowTitleMutable.value = defaultTitle
        if (loadOnCreate) {
            onRefreshClick()
        }
    }

    /** Вызывается, когда нажали на «LinkCard(Загрузить ещё)». */
    open fun onLinkCardClick() {
        paginator.append(showProgress = progressOnAppend)
    }

    /** Нажали «обновить» (обычно перезагрузить c первой страницы). */
    open fun onRefreshClick() {
        paginator.refresh(showProgress = progressOnRefresh)
    }

    /** Нажали на «LoadingCard», если она была в состоянии ошибки. */
    open fun onLoadingCardClick() {
        val failedPage = paginator.state.value.failedPage
        val showProgress =
            if (failedPage == null || failedPage == firstPage) {
                progressOnRefresh
            } else {
                progressOnAppend
            }
        paginator.retry(showProgress = showProgress)
    }

    /** Compose-first dispatch: экран передаёт CardItem, а VM решает, что с ним делать. */
    open fun onCardItemClick(item: CardItem) {
        when (item) {
            is LibriaCard -> onLibriaCardClick(item)
            is LinkCard -> onLinkCardClick()
            is LoadingCard -> onLoadingCardClick()
            is InfoCard -> Unit
        }
    }

    /** При клике по обычной карточке (LibriaCard). Переопределяйте в наследниках. */
    open fun onLibriaCardClick(card: LibriaCard) = Unit

    /**
     * Нужно реализовать в наследниках:
     * какую именно порцию данных грузить при запросе конкретной страницы.
     */
    protected open suspend fun getLoader(requestPage: Int): List<LibriaCard> {
        error("Either getLoader() or loadPagingResult() must be implemented.")
    }

    /**
     * Когда нужно показать кнопку «Загрузить ещё».
     * По умолчанию проверяем, что в ответе >= 10 элементов (условно).
     * сейчас это бесконечная загрузка по кругу - это фича, не трогать
     */
    protected open fun hasMoreCards(
        newCards: List<LibriaCard>,
        allCards: List<LibriaCard>,
    ): Boolean {
        return newCards.size >= 10
    }

    /**
     * Нужно ли обновлять/перезаписывать текущие карточки при обновлении.
     * Если `preventClearOnRefresh = true`, мы сравниваем id и решаем, менять ли начало списка.
     */
    protected open fun needsModify(
        newCards: List<LibriaCard>,
        allCards: List<LibriaCard>,
    ): Boolean {
        if (!preventClearOnRefresh) return true
        // Сравниваем "срез" по контенту, а не только набор id.
        val oldSlice = allCards.take(newCards.size)
        return oldSlice != newCards
    }

    /** Карточка-ошибка, если запрос упал. Можно переопределить вид текста и т.п. */
    protected open fun getErrorCard(error: Throwable): LoadingCard {
        return LoadingCard(
            title = "Повторить загрузку",
            description = "Произошла ошибка: ${error.message}",
            isError = true,
        )
    }

    /** Плейсхолдер, когда данные пришли пустыми (чтобы не оставлять UI "тихо пустым"). */
    protected open fun getEmptyStateCard(): CardItem? = null

    /**
     * Test hook to make asynchronous loading deterministic.
     * Production keeps using AppDispatchers.io.
     */
    internal fun setLoaderDispatcherForTests(dispatcher: CoroutineDispatcher) {
        loaderDispatcher = dispatcher
    }

    protected open suspend fun loadPagingResult(
        requestPage: Int,
        currentState: TvPagingState<LibriaCard>,
    ): TvPagingLoadResult<LibriaCard> {
        val newCards = getLoader(requestPage)
        val isFirstPage = requestPage == firstPage
        val allowModify =
            if (isFirstPage) {
                needsModify(newCards, currentState.items)
            } else {
                true
            }
        val mergedItems =
            if (isFirstPage) {
                if (allowModify) {
                    newCards
                } else {
                    currentState.items
                }
            } else {
                currentState.items + newCards
            }
        val appliedPage =
            if (isFirstPage && !allowModify) {
                currentState.currentPage
            } else {
                requestPage
            }
        return TvPagingLoadResult(
            pageItems = newCards,
            mergedItems = mergedItems,
            canLoadMore = hasMoreCards(newCards, mergedItems),
            appliedPage = appliedPage,
        )
    }

    private fun composeCards(state: TvPagingState<LibriaCard>): List<CardItem> {
        return LoadMoreCardsComposer(
            loadMoreCard = loadMoreCard,
            loadingCard = loadingCard,
            errorCardFactory = ::getErrorCard,
            emptyCardFactory = ::getEmptyStateCard,
        ).compose(
            state,
        )
    }

    private fun createPaginator(): TvCardsPaginator<LibriaCard> {
        return TvCardsPaginator(
            scope = viewModelScope,
            firstPage = firstPage,
            dispatcherProvider = { loaderDispatcher },
            loadPage = ::loadPagingResult,
            onStateChanged = { state ->
                cardsDataMutable.value = composeCards(state)
            },
        )
    }
}
