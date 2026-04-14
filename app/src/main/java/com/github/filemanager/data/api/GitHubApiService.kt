package com.github.filemanager.data.api

import com.github.filemanager.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface GitHubApiService {

    // 获取当前用户信息（验证Token）
    @GET("user")
    suspend fun getCurrentUser(): Response<User>

    // 获取用户的所有仓库
    @GET("user/repos")
    suspend fun getUserRepos(
        @Query("per_page") perPage: Int = 100,
        @Query("sort") sort: String = "updated"
    ): List<Repository>

    // 获取仓库根目录内容
    @GET("repos/{owner}/{repo}/contents")
    suspend fun getRepoContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("ref") branch: String? = null
    ): List<ContentItem>

    // 获取指定路径的内容（目录或文件）
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getContents(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") branch: String? = null
    ): Any  // 可能是 ContentItem 或 List<ContentItem>

    // 获取单个文件内容（含Base64内容）
    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") branch: String? = null
    ): ContentItem

    // 创建或更新文件
    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: CreateFileRequest
    ): CommitResponse

    // 删除文件
    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: DeleteFileRequest
    ): CommitResponse
}
