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

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.data.remote.RepositoryResponse

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

    var searchQuery by remember { mutableStateOf("") }
    var userRepos by remember { mutableStateOf<List<RepositoryResponse>>(emptyList()) }
    var isLoadingRepos by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf<String?>(null) }
    var confirmTypedName by remember { mutableStateOf("") }

    LaunchedEffect(selectedTab, token) {
        if (selectedTab == 0 && token.isNotEmpty() && userRepos.isEmpty()) {
            isLoadingRepos = true
            try {
                userRepos = githubService.getUserRepos("Bearer $token")
            } catch (e: Exception) {
            } finally {
                isLoadingRepos = false
            }
        }
    }

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

    if (showConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { 
                showConfirmDialog = null
                confirmTypedName = "" 
            },
            title = { Text("Confirm Link") },
            text = {
                Column {
                    Text("Type the repository name to connect:")
                    Text(showConfirmDialog!!, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                    OutlinedTextField(
                        value = confirmTypedName,
                        onValueChange = { confirmTypedName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repoUrlOrName = showConfirmDialog!!
                        showConfirmDialog = null
                        confirmTypedName = ""
                    },
                    enabled = confirmTypedName == showConfirmDialog!!
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showConfirmDialog = null
                    confirmTypedName = "" 
                }) { Text("Cancel") }
            }
        )
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
                    if (token.isEmpty()) {
                        OutlinedTextField(
                            value = repoUrlOrName,
                            onValueChange = { repoUrlOrName = it },
                            label = { Text("Repository (e.g., owner/repo)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text("Connect a GitHub token in settings to see your repositories list.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search Repositories") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (isLoadingRepos) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            val filteredRepos = userRepos.filter { it.full_name.contains(searchQuery, ignoreCase = true) }
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredRepos, key = { it.full_name }) { repo ->
                                    Card(
                                        onClick = { showConfirmDialog = repo.full_name },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (repoUrlOrName == repo.full_name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Text(
                                            text = repo.full_name,
                                            modifier = Modifier.padding(16.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (repoUrlOrName == repo.full_name) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
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

                if (selectedTab != 0 || token.isEmpty()) {
                    Spacer(modifier = Modifier.weight(1f))
                }

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
                                } catch (e: retrofit2.HttpException) {
                                    if (e.code() == 403) {
                                        errorMessage = "Error: 403 Forbidden. Is your token provided with 'repo' scope?"
                                    } else {
                                        errorMessage = "Error creating repository: ${e.message()}"
                                    }
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
