package com.github.filemanager.data.repository

import android.util.Base64
import com.github.filemanager.data.api.GitHubApiService
import com.github.filemanager.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GitHubRepository(
    private val apiService: GitHubApiService
) {

    suspend fun validateToken(): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getCurrentUser()
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Token invalid: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserRepositories(): Result<List<Repository>> = withContext(Dispatchers.IO) {
        try {
            val repos = apiService.getUserRepos()
            Result.success(repos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getContents(owner: String, repo: String, path: String = "", branch: String? = null): Result<List<ContentItem>> = 
        withContext(Dispatchers.IO) {
            try {
                val response = if (path.isEmpty()) {
                    apiService.getRepoContents(owner, repo, branch)
                } else {
                    val result = apiService.getContents(owner, repo, path, branch)
                    when (result) {
                        is List<*> -> result.filterIsInstance<ContentItem>()
                        is ContentItem -> listOf(result)
                        else -> emptyList()
                    }
                }
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getFileContent(owner: String, repo: String, path: String): Result<ContentItem> = 
        withContext(Dispatchers.IO) {
            try {
                val file = apiService.getFileContent(owner, repo, path)
                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun createOrUpdateFile(
        owner: String,
        repo: String,
        path: String,
        content: String,  // 原始内容
        commitMessage: String,
        sha: String? = null
    ): Result<CommitResponse> = withContext(Dispatchers.IO) {
        try {
            // GitHub要求文件内容必须Base64编码
            val base64Content = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
            val request = CreateFileRequest(
                message = commitMessage,
                content = base64Content,
                sha = sha
            )
            val response = apiService.createOrUpdateFile(owner, repo, path, request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFile(
        owner: String,
        repo: String,
        path: String,
        sha: String,
        commitMessage: String
    ): Result<CommitResponse> = withContext(Dispatchers.IO) {
        try {
            val request = DeleteFileRequest(
                message = commitMessage,
                sha = sha
            )
            val response = apiService.deleteFile(owner, repo, path, request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 重命名/移动文件：创建新文件后删除旧文件
    suspend fun moveOrRenameFile(
        owner: String,
        repo: String,
        oldPath: String,
        newPath: String,
        content: String,
        sha: String,
        commitMessage: String
    ): Result<CommitResponse> = withContext(Dispatchers.IO) {
        try {
            // 先在新路径创建文件
            val createResult = createOrUpdateFile(
                owner, repo, newPath, content,
                "$commitMessage (create new path)", 
                null  // 新文件不需要SHA
            )
            if (createResult.isFailure) return@withContext createResult
            
            // 再删除旧文件
            val deleteResult = deleteFile(
                owner, repo, oldPath, sha,
                "$commitMessage (delete old path)"
            )
            deleteResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
