package com.rust_book.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rust_book.example.presentation.home.HomeScreenViewModel
import com.rust_book.example.presentation.navigation.AppNavHost
import com.rust_book.example.presentation.navigation.HomeScreenNav
import com.rust_book.example.presentation.navigation.SelectLanguageOfBookNav
import com.rust_book.example.presentation.setup.SetupViewModel
import com.rust_book.example.ui.theme.RustDocumentationTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val setupViewModel = koinViewModel<SetupViewModel>()
      val setupModelSate by setupViewModel.state.collectAsState()
      RustDocumentationTheme {
        if (setupModelSate.isDownloaded == null &&
          (setupModelSate.initPath ?: "").isEmpty()
        ) {
          Box(
            modifier = Modifier.Companion.fillMaxSize(),
            contentAlignment = Alignment.Companion.Center
          ) {
            CircularProgressIndicator()
          }
        } else {
          val homeScreenViewModel = koinViewModel<HomeScreenViewModel>()
          val lastOpenedPath: String? =
            homeScreenViewModel.state.collectAsState().value.historyOfVisitedPath.lastOrNull()
          AppNavHost(
            startDestination = if (setupModelSate.isDownloaded == true) {
              HomeScreenNav(
                lastOpenedPath ?: setupModelSate.initPath!!
              )
            } else {
              SelectLanguageOfBookNav
            }
          )
        }
      }
    }
  }
}