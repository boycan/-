package com.waa.assistant.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.waa.assistant.ui.chats.ChatsScreen
import com.waa.assistant.ui.home.HomeScreen
import com.waa.assistant.ui.logs.LogsScreen
import com.waa.assistant.ui.knowledge.KnowledgeScreen
import com.waa.assistant.ui.permissions.PermissionsScreen
import com.waa.assistant.ui.review.ReviewScreen
import com.waa.assistant.ui.rules.RulesScreen
import com.waa.assistant.ui.settings.SettingsScreen
import com.waa.assistant.ui.theme.WaaTheme

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            WaaTheme {
                WaaAppRoot()
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun WaaAppRoot(vm: AppViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    val pending by vm.pendingReviewCount.collectAsState()
    val nav = rememberNavController()
    val start = if (settings.permissionIntroAccepted) "home" else "permissions"

    val tabs = listOf(
        Tab("home", "首页", Icons.Default.Home),
        Tab("chats", "会话", Icons.Default.Chat),
        Tab("review", "审核", Icons.Default.VerifiedUser),
        Tab("rules", "规则", Icons.Default.Rule),
        Tab("logs", "日志", Icons.Default.ListAlt),
        Tab("knowledge", "知识库", Icons.Default.LibraryBooks),
        Tab("settings", "设置", Icons.Default.Settings)
    )

    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (current != "permissions") {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab.route == "review" && pending > 0) {
                                    BadgedBox(badge = { Badge { Text("$pending") } }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = start,
            modifier = Modifier.padding(padding)
        ) {
            composable("permissions") {
                PermissionsScreen(
                    onAccepted = {
                        vm.acceptPermissions()
                        nav.navigate("home") {
                            popUpTo("permissions") { inclusive = true }
                        }
                    }
                )
            }
            composable("home") { HomeScreen(vm) }
            composable("chats") { ChatsScreen(vm) }
            composable("review") { ReviewScreen(vm) }
            composable("rules") { RulesScreen(vm) }
            composable("logs") { LogsScreen(vm) }
            composable("knowledge") { KnowledgeScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
