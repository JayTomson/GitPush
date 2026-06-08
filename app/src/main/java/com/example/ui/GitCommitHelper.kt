package com.example.ui

import android.net.Uri
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.example.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import java.io.InputStream

class GitCommitHelper(
    private val service: GitHubService,
    private val context: Context,
    private val token: String
) {
    private val authHeader = "Bearer $token"

    suspend fun commitFolder(
        owner: String,
        repo: String,
        branch: String,
        folderUri: Uri,
        commitMessage: String,
        onProgress: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            onProgress("Getting latest commit on $branch...")
            val ref = service.getBranchRef(owner, repo, branch, authHeader)
            val latestCommitSha = ref.`object`.sha

            onProgress("Getting base tree...")
            val currentCommit = service.getCommit(owner, repo, latestCommitSha, authHeader)
            val baseTreeSha = currentCommit.tree.sha

            val rootDocFile = DocumentFile.fromTreeUri(context, folderUri)
            if (rootDocFile == null || !rootDocFile.isDirectory) {
                throw Exception("Invalid folder selected")
            }

            val allFiles = getFilesRecursively(rootDocFile)
            val treeItems = mutableListOf<TreeItem>()

            for ((path, file) in allFiles) {
                onProgress("Uploading $path...")
                
                // Read and encode file
                val inputStream: InputStream? = context.contentResolver.openInputStream(file.uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()
                val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)

                val blobResponse = service.createBlob(owner, repo, authHeader, CreateBlobRequest(content = base64Content))
                treeItems.add(
                    TreeItem(
                        path = path,
                        mode = "100644", // file (blob)
                        type = "blob",
                        sha = blobResponse.sha
                    )
                )
            }

            onProgress("Creating commit tree...")
            val newTreeRes = service.createTree(
                owner, repo, authHeader,
                CreateTreeRequest(base_tree = baseTreeSha, tree = treeItems)
            )

            onProgress("Creating commit...")
            val newCommitRes = service.createCommit(
                owner, repo, authHeader,
                CreateCommitRequest(
                    message = commitMessage,
                    parents = listOf(latestCommitSha),
                    tree = newTreeRes.sha
                )
            )

            onProgress("Updating branch reference...")
            service.updateRef(
                owner, repo, branch, authHeader,
                UpdateRefRequest(sha = newCommitRes.sha)
            )

            onProgress("Success!")
        } catch (e: Exception) {
            onProgress("Error: ${e.message}")
            throw e
        }
    }

    private fun getFilesRecursively(parent: DocumentFile, currentPath: String = ""): List<Pair<String, DocumentFile>> {
        val result = mutableListOf<Pair<String, DocumentFile>>()
        for (file in parent.listFiles()) {
            // Ignore .git and common unwanted folders (like build/node_modules mostly? Let's just ignore .git)
            if (file.name?.startsWith(".git") == true) continue
            
            val path = if (currentPath.isEmpty()) file.name!! else "$currentPath/${file.name}"
            if (file.isDirectory) {
                result.addAll(getFilesRecursively(file, path))
            } else {
                result.add(path to file)
            }
        }
        return result
    }
}
