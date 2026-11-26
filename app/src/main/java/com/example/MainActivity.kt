package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.GameHostScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.RadarScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.BumpPartyTheme
import com.example.ui.theme.DeepInk
import com.example.ui.theme.ElectricPink
import com.example.ui.theme.ElectricPinkSoft
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SunburstYellow
import com.example.ui.theme.WarmCream
import com.example.viewmodel.PartyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BumpPartyTheme {
                BumpPartyApp()
            }
        }
    }
}

@Composable
fun BumpPartyApp(
    viewModel: PartyViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tilt by viewModel.tiltTelemetry.collectAsStateWithLifecycle()
    val cachedPacks by viewModel.cachedPacks.collectAsStateWithLifecycle()
    val latestSnapshot by viewModel.latestSnapshot.collectAsStateWithLifecycle()
    val matchHistory by viewModel.matchHistory.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Lifecycle-aware hardware accelerometer start/stop
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startSensors()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopSensors()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopSensors()
        }
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeToast()
        }
    }

    // Full-screen Active Mini-Game Arena
    if (state.activeGame != null) {
        GameHostScreen(
            state = state,
            tilt = tilt,
            shakeEvents = viewModel.sensorHub.shakeEvents,
            haptics = viewModel.haptics,
            onMatchFinished = { summary -> viewModel.finishActiveGame(summary) },
            onRematch = { viewModel.rematchActiveGame() },
            onExitGame = { viewModel.exitActiveGame() },
            onTriggerWeakNetworkDrill = { viewModel.triggerWeakNetworkRecoveryDrill() }
        )
        return
    }

    val navItems = listOf(
        Triple("碰碰雷达", Icons.Filled.Radar, Icons.Outlined.Radar),
        Triple("派对大厅", Icons.Filled.SportsEsports, Icons.Outlined.SportsEsports),
        Triple("战绩缓存", Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents)
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmCream),
        containerColor = WarmCream,
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    modifier = Modifier
                        .padding(12.dp)
                        .border(2.dp, DeepInk, RoundedCornerShape(14.dp)),
                    containerColor = DeepInk,
                    contentColor = SunburstYellow,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(data.visuals.message, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = PureWhite,
                windowInsets = WindowInsets.navigationBars,
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = DeepInk,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            ) {
                navItems.forEachIndexed { index, (label, filledIcon, outlinedIcon) ->
                    val selected = selectedTab == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            viewModel.haptics.lightTap()
                            selectedTab = index
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) filledIcon else outlinedIcon,
                                contentDescription = label
                            )
                        },
                        label = {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricPink,
                            selectedTextColor = DeepInk,
                            indicatorColor = ElectricPinkSoft,
                            unselectedIconColor = DeepInk.copy(alpha = 0.65f),
                            unselectedTextColor = DeepInk.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier.testTag("nav_tab_$index")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> RadarScreen(
                    state = state,
                    onSwitchMode = { viewModel.switchConnectionMode(it) },
                    onStartNearby = { viewModel.startNearbyRadar() },
                    onStopNearby = { viewModel.stopNearbyRadar() },
                    onTriggerBump = { viewModel.triggerBumpProximityMatch() },
                    onSetPlayerCount = { count ->
                        viewModel.setSimulatedPlayerCount(count)
                    },
                    onUpdateProfile = { n, e, c -> viewModel.updateProfile(n, e, c) },
                    onOpenLobby = { selectedTab = 1 }
                )

                1 -> LobbyScreen(
                    state = state,
                    cachedPacks = cachedPacks,
                    recommendedPacksForPending = state.pendingPackPickerGame?.let {
                        viewModel.getRecommendedPacksFor(it.id)
                    }.orEmpty(),
                    onAddBot = { viewModel.addSimulatedFriend() },
                    onRemoveBot = { viewModel.removeLastSimulatedFriend() },
                    onSelectTier = { viewModel.selectTierTab(it) },
                    onLaunchGame = { viewModel.requestLaunchGame(it) },
                    onConfirmDynamicPack = { game, packId ->
                        viewModel.loadDynamicPackAndStart(game, packId)
                    },
                    onDismissPackPicker = { viewModel.dismissPackPicker() },
                    onTriggerWeakNetworkDrill = { viewModel.triggerWeakNetworkRecoveryDrill() }
                )

                else -> VaultScreen(
                    latestSnapshot = latestSnapshot,
                    allPackDescriptors = viewModel.repository.resourcePackManager.availablePacks,
                    cachedPacks = cachedPacks,
                    matchHistory = matchHistory,
                    onRestoreSnapshot = {
                        viewModel.restoreFromSavedSnapshot()
                        selectedTab = 1
                    },
                    onPreloadPack = { viewModel.preloadPackOffline(it) },
                    onDeletePack = { viewModel.deleteCachedPack(it) },
                    onClearHistory = { viewModel.clearAllMatchHistory() }
                )
            }
        }
    }
}
