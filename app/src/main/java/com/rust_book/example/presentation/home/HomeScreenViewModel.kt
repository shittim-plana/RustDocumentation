package com.rust_book.example.presentation.home

import android.app.Application
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rust_book.example.data.local.PreferencesKeys
import com.rust_book.example.presentation.home.HomeScreenAction.*
import com.rust_book.example.utils.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeScreenViewModel(
  private val dataStore: DataStore<Preferences>,
  private val application: Application,
) : ViewModel() {
  private val _state = MutableStateFlow(HomeScreenState())
  val state = _state.asStateFlow()


  private val _canWebViewGoBack = MutableStateFlow(false)
  val canWebViewGoBack: StateFlow<Boolean> = _canWebViewGoBack.asStateFlow()

  val webViewClient = object : WebViewClient() {
    override fun onPageFinished(view: WebView?, url: String?) {
      super.onPageFinished(view, url)
      if (url != null) justChangeCurrentDoc(url)

      _canWebViewGoBack.value = view?.canGoBack() ?: false
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
      super.shouldOverrideUrlLoading(view, request)
      val requestedUrl = request?.url?.toString()
      println(requestedUrl)
      if (requestedUrl != null && requestedUrl != _state.value.currentDocPath) {
        println("Action = $requestedUrl")
        if (view != null) onAction(view, action = ChangeCurrentDoc(requestedUrl))
      }

      _canWebViewGoBack.value = view?.canGoBack() ?: false
      return false
    }


    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
      super.onPageStarted(view, url, favicon)
      _canWebViewGoBack.value = view?.canGoBack() ?: false
    }
  }


  init {
    viewModelScope.launch {
      val preferences = dataStore.data.first()
      val favoritePaths = preferences[PreferencesKeys.FAVORITE_PATHS] ?: emptySet()
      val homePath = preferences[PreferencesKeys.HOME_PATH]
      val history = preferences[PreferencesKeys.HISTORY_OF_VISITED_PATH] ?: emptySet()
      val language = preferences[PreferencesKeys.BOOK_LANGUAGE] ?: "English"
      _state.value = _state.value.copy(
        allFavoritePath = favoritePaths.toList(),
        homePath = homePath,
        currentDocPath = homePath,
        searchQuery = homePath?.split("book/")?.last(),
        historyOfVisitedPath = history.toList(),
        language = language
      )



      _state.value = _state.value.copy(isThisFavorite = favoritePaths.contains(homePath))


      getAllHtmlFileList(language)



      dataStore.data
        .map { prefs ->
          Triple(
            prefs[PreferencesKeys.FAVORITE_PATHS] ?: emptySet(),
            prefs[PreferencesKeys.HOME_PATH] ?: "file:///android_asset/index.html",
            prefs[PreferencesKeys.HISTORY_OF_VISITED_PATH] ?: emptySet()
          )
        }
        .collect { (favorites, home, history) ->
          _state.value = _state.value.copy(
            allFavoritePath = favorites.toList(),
            homePath = home,
            isThisFavorite = favorites.contains(_state.value.currentDocPath),
            historyOfVisitedPath = history.toList()
          )
        }

    }
  }

  fun justChangeCurrentDoc(path: String) {
    if (File(path).exists()) {
      _state.value = _state.value.copy(
        currentDocPath = path,
        homePath = path,
        searchQuery = if (path.startsWith("http")) path else path.split("book/").last(),
        isThisFavorite = _state.value.allFavoritePath.contains(path)
      )
    } else {
      return
    }
  }

  fun onAction(webView: WebView? = null, action: HomeScreenAction) {
    if (webView == null) return;
    when (action) {
      is ChangeCurrentDoc -> {
        action.path.let {
          val history = _state.value.historyOfVisitedPath.toMutableList().take(20)
            .toMutableList()
          if (history.firstOrNull() != it) {
            if (it != null) history.add(0, it)
          }
          viewModelScope.launch {
            dataStore.edit { dataBase ->
              dataBase[PreferencesKeys.HISTORY_OF_VISITED_PATH] = history.toSet()
            }
          }
          println("Action Path ${action.path}")
          if (action.path != null) webView.loadUrl(action.path)
          _state.value = _state.value.copy(
            currentDocPath = it,
            searchQuery = if (it?.startsWith("http") == true) it else it?.split("book/")?.last(),
            historyOfVisitedPath = history,
            isThisFavorite = _state.value.allFavoritePath.contains(it)
          )
        }
        println(File("XYZ ->" + action.path).exists().toString())
        if (action.path != null) {
          webView.loadUrl(action.path)
          _state.value = _state.value.copy(
            currentDocPath = action.path,
            searchQuery = if (action.path.startsWith("http")) action.path else action.path.split("book/")
              .lastOrNull() ?: "",

            isThisFavorite = _state.value.allFavoritePath.contains(action.path)
          )
        }
        _canWebViewGoBack.value = webView.canGoBack()

      }

      is WebViewInstanceCreate -> {
        webView.loadUrl(action.initPath)
        _canWebViewGoBack.value = webView.canGoBack()
      }

      is AddFavorite -> {
        viewModelScope.launch {
          dataStore.edit {
            val currentFavorites = it[PreferencesKeys.FAVORITE_PATHS] ?: emptySet()
            it[PreferencesKeys.FAVORITE_PATHS] = currentFavorites + action.path
          }
        }
      }

      is RemoveFavorite -> {
        viewModelScope.launch {
          dataStore.edit {
            val currentFavorites = it[PreferencesKeys.FAVORITE_PATHS] ?: emptySet()
            it[PreferencesKeys.FAVORITE_PATHS] = currentFavorites - action.path
          }

        }
      }

      is Search -> {
        _state.value = _state.value.copy(searchQuery = action.query)
        println(_state.value.allDocsPath.toString())
        _state.value = _state.value.copy(
          searchResult = if (action.query.isNotBlank()) {
            _state.value.allDocsPath.filter { it.contains(action.query, ignoreCase = true) }
          } else {
            emptyList()
          }
        )
      }

      is ShowMenu -> {
        _state.value = _state.value.copy(showMenu = action.show)
      }

      is IsSearchTyping -> {
        _state.value = _state.value.copy(isSearchTyping = action.isTyping)
      }

      is GoBack -> {
        if (webView.canGoBack()) {
          webView.goBack()
          _canWebViewGoBack.value = webView.canGoBack()
        }
      }

      is GoForward -> {
        if (webView.canGoForward()) {
          webView.goForward()
          _canWebViewGoBack.value =
            webView.canGoBack()
        }
      }

      is GoHome -> {
        viewModelScope.launch {
          val homePath = dataStore.data.first()[PreferencesKeys.HOME_PATH]
          onAction(webView, ChangeCurrentDoc(homePath))
          if (homePath != null) {
            webView.loadUrl(homePath)
          }
        }
      }

      is SetAsHome -> {
        viewModelScope.launch {
          dataStore.edit {
            it[PreferencesKeys.HOME_PATH] = action.path
          }
        }
      }

      is ResetApp -> {
        viewModelScope.launch {
          dataStore.edit {
            it.clear()
          }
          if (_state.value.language != null) {
            AppUtils().deleteFolder(_state.value.language!!, application = application)
          }
        }
      }

      is HandleSystemBack -> {
        if (webView.canGoBack()) {
          webView.goBack()
          _canWebViewGoBack.value = webView.canGoBack()
          action.onHandled(true)
        } else {
          action.onHandled(false)
        }
      }

      is ShowFavoritesPopup -> {
        _state.value = _state.value.copy(showFavoritesPopup = action.show)
      }

      is ShowHistoryPopup -> {
        _state.value = _state.value.copy(showHistoryPopup = action.show)
      }
    }
  }

  fun getAllHtmlFileList(bookLanguage: String) {
    viewModelScope.launch {
      val targetFolder: String = application.dataDir.path + "/${bookLanguage}/book"
      println("Target Path -> $targetFolder")
      _state.value = _state.value.copy(allDocsPath = withContext(Dispatchers.IO) {
        val fileList = mutableListOf<String>()
        val folder = File(targetFolder)
        if (folder.exists() && folder.isDirectory) {
          try {
            folder.walkTopDown().forEach { file ->
              if (file.isFile && file.extension == "html") {
                fileList.add(file.absolutePath)
              }
            }
          } catch (e: Exception) {
            println("Error walking file tree: ${e.message}")
          }
        }
        fileList
      })
    }
  }

}
