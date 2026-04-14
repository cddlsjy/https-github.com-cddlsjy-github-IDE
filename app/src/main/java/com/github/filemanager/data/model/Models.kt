package com.github.filemanager.data.model

import com.google.gson.annotations.SerializedName

// 用户信息
data class User(
    val id: Long,
    val login: String,
    val name: String?,
    val email: String?,
    val avatar_url: String?
)

// 仓库信息
data class Repository(
    val id: Long,
    val name: String,
    @SerializedName("full_name")
    val full_name: String,
    val owner: Owner,
    val description: String?,
    @SerializedName("private")
    val isPrivate: Boolean,
    @SerializedName("default_branch")
    val defaultBranch: String?
)

data class Owner(
    val login: String,
    val id: Long
)

// GitHub Contents API 响应模型
data class ContentItem(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val url: String,
    @SerializedName("html_url")
    val html_url: String?,
    @SerializedName("git_url")
    val git_url: String?,
    @SerializedName("download_url")
    val download_url: String?,
    val type: String,  // "file" or "dir"
    val content: String? = null,
    val encoding: String? = null,
    @SerializedName("_links")
    val links: Links? = null
)

data class Links(
    val self: String,
    val git: String?,
    val html: String?
)

// 文件树节点（用于UI展示）
data class FileNode(
    val name: String,
    val path: String,
    val type: String,  // "file" or "dir"
    val sha: String? = null,
    val size: Long = 0,
    val downloadUrl: String? = null
)

// PUT请求体（创建/更新文件）
data class CreateFileRequest(
    val message: String,
    val content: String,  // Base64编码后的内容
    val sha: String? = null,  // 更新时必须提供
    val branch: String? = null
)

// DELETE请求体
data class DeleteFileRequest(
    val message: String,
    val sha: String,
    val branch: String? = null
)

// API响应中的commit信息
data class CommitResponse(
    val content: ContentItem?,
    val commit: CommitInfo
)

data class CommitInfo(
    val sha: String,
    val message: String,
    val author: AuthorInfo
)

data class AuthorInfo(
    val name: String,
    val email: String,
    val date: String
)
