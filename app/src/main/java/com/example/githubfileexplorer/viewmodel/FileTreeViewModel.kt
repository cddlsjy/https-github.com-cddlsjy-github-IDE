import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.githubfileexplorer.model.ContentItem
import com.example.githubfileexplorer.network.RetrofitClient

class FileTreeViewModel(
    private val token: String,
    private val owner: String,
    private val repo: String,
    private val path: String
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    sealed class UiState {
        object Loading : UiState()
        data class Success(val items: List<ContentItem>) : UiState()
        data class Error(val message: String) : UiState()
    }

    fun loadContents() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val api = RetrofitClient.createApi(token)
                val response = api.getContents("Bearer $token", owner, repo, path)
                _uiState.value = UiState.Success(response)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load contents")
            }
        }
    }
}

class FileTreeViewModelFactory(
    private val token: String,
    private val owner: String,
    private val repo: String,
    private val path: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FileTreeViewModel(token, owner, repo, path) as T
    }
}