package com.github.filemanager.ui

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.filemanager.data.model.FileNode
import com.github.filemanager.data.model.Repository
import com.github.filemanager.data.network.GitHubApiClient
import com.github.filemanager.data.repository.GitHubRepository
import com.github.filemanager.ui.screens.*
import com.github.filemanager.ui.viewmodel.GitHubViewModel
import com.github.filemanager.ui.viewmodel.GitHubViewModelFactory

@Composable
fun GitHubFileManagerApp() {
    val viewModel: GitHubViewModel = viewModel(
        factory = GitHubViewModelFactory(GitHubRepository(GitHubApiClient.apiService))
    )
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.isLoggedIn) {
        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = { }
        )
    } else {
        var selectedRepo by remember { mutableStateOf<Repository?>(null) }
        var currentScreen by remember { mutableStateOf("repos") }
        var selectedFile by remember { mutableStateOf<FileNode?>(null) }

        when (currentScreen) {
            "repos" -> {
                RepositoryListScreen(
                    viewModel = viewModel,
                    onRepoSelected = { repo ->
                        selectedRepo = repo
                        currentScreen = "browser"
                        viewModel.loadContents(repo.owner.login, repo.name)
                    },
                    onLogout = {
                        viewModel.logout()
                    }
                )
            }
            "browser" -> {
                selectedRepo?.let { repo ->
                    FileBrowserScreen(
                        owner = repo.owner.login,
                        repo = repo.name,
                        viewModel = viewModel,
                        onFileClick = { file ->
                            if (file.type == "file") {
                                selectedFile = file
                                currentScreen = "editor"
                                viewModel.loadFileForEdit(repo.owner.login, repo.name, file.path)
                            }
                        },
                        onBack = {
                            currentScreen = "repos"
                            selectedRepo = null
                        }
                    )
                }
            }
            "editor" -> {
                selectedRepo?.let { repo ->
                    selectedFile?.let { file ->
                        FileEditorScreen(
                            owner = repo.owner.login,
                            repo = repo.name,
                            filePath = file.path,
                            viewModel = viewModel,
                            onBack = {
                                currentScreen = "browser"
                                selectedFile = null
                            }
                        )
                    }
                }
            }
        }
    }
}
