package com.soumik.stark.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.soumik.stark.core.security.SessionLock
import com.soumik.stark.core.util.Prefs
import com.soumik.stark.tracking.service.TrackingForegroundService
import com.soumik.stark.ui.lock.LockScreen
import com.soumik.stark.ui.map.MapScreen
import com.soumik.stark.ui.more.MoreScreen
import com.soumik.stark.ui.onboarding.OnboardingScreen
import com.soumik.stark.ui.speedo.SpeedoScreen
import com.soumik.stark.ui.stats.StatsScreen
import com.soumik.stark.ui.theme.StarkTheme
import com.soumik.stark.ui.theme.ThemeState
import com.soumik.stark.ui.timeline.TimelineScreen
import com.soumik.stark.ui.today.TodayScreen
import com.soumik.stark.ui.trip_detail.TripDetailScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        SessionLock.initFor(this)
        ThemeState.init(this)
        setContent {
            val night by ThemeState.nightRide.collectAsStateWithLifecycle()
            val dynamic by ThemeState.dynamicColor.collectAsStateWithLifecycle()
            StarkTheme(nightRide = night, dynamicColor = dynamic) { Root() }
        }
    }

    override fun onStart() {
        super.onStart()
        SessionLock.onForeground(this)
    }

    override fun onStop() {
        super.onStop()
        SessionLock.onBackground()
    }
}

private sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Today : Dest("today", "Today", Icons.Filled.Today)
    data object Timeline : Dest("timeline", "Timeline", Icons.AutoMirrored.Filled.List)
    data object Map : Dest("map", "Map", Icons.Filled.Map)
    data object Stats : Dest("stats", "Stats", Icons.Filled.BarChart)
    data object More : Dest("more", "More", Icons.Filled.MoreHoriz)
}

private val bottomDests = listOf(Dest.Today, Dest.Timeline, Dest.Map, Dest.Stats, Dest.More)

@Composable
private fun Root() {
    val context = LocalContext.current
    val locked by SessionLock.locked.collectAsStateWithLifecycle()
    var onboarded by remember { mutableStateOf(Prefs.getBool(context, Prefs.KEY_ONBOARDED)) }

    val scope = rememberCoroutineScope()
    when {
        !onboarded -> OnboardingScreen(onDone = {
            onboarded = true
            SessionLock.initFor(context)
        })
        locked -> LockScreen(onUnlocked = { decoy ->
            if (decoy) {
                scope.launch {
                    withContext(kotlinx.coroutines.Dispatchers.IO) { com.soumik.stark.core.security.Decoy.enter(context) }
                    SessionLock.unlock(true)
                }
            } else {
                SessionLock.unlock(false)
            }
        })
        else -> MainShell()
    }
}

@Composable
private fun MainShell() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val topLevel = bottomDests.any { it.route == currentRoute }
    val fullScreen = !topLevel
    val context = LocalContext.current

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            TrackingForegroundService.start(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    fun startTracking() {
        if (hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            TrackingForegroundService.start(context)
        } else {
            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) perms += Manifest.permission.ACTIVITY_RECOGNITION
            permLauncher.launch(perms.toTypedArray())
        }
    }

    Scaffold(
        bottomBar = {
            if (!fullScreen) {
                NavigationBar {
                    bottomDests.forEach { dest ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == dest.route } == true
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
        NavHost(nav, startDestination = Dest.Today.route, modifier = Modifier.padding(inner)) {
            composable(Dest.Today.route) {
                TodayScreen(
                    onStart = { startTracking() },
                    onStop = { TrackingForegroundService.stop(context) },
                    onOpenSpeedo = { nav.navigate("speedo") },
                    onOpenTrip = { id -> nav.navigate("trip/$id") },
                )
            }
            composable(Dest.Timeline.route) {
                TimelineScreen(onOpenTrip = { id -> nav.navigate("trip/$id") })
            }
            composable(Dest.Map.route) { MapScreen() }
            composable(Dest.Stats.route) { StatsScreen() }
            composable(Dest.More.route) {
                MoreScreen(
                    onOpenFuel = { nav.navigate("fuel") },
                    onOpenBackup = { nav.navigate("backup") },
                    onOpenPlaces = { nav.navigate("places") },
                    onOpenAutomation = { nav.navigate("automation") },
                )
            }
            composable("fuel") { com.soumik.stark.ui.fuel.FuelScreen(onBack = { nav.popBackStack() }) }
            composable("places") { com.soumik.stark.ui.places.PlacesScreen(onBack = { nav.popBackStack() }) }
            composable("backup") { com.soumik.stark.ui.backup.BackupScreen(onBack = { nav.popBackStack() }) }
            composable("automation") { com.soumik.stark.ui.automation.AutomationScreen(onBack = { nav.popBackStack() }) }
            composable("speedo") { SpeedoScreen(onClose = { nav.popBackStack() }) }
            composable(
                "trip/{legId}",
                arguments = listOf(navArgument("legId") { type = NavType.LongType }),
            ) { entry ->
                TripDetailScreen(legId = entry.arguments!!.getLong("legId"), onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun hasPermission(context: android.content.Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
