import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import com.example.githubfileexplorer.viewmodel.EditFileViewModel

@Composable
fun EditFileScreen(
    token: String,
    owner: String,
    repo: String,
    filePath: String,
    onBack: () -> Unit
) {
    val viewModel: EditFileViewModel = viewModel(
        key = "$owner/$repo/$filePath",
        factory = EditFileViewModelFactory(token, owner, repo, filePath)
    )
    val uiState by viewModel.uiState.collectAsState()
    var content by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("Update $filePath") }
    var showMoveDialog by remember { mutableStateOf(false) }
    var newPathInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadFile()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(filePath.substringAfterLast("/")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.deleteFile(commitMessage) { onBack() } }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = { showMoveDialog = true }) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Move/Rename")
                    }
                }
            )
        }
    ) {
        padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (uiState) {
                is EditFileViewModel.UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
                is EditFileViewModel.UiState.ContentLoaded -> {
                    content = (uiState as EditFileViewModel.UiState.ContentLoaded).originalContent
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier.fillMaxSize().weight(1f),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commitMessage,
                            onValueChange = { commitMessage = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Commit message") },
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                viewModel.saveFile(content, commitMessage) {
                                    onBack()
                                }
                            }
                        ) {
                            Text("Save")
                        }
                    }
                }
                is EditFileViewModel.UiState.Error -> {
                    Text("Error: ${(uiState as EditFileViewModel.UiState.Error).message}", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move / Rename") },
            text = {
                OutlinedTextField(
                    value = newPathInput,
                    onValueChange = { newPathInput = it },
                    label = { Text("New path (relative to repo root)") },
                    singleLine = true,
                    placeholder = { Text("e.g., folder/newName.txt") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPathInput.isNotBlank()) {
                            viewModel.moveFile(newPathInput, commitMessage) {
                                onBack()
                            }
                        }
                        showMoveDialog = false
                    }
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}