package com.github.filemanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.filemanager.data.model.Repository
import com.github.filemanager.ui.viewmodel.GitHubViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepositoryListScreen(
    viewModel: GitHubViewModel,
    onRepoSelected: (Repository) -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("选择仓库")
                        uiState.currentUser?.login?.let {
                            Text(
                                text = "登录: $it",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadRepositories() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "登出")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (uiState.isLoadingRepos) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn {
                    items(uiState.repositories) { repo ->
                        RepositoryItem(
                            repo = repo,
                            onClick = { onRepoSelected(repo) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RepositoryItem(
    repo: Repository,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = repo.name,
                    style = MaterialTheme.typography.titleMedium
                )
                repo.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = repo.full_name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null
            )
        }
    }
}
