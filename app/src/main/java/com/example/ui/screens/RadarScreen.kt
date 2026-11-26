package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ConnectionMode
import com.example.data.RoomConnectionHealth
import com.example.ui.theme.AvatarBadge
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CobaltBlueSoft
import com.example.ui.theme.DeepInk
import com.example.ui.theme.ElectricPink
import com.example.ui.theme.ElectricPinkSoft
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.LatencySignalPill
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenSoft
import com.example.ui.theme.PlayerPalette
import com.example.ui.theme.PopButton
import com.example.ui.theme.PopCard
import com.example.ui.theme.PopTag
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SunburstYellow
import com.example.ui.theme.SunburstYellowSoft
import com.example.ui.theme.WarmCream
import com.example.viewmodel.PartyUiState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RadarScreen(
    state: PartyUiState,
    onSwitchMode: (ConnectionMode) -> Unit,
    onStartNearby: () -> Unit,
    onStopNearby: () -> Unit,
    onTriggerBump: () -> Unit,
    onSetPlayerCount: (Int) -> Unit,
    onUpdateProfile: (String, String, Int) -> Unit,
    onOpenLobby: () -> Unit
) {
    var showProfileDialog by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            onStartNearby()
        } else {
            // Gracefully start or guide user while allowing instant fallback
            onStartNearby()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmCream),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Brand Bar & Editable Local Profile
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "碰碰派对",
                            style = MaterialTheme.typography.displaySmall,
                            color = DeepInk
                        )
                        Text(
                            text = "靠近自动开房 · 零流量聚会神器",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DeepInk.copy(alpha = 0.72f)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(PureWhite)
                            .border(2.dp, DeepInk, RoundedCornerShape(999.dp))
                            .clickable { showProfileDialog = true }
                            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
                            .testTag("profile_edit_chip")
                    ) {
                        AvatarBadge(state.profile.avatarEmoji, state.profile.colorIndex, size = 32.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(state.profile.nickname, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "编辑昵称",
                            tint = CobaltBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Dopamine Editorial Hero Illustration Banner
            item {
                PopCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = PureWhite,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(142.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_party_hero_1795712420867),
                            contentDescription = "碰碰派对线下聚会主视觉",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            LatencySignalPill(
                                label = if (state.connectionMode == ConnectionMode.NEARBY_P2P) "Nearby 直连" else "AI 模拟派对",
                                rttMs = state.averageRttMs,
                                accentColor = when (state.connectionHealth) {
                                    RoomConnectionHealth.CONNECTED, RoomConnectionHealth.RESTORED -> MintGreen
                                    RoomConnectionHealth.SCANNING -> SunburstYellow
                                    RoomConnectionHealth.RECONNECTING -> FlameOrange
                                    else -> CobaltBlue
                                }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                        ) {
                            PopTag(
                                text = "房间 ${state.roomCode} · ${state.players.size}人在房",
                                containerColor = SunburstYellow
                            )
                        }
                    }
                }
            }

            // Connection Mode Switcher (Real Nearby vs Local AI Simulation)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isNearby = state.connectionMode == ConnectionMode.NEARBY_P2P
                    PopCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_nearby_card"),
                        containerColor = if (isNearby) CobaltBlue else PureWhite,
                        onClick = { onSwitchMode(ConnectionMode.NEARBY_P2P) },
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.WifiTethering,
                                contentDescription = null,
                                tint = if (isNearby) PureWhite else CobaltBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Nearby 真机直连",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isNearby) PureWhite else DeepInk
                                )
                                Text(
                                    "蓝牙+Wi-Fi零流量",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isNearby) PureWhite.copy(alpha = 0.85f) else DeepInk.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }

                    PopCard(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("mode_sim_card"),
                        containerColor = if (!isNearby) ElectricPink else PureWhite,
                        onClick = { onSwitchMode(ConnectionMode.LOCAL_SIMULATED) },
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Groups,
                                contentDescription = null,
                                tint = if (!isNearby) PureWhite else ElectricPink,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    "AI 模拟联机",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (!isNearby) PureWhite else DeepInk
                                )
                                Text(
                                    "单机畅玩 2-8 人",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (!isNearby) PureWhite.copy(alpha = 0.85f) else DeepInk.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }

            // Animated Proximity Bump Radar Card
            item {
                PopCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = PureWhite
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProximityRadarVisualizer(
                            players = state.players,
                            isScanning = state.connectionHealth == RoomConnectionHealth.SCANNING ||
                                state.connectionHealth == RoomConnectionHealth.CONNECTED,
                            onCenterBumpClick = {
                                if (state.connectionMode == ConnectionMode.NEARBY_P2P &&
                                    state.connectionHealth == RoomConnectionHealth.IDLE
                                ) {
                                    permissionLauncher.launch(requiredPermissions)
                                } else {
                                    onTriggerBump()
                                }
                            }
                        )

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = state.statusBanner,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = DeepInk,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))

                        if (state.connectionMode == ConnectionMode.NEARBY_P2P) {
                            val scanning = state.connectionHealth == RoomConnectionHealth.SCANNING
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PopButton(
                                    text = if (scanning) "停止扫描" else "开启靠近雷达",
                                    onClick = {
                                        if (scanning) onStopNearby()
                                        else permissionLauncher.launch(requiredPermissions)
                                    },
                                    containerColor = if (scanning) SunburstYellow else CobaltBlue,
                                    contentColor = if (scanning) DeepInk else PureWhite,
                                    icon = Icons.Filled.Radar,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("nearby_toggle_button")
                                )
                                PopButton(
                                    text = "进入大厅",
                                    onClick = onOpenLobby,
                                    containerColor = ElectricPink,
                                    icon = Icons.Filled.SportsEsports,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("radar_enter_lobby_btn")
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PopButton(
                                    text = "碰一碰 +1好友",
                                    onClick = onTriggerBump,
                                    containerColor = SunburstYellow,
                                    contentColor = DeepInk,
                                    icon = Icons.Filled.Bolt,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("bump_add_friend_button")
                                )
                                PopButton(
                                    text = "进入派对大厅",
                                    onClick = onOpenLobby,
                                    containerColor = ElectricPink,
                                    icon = Icons.Filled.SportsEsports,
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .testTag("radar_enter_lobby_btn")
                                )
                            }
                        }
                    }
                }
            }

            // Quick Party Size Presets (instantly test 2P / 3P / 4P / 6P / 8P matching!)
            item {
                PopCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MintGreenSoft
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "一键体验不同人数匹配档位",
                                style = MaterialTheme.typography.titleSmall,
                                color = DeepInk
                            )
                            PopTag("摇手机也可碰一碰", PureWhite)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                2 to "2人精操",
                                3 to "3人混战",
                                4 to "4人协作",
                                6 to "6人团战",
                                8 to "8人狂欢"
                            ).forEach { (count, label) ->
                                val selected = state.players.size == count &&
                                    state.connectionMode == ConnectionMode.LOCAL_SIMULATED
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) DeepInk else PureWhite)
                                        .border(1.5.dp, DeepInk, RoundedCornerShape(12.dp))
                                        .clickable { onSetPlayerCount(count) }
                                        .padding(vertical = 10.dp)
                                        .testTag("preset_players_$count"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (selected) SunburstYellow else DeepInk,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProfileDialog) {
        ProfileEditDialog(
            currentName = state.profile.nickname,
            currentEmoji = state.profile.avatarEmoji,
            currentColorIdx = state.profile.colorIndex,
            onDismiss = { showProfileDialog = false },
            onSave = { n, e, c ->
                onUpdateProfile(n, e, c)
                showProfileDialog = false
            }
        )
    }
}

@Composable
private fun ProximityRadarVisualizer(
    players: List<com.example.data.PartyPlayer>,
    isScanning: Boolean,
    onCenterBumpClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_wave")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    Box(
        modifier = Modifier
            .size(230.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension / 2f - 8f

            // Static concentric rings
            listOf(0.45f, 0.72f, 0.96f).forEachIndexed { idx, ratio ->
                drawCircle(
                    color = when (idx) {
                        0 -> ElectricPinkSoft
                        1 -> CobaltBlueSoft
                        else -> MintGreenSoft
                    }.copy(alpha = 0.65f),
                    radius = maxR * ratio,
                    center = center
                )
                drawCircle(
                    color = DeepInk.copy(alpha = 0.25f),
                    radius = maxR * ratio,
                    center = center,
                    style = Stroke(width = 2.5f)
                )
            }

            if (isScanning) {
                listOf(wave1, wave2 % 1f).forEach { progress ->
                    val r = maxR * (0.28f + 0.72f * progress)
                    val alpha = (1f - progress).coerceIn(0f, 1f) * 0.7f
                    drawCircle(
                        color = ElectricPink.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = 6f)
                    )
                }
            }
        }

        // Orbiting connected players around the radar
        val remotes = players.drop(1)
        remotes.forEachIndexed { idx, player ->
            val angleRad = (idx.toFloat() / remotes.size.coerceAtLeast(1) * 2.0 * Math.PI) - Math.PI / 2.0
            val orbitDp = 82f
            val dx = (cos(angleRad) * orbitDp).dp
            val dy = (sin(angleRad) * orbitDp).dp
            Box(modifier = Modifier.offset(x = dx, y = dy)) {
                AvatarBadge(player.avatarEmoji, player.colorIndex, size = 38.dp)
            }
        }

        // Center interactive Bump button
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(CircleShape)
                .background(ElectricPink)
                .border(3.dp, DeepInk, CircleShape)
                .clickable(onClick = onCenterBumpClick)
                .testTag("radar_center_bump"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚡", fontSize = 26.sp)
                Text(
                    text = "碰一碰",
                    style = MaterialTheme.typography.labelLarge,
                    color = PureWhite
                )
            }
        }
    }
}

@Composable
private fun ProfileEditDialog(
    currentName: String,
    currentEmoji: String,
    currentColorIdx: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var emoji by remember { mutableStateOf(currentEmoji) }
    var colorIdx by remember { mutableIntStateOf(currentColorIdx) }
    val emojis = listOf("🦊", "🐼", "🦄", "🐯", "🐸", "🐙", "🤖", "⚡")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义派对名片", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(12) },
                    label = { Text("你的聚会昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("选择形象头像", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojis.forEach { item ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (emoji == item) SunburstYellow else WarmCream)
                                .border(
                                    width = if (emoji == item) 2.dp else 1.dp,
                                    color = DeepInk,
                                    shape = CircleShape
                                )
                                .clickable { emoji = item },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(item, fontSize = 18.sp)
                        }
                    }
                }
                Text("选择多巴胺底色", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PlayerPalette.forEachIndexed { idx, col ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (colorIdx == idx) 3.dp else 1.dp,
                                    color = DeepInk,
                                    shape = CircleShape
                                )
                                .clickable { colorIdx = idx }
                        )
                    }
                }
            }
        },
        confirmButton = {
            PopButton(
                text = "保存",
                onClick = { onSave(name, emoji, colorIdx) },
                containerColor = MintGreen,
                contentColor = DeepInk
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
