import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Base64
import java.nio.charset.StandardCharsets
import com.example.githubfileexplorer.model.ContentItem
import com.example.githubfileexplorer.model.CommitBody
import com.example.githubfileexplorer.network.RetrofitClient

class EditFileViewModel(
    private val token: String,
    private val owner: String,
    private val repo: String,
    private val filePath: String
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var currentSha: String? = null

    sealed class UiState {
        object Loading : UiState()
        data class ContentLoaded(val originalContent: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    fun loadFile() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val api = RetrofitClient.createApi(token)
                val file = api.getFileContent("Bearer $token", owner, repo, filePath)
                currentSha = file.sha
                val decoded = if (file.content != null) {
                    String(Base64.decode(file.content, Base64.DEFAULT), StandardCharsets.UTF_8)
                } else {
                    ""
                }
                _uiState.value = UiState.ContentLoaded(decoded)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load file")
            }
        }
    }

    fun saveFile(newContent: String, commitMsg: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.createApi(token)
                val encoded = Base64.encodeToString(newContent.toByteArray(), Base64.NO_WRAP)
                val body = CommitBody(
                    message = commitMsg,
                    content = encoded,
                    sha = currentSha
                )
                api.createOrUpdateFile("Bearer $token", owner, repo, filePath, body)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Save failed")
            }
        }
    }

    fun deleteFile(commitMsg: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.createApi(token)
                api.deleteFile("Bearer $token", owner, repo, filePath, commitMsg, currentSha!!)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Delete failed")
            }
        }
    }

    fun moveFile(newPath: String, commitMsg: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.createApi(token)
                // 1. 获取当前文件内容
                val file = api.getFileContent("Bearer $token", owner, repo, filePath)
                val decoded = String(Base64.decode(file.content, Base64.DEFAULT), StandardCharsets.UTF_8)
                val encoded = Base64.encodeToString(decoded.toByteArray(), Base64.NO_WRAP)
                // 2. PUT 到新路径
                val body = CommitBody(message = commitMsg, content = encoded)
                api.createOrUpdateFile("Bearer $token", owner, repo, newPath, body)
                // 3. DELETE 旧路径
                api.deleteFile("Bearer $token", owner, repo, filePath, commitMsg, file.sha)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Move failed")
            }
        }
    }
}

class EditFileViewModelFactory(
    private val token: String,
    private val owner: String,
    private val repo: String,
    private val filePath: String
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EditFileViewModel(token, owner, repo, filePath) as T
    }
}