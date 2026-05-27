package com.thai2chinese

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thai2chinese.data.AppConfig
import com.thai2chinese.ui.home.HomeScreen
import com.thai2chinese.ui.player.PlayerScreen
import com.thai2chinese.ui.processing.ProcessingScreen
import com.thai2chinese.ui.settings.SettingsScreen
import com.thai2chinese.ui.theme.BgMain
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val config = AppConfig.getInstance(this)
            val startDestination = if (config.isConfigured) "home" else "settings/true"
            Scaffold(modifier = Modifier.fillMaxSize().background(BgMain)) { padding ->
                NavHost(navController = navController, startDestination = startDestination,
                    modifier = Modifier.fillMaxSize().background(BgMain).padding(padding)) {
                    composable("home") {
                        HomeScreen(
                            onNavigateToPlayer = { navController.navigate("player/$it") },
                            onNavigateToProcessing = { uri, name ->
                                navController.navigate("processing/${URLEncoder.encode(uri, "UTF-8")}/${URLEncoder.encode(name, "UTF-8")}")
                            },
                            onNavigateToSettings = { navController.navigate("settings/false") }
                        )
                    }
                    composable("settings/{fromSetup}",
                        arguments = listOf(navArgument("fromSetup") { type = NavType.BoolType; defaultValue = false })) { entry ->
                        val fromSetup = entry.arguments?.getBoolean("fromSetup") ?: false
                        SettingsScreen(
                            onBack = {
                                if (fromSetup) navController.navigate("home") { popUpTo(0) { inclusive = true } }
                                else navController.popBackStack()
                            },
                            config = config
                        )
                    }
                    composable("processing/{videoUri}/{filename}",
                        arguments = listOf(
                            navArgument("videoUri") { type = NavType.StringType },
                            navArgument("filename") { type = NavType.StringType }
                        )) { entry ->
                        val uri = URLDecoder.decode(entry.arguments?.getString("videoUri") ?: "", "UTF-8")
                        val name = URLDecoder.decode(entry.arguments?.getString("filename") ?: "", "UTF-8")
                        ProcessingScreen(videoUri = uri, filename = name,
                            onNavigateToPlayer = { navController.navigate("player/$it") { popUpTo("home") { inclusive = false } } },
                            onBack = { navController.popBackStack() })
                    }
                    composable("player/{taskId}",
                        arguments = listOf(navArgument("taskId") { type = NavType.StringType })) { entry ->
                        PlayerScreen(taskId = entry.arguments?.getString("taskId") ?: "",
                            onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
