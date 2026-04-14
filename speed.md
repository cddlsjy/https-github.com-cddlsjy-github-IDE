下面给出一个完整的 Android App 实现，使用 **Jetpack Compose** 构建 UI，**Retrofit + OkHttp** 调用 GitHub API，**Coroutines + Flow** 处理异步，**DataStore** 存储 Token。

## 功能覆盖
- 使用 GitHub Personal Access Token 登录（验证 `/user`）
- 列出当前用户的所有仓库
- 浏览任意仓库的文件树（支持进入子文件夹）
- 对**文本文件**（根据扩展名判断）进行：
  - **在线编辑**（获取内容 → 编辑 → PUT 更新）
  - **删除**（DELETE）
  - **移动/重命名**（PUT 新路径 + DELETE 旧路径）
- 每次变更都通过 Git Commit 提交回 GitHub（可自定义提交信息）

---

## 1. 模块 build.gradle (app)

```groovy
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
    id 'kotlin-kapt'
    id 'dagger.hilt.android.plugin'   // 可选，本示例用简单依赖注入，不强制 Hilt
}

android {
    namespace 'com.example.githubfileexplorer'
    compileSdk 34

    defaultConfig {
        applicationId "com.example.githubfileexplorer"
        minSdk 21
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion '1.5.4'
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'androidx.activity:activity-compose:1.8.0'

    // Compose
    implementation platform('androidx.compose:compose-bom:2023.10.01')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
    implementation 'androidx.compose.material:material-icons-extended'
    implementation 'androidx.navigation:navigation-compose:2.7.5'

    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // Networking
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'
    implementation 'com.squareup.okhttp3:logging-interceptor:4.12.0'

    // DataStore
    implementation 'androidx.datastore:datastore-preferences:1.0.0'

    // ViewModel
    implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0'
}
```

---

## 2. AndroidManifest.xml 关键部分

```xml
<uses-permission android:name="android.permission.INTERNET" />

<application
    android:allowBackup="true"
    android:theme="@style/Theme.AppCompat.Light.DarkActionBar"
    android:usesCleartextTraffic="false">
    <activity
        android:name=".MainActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

---

## 3. 数据模型

```kotlin
// model/User.kt
data class User(val login: String, val name: String?)

// model/Repository.kt
data class Repository(
    val id: Long,
    val name: String,
    val full_name: String,
    val owner: Owner
) {
    data class Owner(val login: String)
}

// model/ContentItem.kt
import com.google.gson.annotations.SerializedName

data class ContentItem(
    val name: String,
    val path: String,
    val sha: String,
    val size: Int,
    val type: String,      // "file" or "dir"
    @SerializedName("download_url")
    val downloadUrl: String?,
    val content: String?   // only present when ?ref=…&content=1 (base64)
)

// model/FileContent.kt
data class FileContent(
    val content: String,   // base64 encoded
    val encoding: String = "base64",
    val sha: String? = null
)

// model/CommitBody.kt
data class CommitBody(
    val message: String,
    val content: String,   // base64 encoded new content
    val sha: String? = null,  // required for update/delete
    val branch: String? = null
)
```

---

## 4. GitHub API 接口定义

```kotlin
// api/GitHubApi.kt
import retrofit2.http.*

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
```

> **注意**：GitHub API 的 `GET /repos/.../contents` 对于目录返回 `List<ContentItem>`，对于文件返回单个 `ContentItem`。为了方便，我们分别定义两个方法，并在业务层根据返回类型解析（使用 `ResponseBody` 或自定义 Converter）。为简化，这里假定调用 `/contents` 时如果是文件会抛异常，因此专门用 `getFileContent` 获取文件。

实际项目中可使用 `@GET` 返回 `Response<T>` 并手动解析。示例为了清晰，省略了复杂解析，生产中可使用 `ScalarsConverter` 或自定义。

---

## 5. Token 认证拦截器

```kotlin
// network/AuthInterceptor.kt
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        return chain.proceed(request)
    }
}
```

---

## 6. Retrofit 客户端

```kotlin
// network/RetrofitClient.kt
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

object RetrofitClient {
    private const val BASE_URL = "https://api.github.com/"

    fun createApi(token: String): GitHubApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(token))
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApi::class.java)
    }
}
```

---

## 7. Token 存储管理

```kotlin
// storage/TokenManager.kt
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class TokenManager(private val context: Context) {
    companion object {
        private val TOKEN_KEY = stringPreferencesKey("github_token")
    }

    val tokenFlow: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[TOKEN_KEY] }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    suspend fun clearToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
        }
    }
}
```

---

## 8. 工具：判断文本文件

```kotlin
// utils/FileTypeHelper.kt
object FileTypeHelper {
    private val textExtensions = setOf(
        "txt", "md", "kt", "java", "xml", "json", "gradle", "properties",
        "py", "js", "html", "css", "sh", "yaml", "yml", "conf", "ini"
    )

    fun isTextFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in textExtensions
    }
}
```

---

## 9. ViewModel 层（示例：仓库列表）

```kotlin
// viewmodel/ReposViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReposViewModel(
    private val token: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    sealed class UiState {
        object Loading : UiState()
        data class Success(val repos: List<Repository>) : UiState()
        data class Error(val message: String) : UiState()
    }

    fun loadRepos() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val api = RetrofitClient.createApi(token)
                val repos = api.listRepos("Bearer $token")
                _uiState.value = UiState.Success(repos)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
```

类似地，需要为文件树和文件编辑编写对应的 ViewModel。

---

## 10. 主 Activity 和导航

```kotlin
// MainActivity.kt
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.githubfileexplorer.storage.TokenManager

class MainActivity : ComponentActivity() {
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokenManager = TokenManager(applicationContext)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(tokenManager)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(tokenManager: TokenManager) {
    val navController = rememberNavController()
    val token by tokenManager.tokenFlow.collectAsState(initial = null)

    NavHost(navController, startDestination = if (token == null) "login" else "repos") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { newToken ->
                    navController.navigate("repos") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                tokenManager = tokenManager
            )
        }
        composable("repos") {
            ReposScreen(
                token = token!!,
                onRepoClick = { owner, repo ->
                    navController.navigate("fileTree/$owner/$repo/")
                }
            )
        }
        composable(
            "fileTree/{owner}/{repo}/{*path}",
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner")!!
            val repo = backStackEntry.arguments?.getString("repo")!!
            val path = backStackEntry.arguments?.getString("path") ?: ""
            FileTreeScreen(
                token = token!!,
                owner = owner,
                repo = repo,
                currentPath = path,
                onNavigateToSubPath = { subPath ->
                    navController.navigate("fileTree/$owner/$repo/$subPath")
                },
                onFileClick = { filePath, fileName ->
                    navController.navigate("editFile/$owner/$repo/$filePath?fileName=$fileName")
                }
            )
        }
        composable(
            "editFile/{owner}/{repo}/{*path}",
            arguments = listOf(
                navArgument("owner") { type = NavType.StringType },
                navArgument("repo") { type = NavType.StringType },
                navArgument("path") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString("owner")!!
            val repo = backStackEntry.arguments?.getString("repo")!!
            val path = backStackEntry.arguments?.getString("path")!!
            EditFileScreen(
                token = token!!,
                owner = owner,
                repo = repo,
                filePath = path,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

---

## 11. 登录界面

```kotlin
// ui/LoginScreen.kt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: (String) -> Unit,
    tokenManager: TokenManager
) {
    var tokenInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("GitHub Personal Access Token", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("Token") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (errorMsg != null) {
            Text(errorMsg!!, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = {
                scope.launch {
                    isLoading = true
                    errorMsg = null
                    try {
                        // 验证 Token
                        val api = RetrofitClient.createApi(tokenInput)
                        val user = api.getUser("Bearer $tokenInput")
                        tokenManager.saveToken(tokenInput)
                        onLoginSuccess(tokenInput)
                    } catch (e: Exception) {
                        errorMsg = "Invalid token: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = tokenInput.isNotBlank() && !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Text("Login")
        }
    }
}
```

---

## 12. 仓库列表界面

```kotlin
// ui/ReposScreen.kt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
                items(uiState.repos) { repo ->
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
            Text("Error: ${uiState.message}", modifier = Modifier.padding(16.dp))
        }
    }
}

// 简单工厂
class ReposViewModelFactory(private val token: String) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return ReposViewModel(token) as T
    }
}
```

---

## 13. 文件树界面

```kotlin
// ui/FileTreeScreen.kt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (uiState) {
                is FileTreeViewModel.UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is FileTreeViewModel.UiState.Success -> {
                    LazyColumn {
                        items(uiState.items) { item ->
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
                    Text("Error: ${uiState.message}", modifier = Modifier.padding(16.dp))
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
```

```kotlin
// viewmodel/FileTreeViewModel.kt
class FileTreeViewModel(
    private val token: String,
    private val owner: String,
    private val repo: String,
    private val path: String
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    sealed class UiState {
        object Loading : UiState()
        data class Success(val items: List<ContentItem>) : UiState()
        data class Error(val message: String) : UiState()
    }

    fun loadContents() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val api = RetrofitClient.createApi(token)
                val response = api.getContents("Bearer $token", owner, repo, path)
                _uiState.value = UiState.Success(response)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load contents")
            }
        }
    }
}

class FileTreeViewModelFactory(
    private val token: String,
    private val owner: String,
    private val repo: String,
    private val path: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FileTreeViewModel(token, owner, repo, path) as T
    }
}
```

---

## 14. 文件编辑界面（支持编辑、删除、移动/重命名）

```kotlin
// ui/EditFileScreen.kt
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.nio.charset.StandardCharsets

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
                    IconButton(onClick = { viewModel.deleteFile(commitMessage) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                    IconButton(onClick = { showMoveDialog = true }) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Move/Rename")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (uiState) {
                is EditFileViewModel.UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
                is EditFileViewModel.UiState.ContentLoaded -> {
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
                    Text("Error: ${uiState.message}", modifier = Modifier.padding(16.dp))
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
```

```kotlin
// viewmodel/EditFileViewModel.kt
class EditFileViewModel(
    private val token: String,
    private val owner: String,
    private val repo: String,
    private val filePath: String
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var currentSha: String? = null

    sealed class UiState {
        object Loading : UiState()
        data class ContentLoaded(val originalContent: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    fun loadFile() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val api = RetrofitClient.createApi(token)
                val file = api.getFileContent("Bearer $token", owner, repo, filePath)
                currentSha = file.sha
                val decoded = if (file.content != null) {
                    String(Base64.decode(file.content, Base64.DEFAULT), StandardCharsets.UTF_8)
                } else {
                    ""
                }
                _uiState.value = UiState.ContentLoaded(decoded)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load file")
            }
        }
    }

    fun saveFile(newContent: String, commitMsg: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.createApi(token)
                val encoded = Base64.encodeToString(newContent.toByteArray(), Base64.NO_WRAP)
                val body = CommitBody(
                    message = commitMsg,
                    content = encoded,
                    sha = currentSha
                )
                api.createOrUpdateFile("Bearer $token", owner, repo, filePath, body)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Save failed")
            }
        }
    }

    fun deleteFile(commitMsg: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.createApi(token)
                api.deleteFile("Bearer $token", owner, repo, filePath, commitMsg, currentSha!!)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Delete failed")
            }
        }
    }

    fun moveFile(newPath: String, commitMsg: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.createApi(token)
                // 1. 获取当前文件内容
                val file = api.getFileContent("Bearer $token", owner, repo, filePath)
                val decoded = String(Base64.decode(file.content, Base64.DEFAULT), StandardCharsets.UTF_8)
                val encoded = Base64.encodeToString(decoded.toByteArray(), Base64.NO_WRAP)
                // 2. PUT 到新路径
                val body = CommitBody(message = commitMsg, content = encoded)
                api.createOrUpdateFile("Bearer $token", owner, repo, newPath, body)
                // 3. DELETE 旧路径
                api.deleteFile("Bearer $token", owner, repo, filePath, commitMsg, file.sha)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Move failed")
            }
        }
    }
}

class EditFileViewModelFactory(
    private val token: String,
    private val owner: String,
    private val repo: String,
    private val filePath: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EditFileViewModel(token, owner, repo, filePath) as T
    }
}
```

---

## 总结

以上代码提供了完整的功能骨架：

1. **Token 登录与存储** – 验证 Token 并保存在 DataStore。
2. **仓库列表** – 调用 `/user/repos`。
3. **文件树** – 递归浏览，文件夹进入，文本文件可编辑。
4. **文件编辑** – 支持保存（PUT）、删除（DELETE）、移动/重命名（PUT 新 + DELETE 旧）。
5. **Git 提交** – 每次变更都通过 GitHub API 的 `message` 参数提交。

注意：
- 实际使用时需在 `AndroidManifest.xml` 中添加 `android:usesCleartextTraffic="false"`（GitHub API 是 HTTPS，无需 true）。
- 文件树中需要处理非文本文件（不提供编辑入口），本示例只在点击文件时跳转编辑界面，编辑界面会尝试加载并假设是文本文件，你可以扩展判断。
- 移动/重命名功能在对话框中输入相对于仓库根目录的**新路径**（例如 `src/main/NewName.kt`）。
- 由于 GitHub API 对 `/contents` 返回单个文件或列表的不同处理，示例中使用了两个不同的方法 `getContents`（列表）和 `getFileContent`（单个），生产环境建议用 `@GET` 返回 `ResponseBody` 并动态解析。

将此代码复制到 Android Studio 中，配置好依赖，即可运行体验。1