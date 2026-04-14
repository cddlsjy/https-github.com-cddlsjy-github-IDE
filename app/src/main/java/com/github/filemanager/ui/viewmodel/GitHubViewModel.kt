package com.github.filemanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.github.filemanager.data.model.*
import com.github.filemanager.data.network.GitHubApiClient
import com.github.filemanager.data.repository.GitHubRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GitHubViewModel(
    private val repository: GitHubRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitHubUiState())
    val uiState: StateFlow<GitHubUiState> = _uiState.asStateFlow()

    private val _fileContent = MutableStateFlow<ContentItem?>(null)
    val fileContent: StateFlow<ContentItem?> = _fileContent.asStateFlow()

    fun setToken(token: String) {
        GitHubApiClient.setToken(token)
    }

    fun validateAndLogin(token: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            GitHubApiClient.setToken(token)
            val result = repository.validateToken()
            
            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            currentUser = user,
                            token = token
                        )
                    }
                    loadRepositories()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "登录失败: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun loadRepositories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRepos = true) }
            
            val result = repository.getUserRepositories()
            result.fold(
                onSuccess = { repos ->
                    _uiState.update {
                        it.copy(
                            isLoadingRepos = false,
                            repositories = repos
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingRepos = false,
                            error = "加载仓库失败: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun loadContents(owner: String, repo: String, path: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContents = true) }
            
            val result = repository.getContents(owner, repo, path)
            result.fold(
                onSuccess = { items ->
                    val nodes = items.map { item ->
                        FileNode(
                            name = item.name,
                            path = item.path,
                            type = item.type,
                            sha = item.sha,
                            size = item.size,
                            downloadUrl = item.download_url
                        )
                    }
                    _uiState.update {
                        it.copy(
                            isLoadingContents = false,
                            currentPath = path,
                            contentItems = nodes
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingContents = false,
                            error = "加载内容失败: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun loadFileForEdit(owner: String, repo: String, path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFile = true) }
            
            val result = repository.getFileContent(owner, repo, path)
            result.fold(
                onSuccess = { file ->
                    _fileContent.value = file
                    _uiState.update { it.copy(isLoadingFile = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingFile = false,
                            error = "加载文件失败: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun saveFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        commitMessage: String
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            
            val sha = _fileContent.value?.sha
            val result = repository.createOrUpdateFile(
                owner, repo, path, content, commitMessage, sha
            )
            
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                    // 刷新目录
                    val parentPath = path.substringBeforeLast("/", "")
                    loadContents(owner, repo, parentPath)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = "保存失败: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun deleteFile(
        owner: String,
        repo: String,
        path: String,
        sha: String,
        commitMessage: String
    ) {
        viewModelScope.launch {
            val result = repository.deleteFile(owner, repo, path, sha, commitMessage)
            result.fold(
                onSuccess = {
                    val parentPath = path.substringBeforeLast("/", "")
                    loadContents(owner, repo, parentPath)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = "删除失败: ${error.message}") }
                }
            )
        }
    }

    fun moveOrRenameFile(
        owner: String,
        repo: String,
        oldPath: String,
        newPath: String,
        content: String,
        sha: String,
        commitMessage: String
    ) {
        viewModelScope.launch {
            val result = repository.moveOrRenameFile(
                owner, repo, oldPath, newPath, content, sha, commitMessage
            )
            result.fold(
                onSuccess = {
                    val parentPath = oldPath.substringBeforeLast("/", "")
                    loadContents(owner, repo, parentPath)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(error = "移动失败: ${error.message}") }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun logout() {
        GitHubApiClient.clearToken()
        _uiState.value = GitHubUiState()
        _fileContent.value = null
    }
}

data class GitHubUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val token: String = "",
    val currentUser: User? = null,
    val repositories: List<Repository> = emptyList(),
    val isLoadingRepos: Boolean = false,
    val selectedRepo: Repository? = null,
    val currentPath: String = "",
    val contentItems: List<FileNode> = emptyList(),
    val isLoadingContents: Boolean = false,
    val isLoadingFile: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null
)

class GitHubViewModelFactory(
    private val repository: GitHubRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GitHubViewModel(repository) as T
    }
}
