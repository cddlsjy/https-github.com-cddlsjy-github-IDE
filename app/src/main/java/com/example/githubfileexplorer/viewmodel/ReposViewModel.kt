import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.example.githubfileexplorer.model.Repository
import com.example.githubfileexplorer.network.RetrofitClient

class ReposViewModel(
    private val token: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    sealed class UiState {
        object Loading : UiState()
        data class Success(val repos: List<Repository>) : UiState()
        data class Error(val message: String) : UiState()
    }

    fun loadRepos() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val api = RetrofitClient.createApi(token)
                val repos = api.listRepos("Bearer $token")
                _uiState.value = UiState.Success(repos)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}