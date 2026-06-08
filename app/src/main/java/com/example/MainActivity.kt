package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import com.example.data.local.AppDatabase
import com.example.data.local.SettingsRepository
import com.example.data.remote.GitHubService
import com.example.ui.GitCommitHelper
import com.example.ui.screens.AddProjectScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProjectDetailScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private lateinit var database: AppDatabase
  private lateinit var settingsRepository: SettingsRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    database = Room.databaseBuilder(
      applicationContext,
      AppDatabase::class.java, "git-database"
    ).build()
    
    settingsRepository = SettingsRepository(applicationContext)

    setContent {
      MyApplicationTheme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          val navController = rememberNavController()
          val githubService = GitHubService.create()

          NavHost(navController = navController, startDestination = "home") {
            composable("home") {
              HomeScreen(
                projectDao = database.projectDao(),
                onNavigateToAdd = { navController.navigate("add") },
                onNavigateToProject = { id -> navController.navigate("project/$id") },
                onNavigateToSettings = { navController.navigate("settings") }
              )
            }
            composable("settings") {
              SettingsScreen(
                settingsRepository = settingsRepository,
                githubService = githubService,
                onNavigateBack = { navController.popBackStack() }
              )
            }
            composable("add") {
              AddProjectScreen(
                projectDao = database.projectDao(),
                settingsRepository = settingsRepository,
                githubService = githubService,
                onNavigateBack = { navController.popBackStack() }
              )
            }
            composable(
              route = "project/{id}",
              arguments = listOf(navArgument("id") { type = NavType.IntType })
            ) { backStackEntry ->
              val id = backStackEntry.arguments?.getInt("id") ?: return@composable
              ProjectDetailScreen(
                projectId = id,
                projectDao = database.projectDao(),
                settingsRepository = settingsRepository,
                gitCommitHelperFactory = { token ->
                   GitCommitHelper(githubService, applicationContext, token)
                },
                onNavigateBack = { navController.popBackStack() }
              )
            }
          }
        }
      }
    }
  }
}

