package com.github.filemanager.ui.screens

import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.filemanager.ui.viewmodel.GitHubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditorScreen(
    owner: String,
    repo: String,
    filePath: String,
    viewModel: GitHubViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    
    var editedContent by remember { mutableStateOf("") }
    var commitMessage by remember { mutableStateOf("") }
    var isContentLoaded by remember { mutableStateOf(false) }
    var showCommitDialog by remember { mutableStateOf(false) }

    // 解码Base64内容
    LaunchedEffect(fileContent) {
        fileContent?.let { file ->
            if (!isContentLoaded) {
                val decoded = if (file.content != null && file.encoding == "base64") {
                    try {
                        val cleanContent = file.content.replace("\n", "")
                        String(Base64.decode(cleanContent, Base64.DEFAULT))
                    } catch (e: Exception) {
                        "无法解码内容: ${e.message}"
                    }
                } else {
                    ""
                }
                editedContent = decoded
                commitMessage = "Update ${file.name}"
                isContentLoaded = true
            }
        }
    }

    // 监听保存成功
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(filePath.substringAfterLast("/")) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isContentLoaded) {
                        IconButton(
                            onClick = { showCommitDialog = true },
                            enabled = !uiState.isSaving
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Save, contentDescription = "保存")
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when {
                uiState.isLoadingFile -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                isContentLoaded -> {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        BasicTextField(
                            value = editedContent,
                            onValueChange = { editedContent = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (editedContent.isEmpty()) {
                                        Text(
                                            text = "输入文件内容...",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 提交对话框
    if (showCommitDialog) {
        AlertDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text("提交更改") },
            text = {
                Column {
                    Text("文件: ${filePath.substringAfterLast("/")}")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text("Commit message") },
                        singleLine = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveFile(owner, repo, filePath, editedContent, commitMessage)
                        showCommitDialog = false
                    },
                    enabled = commitMessage.isNotBlank()
                ) {
                    Text("提交")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
