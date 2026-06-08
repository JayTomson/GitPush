package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.ProjectDao
import com.example.data.local.ProjectEntity
import com.example.data.local.SettingsRepository
import com.example.ui.GitCommitHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Int,
    projectDao: ProjectDao,
    settingsRepository: SettingsRepository,
    gitCommitHelperFactory: (String) -> GitCommitHelper,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var project by remember { mutableStateOf<ProjectEntity?>(null) }
    var fileList by remember { mutableStateOf<List<String>>(emptyList()) }
    var commitMessage by remember { mutableStateOf("") }
    var isCommitting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    suspend fun refreshFiles() {
        val proj = project ?: return
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(proj.folderUri))
            val list = mutableListOf<String>()
            if (root != null && root.isDirectory) {
                fun collectFiles(dir: DocumentFile, currentPath: String) {
                    dir.listFiles().forEach { file ->
                        if (file.name?.startsWith(".git") == true) return@forEach
                        val path = if (currentPath.isEmpty()) file.name!! else "$currentPath/${file.name}"
                        if (file.isDirectory) collectFiles(file, path) else list.add(path)
                    }
                }
                collectFiles(root, "")
            }
            withContext(Dispatchers.Main) {
                fileList = list
            }
        }
    }

    LaunchedEffect(projectId) {
        project = projectDao.getById(projectId)
        refreshFiles()
    }

    if (project == null) {
        return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCommitting) showDialog = false },
            title = { Text("Commit Changes") },
            text = {
                Column {
                    if (isCommitting) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(16.dp))
                        Text(statusMessage ?: "Working...")
                    } else {
                        OutlinedTextField(
                            value = commitMessage,
                            onValueChange = { commitMessage = it },
                            label = { Text("Commit Message") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (statusMessage != null) {
                            Text(statusMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                if (!isCommitting) {
                    Button(onClick = {
                        val parts = project!!.repo.trim('/').split("/")
                        if (parts.size < 2) {
                            statusMessage = "Invalid repo format. Must contain owner/repo"
                            return@Button
                        }
                        val owner = parts[parts.size - 2]
                        val repoName = parts[parts.size - 1].removeSuffix(".git")

                        coroutineScope.launch {
                            isCommitting = true
                            try {
                                val token = settingsRepository.token.first()
                                if (token.isEmpty()) {
                                    statusMessage = "GitHub token not set in Settings"
                                    isCommitting = false
                                    return@launch
                                }
                                val helper = gitCommitHelperFactory(token)
                                helper.commitFolder(
                                    owner = owner,
                                    repo = repoName,
                                    branch = project!!.branch,
                                    folderUri = Uri.parse(project!!.folderUri),
                                    commitMessage = commitMessage,
                                    onProgress = { statusMessage = it }
                                )
                                showDialog = false
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.localizedMessage}"
                            } finally {
                                isCommitting = false
                            }
                        }
                    }) {
                        Text("Commit & Push")
                    }
                }
            },
            dismissButton = {
                if (!isCommitting) {
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project!!.name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
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
            // Main Active Project Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(20.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                project!!.name,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            )
                            Text(
                                "repo: ${project!!.repo}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                ),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    "branch: ${project!!.branch}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "ACTIVE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Changed Files Box
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "CHANGED FILES",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 2.sp
                                    )
                                )
                                Text(
                                    "${fileList.size} files",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                )
                            }
                            
                            Spacer(Modifier.height(8.dp))

                            Box(modifier = Modifier.heightIn(max = 200.dp)) {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(fileList) { path ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                path,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                                            )
                                            Text(
                                                "MODIFIED",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFFFFB4AB),
                                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                commitMessage = "Update files via Git Commit Manager"
                                showDialog = true
                                statusMessage = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Commit", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        
                        FilledIconButton(
                            onClick = { coroutineScope.launch { refreshFiles() } },
                            modifier = Modifier.size(48.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            }
        }
    }
}
