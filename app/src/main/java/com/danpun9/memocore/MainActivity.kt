package com.danpun9.memocore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.danpun9.memocore.ui.screens.chat.ChatNavEvent
import com.danpun9.memocore.ui.screens.chat.ChatScreen
import com.danpun9.memocore.ui.screens.chat.ChatViewModel
import com.danpun9.memocore.ui.screens.docs.DocsScreen
import com.danpun9.memocore.ui.screens.docs.DocsViewModel
import com.danpun9.memocore.ui.screens.edit_credentials.EditCredentialsScreen
import com.danpun9.memocore.ui.screens.local_models.LocalModelsScreen
import com.danpun9.memocore.ui.screens.local_models.LocalModelsViewModel
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel

@Serializable
object ChatRoute

@Serializable
object EditAPIKeyRoute

@Serializable
object DocsRoute

@Serializable
object LocalModelsRoute

@Serializable
object SettingsRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = koinViewModel()
            val themeMode by mainViewModel.themeMode.collectAsState()

            com.danpun9.memocore.ui.theme.DocQATheme(themeMode = themeMode) {
                val navHostController = rememberNavController()
                NavHost(
                    navController = navHostController,
                    startDestination = ChatRoute,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable<DocsRoute> { backStackEntry ->
                        val viewModel: DocsViewModel =
                            koinViewModel(viewModelStoreOwner = backStackEntry)
                        val docScreenUIState by viewModel.docsScreenUIState.collectAsState()
                        DocsScreen(
                            docScreenUIState,
                            onBackClick = { navHostController.navigateUp() },
                            onEvent = viewModel::onEvent,
                        )
                    }
                    composable<EditAPIKeyRoute> { EditCredentialsScreen(onBackClick = { navHostController.navigateUp() }) }
                    composable<LocalModelsRoute> { backStackEntry ->
                        val viewModel: LocalModelsViewModel =
                            koinViewModel(viewModelStoreOwner = backStackEntry)
                        val uiState by viewModel.uiState.collectAsState()
                        LocalModelsScreen(
                            uiState = uiState,
                            onEvent = viewModel::onEvent,
                            onBackClick = { navHostController.navigateUp() },
                        )
                    }
                    composable<SettingsRoute> { backStackEntry ->
                        val viewModel: com.danpun9.memocore.ui.screens.settings.SettingsViewModel =
                            koinViewModel(viewModelStoreOwner = backStackEntry)
                        val selectedModel by viewModel.selectedModel.collectAsState()
                        val selectedTheme by viewModel.themeMode.collectAsState()
                        com.danpun9.memocore.ui.screens.settings.SettingsScreen(
                            selectedModel = selectedModel,
                            selectedTheme = selectedTheme,
                            onModelSelected = viewModel::onModelSelected,
                            onThemeSelected = viewModel::onThemeSelected,
                            onBackClick = { navHostController.navigateUp() },
                            onEditCredentialsClick = { navHostController.navigate(EditAPIKeyRoute) },
                            onManageLocalModelsClick = { navHostController.navigate(LocalModelsRoute) }
                        )
                    }
                    composable<ChatRoute> { backStackEntry ->
                        val viewModel: ChatViewModel =
                            koinViewModel(viewModelStoreOwner = backStackEntry)
                        val chatScreenUIState by viewModel.chatScreenUIState.collectAsState()
                        val navEvent by viewModel.navEventChannel.collectAsState(ChatNavEvent.None)
                        LaunchedEffect(navEvent) {
                            when (navEvent) {
                                is ChatNavEvent.ToDocsScreen -> {
                                    navHostController.navigate(DocsRoute)
                                }

                                is ChatNavEvent.ToEditAPIKeyScreen -> {
                                    navHostController.navigate(EditAPIKeyRoute)
                                }

                                is ChatNavEvent.ToLocalModelsScreen -> {
                                    navHostController.navigate(LocalModelsRoute)
                                }

                                is ChatNavEvent.ToSettingsScreen -> {
                                    navHostController.navigate(SettingsRoute)
                                }

                                is ChatNavEvent.None -> {}
                            }
                        }
                        ChatScreen(
                            screenUiState = chatScreenUIState,
                            onScreenEvent = { viewModel.onChatScreenEvent(it) },
                        )
                    }
                }
            }
        }
    }
}