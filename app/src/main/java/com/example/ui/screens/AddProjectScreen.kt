package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.local.SettingsRepository
import com.example.data.remote.CreateRepoRequest
import com.example.data.remote.GitHubService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProjectScreen(
    projectDao: ProjectDao,
    settingsRepository: SettingsRepository,
    githubService: GitHubService,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val token by settingsRepository.token.collectAsState(initial = "")

    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("main") }
    var folderUri by remember { mutableStateOf<Uri?>(null) }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var repoUrlOrName by remember { mutableStateOf("") }
    var newRepoName by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(false) }
    
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val documentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            folderUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Project") },
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
                .fillMaxSize()
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0; errorMessage = null },
                    text = { Text("Link Existing") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1; errorMessage = null },
                    text = { Text("Create New") }
                )
            }

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name (Display)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = repoUrlOrName,
                        onValueChange = { repoUrlOrName = it },
                        label = { Text("Repository (e.g., owner/repo)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = newRepoName,
                        onValueChange = { newRepoName = it },
                        label = { Text("New Repository Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Private Repository", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onBackground)
                        Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                    }
                    if (token.isEmpty()) {
                        Text("Warning: GitHub Token is required to create a repository. Please set it in Settings.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch (default: main)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = { documentTreeLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(if (folderUri == null) "Select Project Folder" else "Folder Selected", fontWeight = FontWeight.Bold)
                }

                if (folderUri != null) {
                    Text(
                        "Selected: ${folderUri.toString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                if (errorMessage != null) {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.weight(1f))

                val isFormValid = name.isNotBlank() && folderUri != null && 
                        if (selectedTab == 0) repoUrlOrName.isNotBlank() else (newRepoName.isNotBlank() && token.isNotEmpty())

                Button(
                    onClick = {
                        if (selectedTab == 0) {
                            coroutineScope.launch {
                                projectDao.insert(
                                    ProjectEntity(
                                        name = name,
                                        repo = repoUrlOrName,
                                        branch = branch.ifBlank { "main" },
                                        folderUri = folderUri.toString()
                                    )
                                )
                                onNavigateBack()
                            }
                        } else {
                            coroutineScope.launch {
                                isSaving = true
                                errorMessage = null
                                try {
                                    val response = githubService.createRepository(
                                        auth = "Bearer $token",
                                        request = CreateRepoRequest(
                                            name = newRepoName,
                                            private = isPrivate,
                                            auto_init = true
                                        )
                                    )
                                    projectDao.insert(
                                        ProjectEntity(
                                            name = name,
                                            repo = response.full_name,
                                            branch = branch.ifBlank { "main" },
                                            folderUri = folderUri.toString()
                                        )
                                    )
                                    onNavigateBack()
                                } catch (e: Exception) {
                                    errorMessage = "Error creating repository: ${e.localizedMessage}"
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    enabled = isFormValid && !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (selectedTab == 0) "Save Project" else "Create & Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
