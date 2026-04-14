package com.github.filemanager.ui.screens
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import androidx.compose.ui.unit.dp
import com.github.filemanager.data.model.FileNode
import com.github.filemanager.ui.viewmodel.GitHubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    owner: String,
    repo: String,
    viewModel: GitHubViewModel,
    onFileClick: (FileNode) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("$owner/$repo")
                        Text(
                            text = if (uiState.currentPath.isEmpty()) "/" else uiState.currentPath,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    if (uiState.currentPath.isNotEmpty()) {
                        IconButton(onClick = {
                            val parentPath = uiState.currentPath.substringBeforeLast("/", "")
                            viewModel.loadContents(owner, repo, parentPath)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上级")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadContents(owner, repo, uiState.currentPath) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (uiState.isLoadingContents) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.contentItems.isEmpty() && uiState.currentPath.isEmpty()) {
                Text(
                    text = "目录为空",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn {
                    // 分组显示：先目录，后文件
                    val dirs = uiState.contentItems.filter { it.type == "dir" }
                    val files = uiState.contentItems.filter { it.type == "file" }

                    items(dirs + files, key = { it.path }) { item ->
                        FileItem(
                            node = item,
                            onClick = {
                                if (item.type == "dir") {
                                    Log.d("FileBrowser", "Opening dir: ${item.path}")
                                    viewModel.loadContents(owner, repo, item.path)
                                } else {
                                    onFileClick(item)
                                }
                            },
                            onLongClick = {
                                showFileOptionsDialog(
                                    context = context,
                                    fileNode = item,
                                    owner = owner,
                                    repo = repo,
                                    viewModel = viewModel,
                                    onDeleted = {
                                        viewModel.loadContents(owner, repo, uiState.currentPath)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileItem(
    node: FileNode,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (node.type == "dir") Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (node.type == "dir") 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = node.name,
                modifier = Modifier.weight(1f)
            )
            if (node.type == "file") {
                Text(
                    text = formatFileSize(node.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (node.type == "dir") {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null
                )
            }
        }
    }
}

fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> "${size / (1024 * 1024)} MB"
    }
}

fun showFileOptionsDialog(
    context: Context,
    fileNode: FileNode,
    owner: String,
    repo: String,
    viewModel: GitHubViewModel,
    onDeleted: () -> Unit
) {
    val options = if (fileNode.type == "file") {
        listOf("编辑", "重命名/移动", "删除")
    } else {
        listOf("重命名/移动", "删除")
    }

    AlertDialog.Builder(context)
        .setTitle(fileNode.name)
        .setItems(options.toTypedArray()) { _, which ->
            when (options[which]) {
                "编辑" -> {
                    // 通知外部跳转到编辑界面
                }
                "重命名/移动" -> {
                    showRenameMoveDialog(context, fileNode, owner, repo, viewModel)
                }
                "删除" -> {
                    showDeleteConfirmDialog(context, fileNode, owner, repo, viewModel, onDeleted)
                }
            }
        }
        .setNegativeButton("取消", null)
        .show()
}

fun showDeleteConfirmDialog(
    context: Context,
    fileNode: FileNode,
    owner: String,
    repo: String,
    viewModel: GitHubViewModel,
    onDeleted: () -> Unit
) {
    AlertDialog.Builder(context)
        .setTitle("删除确认")
        .setMessage("确定要删除 ${fileNode.path} 吗？")
        .setPositiveButton("删除") { _, _ ->
            fileNode.sha?.let { sha ->
                viewModel.deleteFile(
                    owner, repo, fileNode.path, sha,
                    "Delete ${fileNode.name}"
                )
                onDeleted()
            }
        }
        .setNegativeButton("取消", null)
        .show()
}

fun showRenameMoveDialog(
    context: Context,
    fileNode: FileNode,
    owner: String,
    repo: String,
    viewModel: GitHubViewModel
) {
    val input = EditText(context)
    input.setText(fileNode.path)
    
    AlertDialog.Builder(context)
        .setTitle("重命名/移动")
        .setMessage("输入新路径:")
        .setView(input)
        .setPositiveButton("确定") { _, _ ->
            val newPath = input.text.toString()
            if (newPath.isNotBlank() && newPath != fileNode.path) {
                // 需要先获取文件内容才能移动
                viewModel.loadFileForEdit(owner, repo, fileNode.path)
            }
        }
        .setNegativeButton("取消", null)
        .show()
}
