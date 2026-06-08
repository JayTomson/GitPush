package com.example.data.remote

import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*

@JsonClass(generateAdapter = true)
data class GitHubRef(
    val ref: String,
    val `object`: GitHubObject
)

@JsonClass(generateAdapter = true)
data class GitHubObject(
    val sha: String,
    val type: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class GitHubCommit(
    val sha: String,
    val tree: GitHubCommitTree
)

@JsonClass(generateAdapter = true)
data class GitHubCommitTree(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    val name: String,
    val description: String? = null,
    val private: Boolean = false,
    val auto_init: Boolean = true
)

@JsonClass(generateAdapter = true)
data class RepositoryResponse(
    val full_name: String,
    val html_url: String
)

@JsonClass(generateAdapter = true)
data class GitHubUser(
    val login: String,
    val id: Long
)

@JsonClass(generateAdapter = true)
data class CreateBlobRequest(
    val content: String,
    val encoding: String = "base64"
)

@JsonClass(generateAdapter = true)
data class CreateBlobResponse(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class TreeItem(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String? = null,
    val content: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateTreeRequest(
    val base_tree: String,
    val tree: List<TreeItem>
)

@JsonClass(generateAdapter = true)
data class CreateTreeResponse(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class CreateCommitRequest(
    val message: String,
    val parents: List<String>,
    val tree: String
)

@JsonClass(generateAdapter = true)
data class CreateCommitResponse(
    val sha: String
)

@JsonClass(generateAdapter = true)
data class UpdateRefRequest(
    val sha: String,
    val force: Boolean = false
)

interface GitHubService {
    @GET("user")
    suspend fun getUser(
        @Header("Authorization") auth: String
    ): GitHubUser

    @POST("user/repos")
    suspend fun createRepository(
        @Header("Authorization") auth: String,
        @Body request: CreateRepoRequest
    ): RepositoryResponse

    @GET("repos/{owner}/{repo}/git/ref/heads/{branch}")
    suspend fun getBranchRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Header("Authorization") auth: String
    ): GitHubRef

    @GET("repos/{owner}/{repo}/git/commits/{sha}")
    suspend fun getCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("sha") sha: String,
        @Header("Authorization") auth: String
    ): GitHubCommit

    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") auth: String,
        @Body request: CreateBlobRequest
    ): CreateBlobResponse

    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") auth: String,
        @Body request: CreateTreeRequest
    ): CreateTreeResponse

    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Header("Authorization") auth: String,
        @Body request: CreateCommitRequest
    ): CreateCommitResponse

    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Header("Authorization") auth: String,
        @Body request: UpdateRefRequest
    )
    
    companion object {
        fun create(): GitHubService {
            return Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(GitHubService::class.java)
        }
    }
}
