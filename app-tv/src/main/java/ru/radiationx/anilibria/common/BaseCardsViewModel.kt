package ru.radiationx.anilibria.common

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.radiationx.anilibria.screen.LifecycleViewModel
import ru.radiationx.shared.ktx.coRunCatching
import timber.log.Timber

abstract class BaseCardsViewModel : LifecycleViewModel() {

    /** Итоговые карточки для показа (LibriaCard, LinkCard, LoadingCard и т.д.) */
    protected val _cardsData = MutableStateFlow<List<CardItem>>(emptyList())
    val cardsData: StateFlow<List<CardItem>> = _cardsData.asStateFlow()

    /** Заголовок ряда. */
    protected val _rowTitle = MutableStateFlow("")
    val rowTitle: StateFlow<String> = _rowTitle.asStateFlow()

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

    /** Текущий список LibriaCard (успешно загруженные). */
    private val currentCards = mutableListOf<LibriaCard>()

    /** Текущая страница (если есть пагинация). */
    private var currentPage = -1

    /** Job для отмены/предотвращения параллельных запросов. */
    private var requestJob: Job? = null
    private var loaderDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun onColdCreate() {
        super.onColdCreate()
        _rowTitle.value = defaultTitle
        if (loadOnCreate) {
            onRefreshClick()
        }
    }

    /** Вызывается, когда нажали на «LinkCard(Загрузить ещё)». */
    open fun onLinkCardClick() {
        onLinkCardBind()
    }

    /** Можно переопределить, если нужна особая логика при биндинге/фокусе LinkCard. */
    open fun onLinkCardBind() {
        loadPage(currentPage + 1)
    }

    /** Нажали «обновить» (обычно перезагрузить c первой страницы). */
    open fun onRefreshClick() {
        loadPage(firstPage)
    }

    /** Нажали на «LoadingCard», если она была в состоянии ошибки. */
    open fun onLoadingCardClick() {
        val pageToLoad = if (currentPage >= firstPage) currentPage else firstPage
        loadPage(pageToLoad)
    }

    /** При клике по обычной карточке (LibriaCard). Переопределяйте в наследниках. */
    open fun onLibriaCardClick(card: LibriaCard) {}

    /**
     * Нужно реализовать в наследниках:
     * какую именно порцию данных грузить при запросе конкретной страницы.
     */
    protected abstract suspend fun getLoader(requestPage: Int): List<LibriaCard>

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
            isError = true
        )
    }

    /** Плейсхолдер, когда данные пришли пустыми (чтобы не оставлять UI "тихо пустым"). */
    protected open fun getEmptyStateCard(): CardItem? = null

    /**
     * Test hook to make asynchronous loading deterministic.
     * Production keeps using Dispatchers.IO.
     */
    internal fun setLoaderDispatcherForTests(dispatcher: CoroutineDispatcher) {
        loaderDispatcher = dispatcher
    }

    /** Главный метод для загрузки (первая или следующая страница). */
    private fun loadPage(requestPage: Int) {
        if (requestJob?.isActive == true) return
        requestJob = viewModelScope.launch {
            // Показываем «loadingCard», если (не первая страница) или при принуд. прогрессе
            if (requestPage != firstPage || progressOnRefresh) {
                _cardsData.value = currentCards + loadingCard
            }
            coRunCatching {
                withContext(loaderDispatcher) { getLoader(requestPage) }
            }.onSuccess { newCards ->
                val isFirstPage = requestPage == firstPage
                val allowModify = if (isFirstPage) {
                    needsModify(newCards, currentCards)
                } else true

                if (isFirstPage && allowModify) {
                    currentCards.clear()
                }
                if (allowModify) {
                    currentPage = requestPage
                    currentCards.addAll(newCards)
                }
                if (currentCards.isEmpty()) {
                    val emptyCard = getEmptyStateCard()
                    if (emptyCard != null) {
                        _cardsData.value = listOf(emptyCard)
                        return@onSuccess
                    }
                }
                // Если ещё есть страницы — добавим linkCard, иначе нет
                _cardsData.value = if (hasMoreCards(newCards, currentCards)) {
                    currentCards + loadMoreCard
                } else {
                    currentCards
                }
            }.onFailure { error ->
                Timber.e(error)
                _cardsData.value = currentCards + getErrorCard(error)
            }
        }
    }
}
