import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.githubfileexplorer.model.ContentItem
import com.example.githubfileexplorer.viewmodel.FileTreeViewModel

@Composable
fun FileTreeScreen(
    token: String,
    owner: String,
    repo: String,
    currentPath: String,
    onNavigateToSubPath: (String) -> Unit,
    onFileClick: (String, String) -> Unit
) {
    val viewModel: FileTreeViewModel = viewModel(
        key = "$owner/$repo/$currentPath",
        factory = FileTreeViewModelFactory(token, owner, repo, currentPath)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(currentPath) {
        viewModel.loadContents()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$owner/$repo/${currentPath.ifEmpty { "root" }}") },
                navigationIcon = {
                    if (currentPath.isNotEmpty()) {
                        IconButton(onClick = {
                            val parent = currentPath.substringBeforeLast("/", "")
                            onNavigateToSubPath(parent)
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) {
        padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (uiState) {
                is FileTreeViewModel.UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is FileTreeViewModel.UiState.Success -> {
                    LazyColumn {
                        items((uiState as FileTreeViewModel.UiState.Success).items) { item ->
                            FileTreeRow(
                                item = item,
                                onFolderClick = {
                                    val newPath = if (currentPath.isEmpty()) item.name else "$currentPath/${item.name}"
                                    onNavigateToSubPath(newPath)
                                },
                                onFileClick = {
                                    val filePath = if (currentPath.isEmpty()) item.name else "$currentPath/${item.name}"
                                    onFileClick(filePath, item.name)
                                }
                            )
                        }
                    }
                }
                is FileTreeViewModel.UiState.Error -> {
                    Text("Error: ${(uiState as FileTreeViewModel.UiState.Error).message}", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun FileTreeRow(item: ContentItem, onFolderClick: () -> Unit, onFileClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (item.type == "dir") onFolderClick()
                else onFileClick()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.type == "dir") Icons.Default.Folder else Icons.Default.InsertDriveFile,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = item.name)
    }
}