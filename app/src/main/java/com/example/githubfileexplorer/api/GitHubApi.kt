import retrofit2.http.*
import com.example.githubfileexplorer.model.User
import com.example.githubfileexplorer.model.Repository
import com.example.githubfileexplorer.model.ContentItem
import com.example.githubfileexplorer.model.CommitBody

interface GitHubApi {

    @GET("user")
    suspend fun getUser(@Header("Authorization") auth: String): User

    @GET("user/repos")
    suspend fun listRepos(
        @Header("Authorization") auth: String,
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): List<Repository>

    @GET("repos/{owner}/{repo}/contents/{*path}")
    suspend fun getContents(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): List<ContentItem>  // 目录时返回数组，文件时返回单个对象（需特殊处理）

    // 获取单个文件内容（含 content base64）
    @GET("repos/{owner}/{repo}/contents/{*path}")
    suspend fun getFileContent(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): ContentItem

    // 创建/更新文件
    @PUT("repos/{owner}/{repo}/contents/{*path}")
    suspend fun createOrUpdateFile(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body body: CommitBody
    ): Any

    // 删除文件
    @DELETE("repos/{owner}/{repo}/contents/{*path}")
    suspend fun deleteFile(
        @Header("Authorization") auth: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("message") message: String,
        @Query("sha") sha: String
    ): Any
}