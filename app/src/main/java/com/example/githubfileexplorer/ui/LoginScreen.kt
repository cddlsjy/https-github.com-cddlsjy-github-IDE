import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.githubfileexplorer.storage.TokenManager
import com.example.githubfileexplorer.network.RetrofitClient

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