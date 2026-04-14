import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.githubfileexplorer.viewmodel.ReposViewModel

@Composable
fun ReposScreen(
    token: String,
    onRepoClick: (owner: String, repo: String) -> Unit
) {
    val viewModel: ReposViewModel = viewModel(key = token, factory = ReposViewModelFactory(token))
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRepos()
    }

    when (uiState) {
        is ReposViewModel.UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
        is ReposViewModel.UiState.Success -> {
            LazyColumn {
                items((uiState as ReposViewModel.UiState.Success).repos) { repo ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable { onRepoClick(repo.owner.login, repo.name) }
                    ) {
                        Text(
                            text = repo.full_name,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
        is ReposViewModel.UiState.Error -> {
            Text("Error: ${(uiState as ReposViewModel.UiState.Error).message}", modifier = Modifier.padding(16.dp))
        }
    }
}

// 简单工厂
class ReposViewModelFactory(private val token: String) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ReposViewModel(token) as T
    }
}