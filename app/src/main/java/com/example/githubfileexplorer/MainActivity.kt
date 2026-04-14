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
import com.example.githubfileexplorer.ui.LoginScreen
import com.example.githubfileexplorer.ui.ReposScreen
import com.example.githubfileexplorer.ui.FileTreeScreen
import com.example.githubfileexplorer.ui.EditFileScreen

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
        ) {
            backStackEntry ->
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
        ) {
            backStackEntry ->
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