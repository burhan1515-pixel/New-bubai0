@file:OptIn(ExperimentalMaterial3Api::class)

package com.newdubai.shop.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.newdubai.shop.util.tr
import kotlinx.coroutines.launch

private data class NavItem(val route: String, val icon: ImageVector)

private val ITEMS = listOf(
    NavItem("dash", Icons.Default.Dashboard),
    NavItem("cust", Icons.Default.People),
    NavItem("orders", Icons.Default.ContentCut),
    NavItem("meas", Icons.Default.Straighten),
    NavItem("fabric", Icons.Default.Store),
    NavItem("cash", Icons.Default.AccountBalanceWallet),
    NavItem("debt", Icons.Default.CreditCard),
    NavItem("exp", Icons.Default.Receipt),
    NavItem("rep", Icons.Default.Assessment),
    NavItem("bak", Icons.Default.CloudUpload),
    NavItem("set", Icons.Default.Settings)
)

private val PRIMARY_TABS = listOf(
    NavItem("dash", Icons.Default.Home),
    NavItem("cust", Icons.Default.People),
    NavItem("orders", Icons.Default.ContentCut),
    NavItem("fabric", Icons.Default.Store),
    NavItem("rep", Icons.Default.Assessment)
)

@Composable
fun MainShell(vm: AppVM) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dash"

    fun go(r: String) {
        scope.launch { drawer.close() }
        nav.navigate(r) {
            popUpTo("dash") { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "NEW DUBAI",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StitchThread(Modifier.width(180.dp).height(22.dp))
                    Text(
                        "خليل الرحمن ظاهري",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Tailoring & Fabric Studio",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                ITEMS.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(tr(item.route)) },
                        icon = { Icon(item.icon, null) },
                        selected = route == item.route ||
                            (route.startsWith("customer") && item.route == "cust"),
                        onClick = { go(item.route) },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                tr(if (route.startsWith("customer")) "cust" else route),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "NEW DUBAI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.locked = true }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock app")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    PRIMARY_TABS.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route ||
                                (route.startsWith("customer") && item.route == "cust"),
                            onClick = { go(item.route) },
                            icon = { Icon(item.icon, contentDescription = tr(item.route)) },
                            label = { Text(tr(item.route), maxLines = 1) },
                            alwaysShowLabel = false
                        )
                    }
                }
            }
        ) { pad ->
            NavHost(
                navController = nav,
                startDestination = "dash",
                modifier = Modifier.padding(pad),
                enterTransition = {
                    fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 16 }
                },
                exitTransition = { fadeOut(tween(150)) },
                popEnterTransition = { fadeIn(tween(240)) },
                popExitTransition = { fadeOut(tween(140)) }
            ) {
                composable("dash") { DashScreen(vm) }
                composable("cust") { CustomersScreen(vm) { nav.navigate("customer/$it") } }
                composable(
                    "customer/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) {
                    CustomerProfile(vm, it.arguments?.getLong("id") ?: 0L) {
                        nav.popBackStack()
                    }
                }
                composable("orders") { OrdersScreen(vm) }
                composable("meas") { MeasurementsScreen(vm) }
                composable("fabric") { FabricScreen(vm) }
                composable("cash") { CashScreen(vm) }
                composable("debt") { DebtScreen(vm) { nav.navigate("customer/$it") } }
                composable("exp") { ExpensesScreen(vm) }
                composable("rep") { ReportsScreen(vm) }
                composable("bak") { BackupScreen(vm) }
                composable("set") { SettingsScreen(vm) }
            }
        }
    }
}
