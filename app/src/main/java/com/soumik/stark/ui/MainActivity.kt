package com.soumik.stark.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soumik.stark.tracking.service.TrackingController
import com.soumik.stark.tracking.service.TrackingForegroundService
import com.soumik.stark.ui.more.MoreScreen
import com.soumik.stark.ui.speedo.SpeedoScreen
import com.soumik.stark.ui.theme.StarkTheme
import com.soumik.stark.ui.timeline.TripsScreen
import com.soumik.stark.ui.today.TodayScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StarkTheme { StarkRoot() }
        }
    }
}

private sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Today : Dest("today", "Today", Icons.Filled.Today)
    data object Trips : Dest("trips", "Trips", Icons.AutoMirrored.Filled.List)
    data object Speed : Dest("speedo", "Speed", Icons.Filled.Speed)
    data object More : Dest("more", "More", Icons.Filled.MoreHoriz)
}

private val bottomDests = listOf(Dest.Today, Dest.Trips, Dest.Speed, Dest.More)

@Composable
private fun StarkRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute != Dest.Speed.route

    val context = androidx.compose.ui.platform.LocalContext.current

    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort; tracking runs regardless once started from foreground */ }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine) {
            TrackingForegroundService.start(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            ) {
                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    fun startTracking() {
        if (hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            TrackingForegroundService.start(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            ) {
                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms += Manifest.permission.POST_NOTIFICATIONS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                perms += Manifest.permission.ACTIVITY_RECOGNITION
            }
            permLauncher.launch(perms.toTypedArray())
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomDests.forEach { dest ->
                        val selected = currentRoute == dest.route ||
                            backStack?.destination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(dest.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Dest.Today.route,
            modifier = Modifier.padding(inner),
        ) {
            composable(Dest.Today.route) {
                TodayScreen(
                    onStart = { startTracking() },
                    onStop = { TrackingForegroundService.stop(context) },
                    onOpenSpeedo = { nav.navigate(Dest.Speed.route) },
                )
            }
            composable(Dest.Trips.route) { TripsScreen() }
            composable(Dest.Speed.route) {
                SpeedoScreen(onClose = { nav.popBackStack() })
            }
            composable(Dest.More.route) { MoreScreen() }
        }
    }
}

private fun hasPermission(context: android.content.Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
