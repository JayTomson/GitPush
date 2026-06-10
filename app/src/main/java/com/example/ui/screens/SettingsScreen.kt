package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.SettingsRepository
import com.example.data.remote.GitHubService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    githubService: GitHubService,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    val savedUsername by settingsRepository.username.collectAsStateWithLifecycle("")
    val savedToken by settingsRepository.token.collectAsStateWithLifecycle("")

    var username by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var isConnected by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(savedUsername) {
        if (savedUsername.isNotEmpty() && username.isEmpty()) {
            username = savedUsername
        }
    }

    LaunchedEffect(savedToken) {
        if (savedToken.isNotEmpty() && token.isEmpty()) {
            token = savedToken
            try {
                val user = githubService.getUser("Bearer $savedToken")
                isConnected = true
                username = user.login
            } catch (e: Exception) {
                isConnected = false
            }
        } else if (savedToken.isEmpty()) {
            isConnected = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "GitHub Credentials",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (isConnected != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isConnected == true) 
                            Icons.Default.Done 
                        else 
                            Icons.Default.Warning,
                        contentDescription = "Status",
                        tint = if (isConnected == true) 
                            androidx.compose.ui.graphics.Color(0xFF3DDC84) 
                        else 
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isConnected == true) "Connected as $username" else "Not Connected or Invalid Token",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConnected == true) 
                            androidx.compose.ui.graphics.Color(0xFF3DDC84) 
                        else 
                            MaterialTheme.colorScheme.error,
                    )
                }
            }
            
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("GitHub Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Personal Access Token") },
                supportingText = { Text("Requires 'repo' scope to create repositories and commit.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("ghp_...") }
            )

            Spacer(modifier = Modifier.weight(1f))

            if (token.isNotEmpty() || username.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            settingsRepository.saveSettings("", "")
                            username = ""
                            token = ""
                            isConnected = false
                            snackbarHostState.showSnackbar("Disconnected successfully.")
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }

            Button(
                onClick = {
                    coroutineScope.launch {
                        isTesting = true
                        try {
                            val user = githubService.getUser("Bearer $token")
                            settingsRepository.saveSettings(user.login, token) // Update to real username just in case
                            snackbarHostState.showSnackbar("Connected successfully as ${user.login}!")
                            kotlinx.coroutines.delay(1000)
                            onNavigateBack()
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Connection failed. Check your token.")
                        } finally {
                            isTesting = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = token.isNotBlank() && !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Connect & Save", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    }
}
