package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ConnectionMode
import com.example.data.DynamicLoadProgress
import com.example.data.GameCatalog
import com.example.data.MiniGameSpec
import com.example.data.PlayerConnState
import com.example.data.PlayerTier
import com.example.data.ResourcePackDescriptor
import com.example.data.RoomConnectionHealth
import com.example.data.local.CachedResourcePackEntity
import com.example.ui.theme.AvatarBadge
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CobaltBlueSoft
import com.example.ui.theme.DeepInk
import com.example.ui.theme.ElectricPink
import com.example.ui.theme.ElectricPinkSoft
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.FlameOrangeSoft
import com.example.ui.theme.LatencySignalPill
import com.example.ui.theme.LilacPop
import com.example.ui.theme.LilacPopSoft
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenSoft
import com.example.ui.theme.PopButton
import com.example.ui.theme.PopCard
import com.example.ui.theme.PopTag
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SunburstYellow
import com.example.ui.theme.SunburstYellowSoft
import com.example.ui.theme.WarmCream
import com.example.ui.theme.WarmCreamAlt
import com.example.viewmodel.PartyUiState

@Composable
fun LobbyScreen(
    state: PartyUiState,
    cachedPacks: List<CachedResourcePackEntity>,
    recommendedPacksForPending: List<ResourcePackDescriptor>,
    onAddBot: () -> Unit,
    onRemoveBot: () -> Unit,
    onSelectTier: (PlayerTier) -> Unit,
    onLaunchGame: (MiniGameSpec) -> Unit,
    onConfirmDynamicPack: (MiniGameSpec, String) -> Unit,
    onDismissPackPicker: () -> Unit,
    onTriggerWeakNetworkDrill: () -> Unit
) {
    val tierGames = GameCatalog.gamesForTier(state.selectedTier)
    val cachedPackIds = cachedPacks.map { it.packId }.toSet()

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
            // Room Status & Weak-Network Recovery Test Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "派对房间 ${state.roomCode}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = DeepInk
                        )
                        Text(
                            text = "低延迟同步 · 离线快照 Seq #${state.syncSeq}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CobaltBlue
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LatencySignalPill(
                            label = if (state.connectionHealth == RoomConnectionHealth.RECONNECTING) "重连中" else "稳定",
                            rttMs = state.averageRttMs,
                            accentColor = if (state.connectionHealth == RoomConnectionHealth.RECONNECTING) FlameOrange else MintGreen
                        )
                    }
                }
            }

            // Reconnecting / Restored Alert Banner
            if (state.connectionHealth == RoomConnectionHealth.RECONNECTING ||
                state.connectionHealth == RoomConnectionHealth.RESTORED
            ) {
                item {
                    val isReconnecting = state.connectionHealth == RoomConnectionHealth.RECONNECTING
                    PopCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (isReconnecting) FlameOrangeSoft else MintGreenSoft
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = state.statusBanner,
                                style = MaterialTheme.typography.titleSmall,
                                color = DeepInk
                            )
                            if (isReconnecting) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = FlameOrange,
                                    trackColor = PureWhite
                                )
                            }
                        }
                    }
                }
            }

            // Connected Players Strip + Quick Roster Adjuster
            item {
                PopCard(modifier = Modifier.fillMaxWidth(), containerColor = PureWhite) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "在线成员（${state.players.size} / 8人）",
                                style = MaterialTheme.typography.titleMedium,
                                color = DeepInk
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (state.connectionMode == ConnectionMode.LOCAL_SIMULATED) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(WarmCreamAlt)
                                            .border(1.5.dp, DeepInk, CircleShape)
                                            .clickable(onClick = onRemoveBot)
                                            .testTag("lobby_remove_bot"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Remove, contentDescription = "减少人数", modifier = Modifier.size(18.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(SunburstYellow)
                                            .border(1.5.dp, DeepInk, CircleShape)
                                            .clickable(onClick = onAddBot)
                                            .testTag("lobby_add_bot"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "增加人数", modifier = Modifier.size(18.dp))
                                    }
                                }
                                PopTag(
                                    text = "弱网演练",
                                    containerColor = FlameOrangeSoft,
                                    modifier = Modifier
                                        .clickable(onClick = onTriggerWeakNetworkDrill)
                                        .testTag("lobby_weak_net_drill")
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.players, key = { it.id }) { player ->
                                val isReconn = player.status == PlayerConnState.RECONNECTING.name
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(62.dp)
                                ) {
                                    AvatarBadge(
                                        emoji = player.avatarEmoji,
                                        colorIndex = player.colorIndex,
                                        size = 48.dp,
                                        isHost = player.isHost
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = player.name.take(4),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = DeepInk,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = when {
                                            player.isLocal -> "本机"
                                            isReconn -> "重连..."
                                            else -> "${player.rttMs}ms"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = if (isReconn) FlameOrange else MintGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Smart Player-Count Auto-Match Banner
            item {
                val rec = state.recommendedTier
                val bannerColor = tierSoftColor(rec)
                PopCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = bannerColor,
                    onClick = { onSelectTier(rec) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PopTag("智能人数匹配", tierAccentColor(rec), if (rec == PlayerTier.TRIO) DeepInk else PureWhite)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "当前 ${state.players.size} 人",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = DeepInk
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "已优先推荐「${rec.title}」：${rec.subtitle}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = DeepInk
                            )
                        }
                    }
                }
            }

            // 4 Dopamine Color-Block Tier Tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayerTier.entries.forEach { tier ->
                        val selected = state.selectedTier == tier
                        val accent = tierAccentColor(tier)
                        val textCol = if (selected) {
                            if (tier == PlayerTier.TRIO || tier == PlayerTier.PARTY_PLUS) DeepInk else PureWhite
                        } else DeepInk
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) accent else PureWhite)
                                .border(2.dp, DeepInk, RoundedCornerShape(14.dp))
                                .clickable { onSelectTier(tier) }
                                .padding(vertical = 10.dp)
                                .testTag("tier_tab_${tier.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = tier.badge,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = textCol
                                )
                                Text(
                                    text = tier.title.take(4),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = textCol.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }

            // Game Cards for Selected Tier
            items(tierGames, key = { it.id.name }) { game ->
                GameSpecCard(
                    spec = game,
                    currentPlayerCount = state.players.size,
                    onPlayClick = { onLaunchGame(game) }
                )
            }
        }
    }

    // 4P+ Dynamic Resource Pack Picker & On-Demand Loader Dialog
    state.pendingPackPickerGame?.let { pendingGame ->
        DynamicResourceLoaderDialog(
            game = pendingGame,
            playerCount = state.players.size.coerceAtLeast(5),
            packs = recommendedPacksForPending,
            cachedPackIds = cachedPackIds,
            loadProgress = state.dynamicLoadProgress,
            onDismiss = onDismissPackPicker,
            onSelectPack = { packId -> onConfirmDynamicPack(pendingGame, packId) }
        )
    }
}

@Composable
private fun GameSpecCard(
    spec: MiniGameSpec,
    currentPlayerCount: Int,
    onPlayClick: () -> Unit
) {
    val accent = tierAccentColor(spec.tier)
    val softBg = tierSoftColor(spec.tier)
    val isPerfectMatch = currentPlayerCount in spec.minPlayers..spec.maxPlayers

    PopCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("game_card_${spec.id.name}"),
        containerColor = PureWhite
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(softBg)
                        .border(2.dp, DeepInk, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(spec.emoji, fontSize = 30.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(spec.title, style = MaterialTheme.typography.titleLarge, color = DeepInk)
                        if (isPerfectMatch) {
                            PopTag("人数绝配", MintGreen)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PopTag(spec.mechanicsTag, softBg)
                        if (spec.requiresDynamicPack) {
                            PopTag("动态资源包", LilacPopSoft)
                        }
                    }
                }
            }

            Text(
                text = spec.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = DeepInk.copy(alpha = 0.85f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🤝 ${spec.interactionHint}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = CobaltBlue,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                PopButton(
                    text = if (spec.requiresDynamicPack) "选包开玩" else "立即开局",
                    onClick = onPlayClick,
                    containerColor = accent,
                    contentColor = if (spec.tier == PlayerTier.TRIO || spec.tier == PlayerTier.PARTY_PLUS) DeepInk else PureWhite,
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier.testTag("launch_game_${spec.id.name}")
                )
            }
        }
    }
}

@Composable
private fun DynamicResourceLoaderDialog(
    game: MiniGameSpec,
    playerCount: Int,
    packs: List<ResourcePackDescriptor>,
    cachedPackIds: Set<String>,
    loadProgress: DynamicLoadProgress?,
    onDismiss: () -> Unit,
    onSelectPack: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (loadProgress == null) onDismiss() },
        title = {
            Column {
                Text("4人+ 动态资源按需加载", style = MaterialTheme.typography.headlineMedium, color = DeepInk)
                Text(
                    "已根据当前 ${playerCount} 人局匹配「${game.title}」专属资源包",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CobaltBlue
                )
            }
        },
        text = {
            if (loadProgress != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PopTag(
                        text = if (loadProgress.fromOfflineCache) "⚡ 本地离线缓存加速" else "📦 动态分片装配中",
                        containerColor = if (loadProgress.fromOfflineCache) MintGreen else SunburstYellow
                    )
                    Text(loadProgress.packTitle, style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { loadProgress.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = ElectricPink,
                        trackColor = ElectricPinkSoft
                    )
                    Text(
                        text = "${(loadProgress.progress * 100).toInt()}% · ${loadProgress.stageLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = DeepInk
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    packs.forEach { pack ->
                        val isCached = pack.packId in cachedPackIds
                        val matchesSize = playerCount in pack.minPlayers..pack.maxPlayers
                        PopCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("select_pack_${pack.packId}"),
                            containerColor = if (matchesSize) MintGreenSoft else PureWhite,
                            onClick = { onSelectPack(pack.packId) },
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pack.emoji, fontSize = 28.sp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(pack.title, style = MaterialTheme.typography.titleSmall)
                                        if (matchesSize) PopTag("推荐", SunburstYellow)
                                    }
                                    Text(pack.subtitle, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCached) Icons.Filled.CloudDone else Icons.Filled.Download,
                                            contentDescription = null,
                                            tint = if (isCached) MintGreen else CobaltBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isCached) "已离线缓存 · 秒开 (${pack.estimatedKb}KB)" else "按需动态加载 (${pack.estimatedKb}KB)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCached) DeepInk else CobaltBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (loadProgress == null) {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

fun tierAccentColor(tier: PlayerTier): Color = when (tier) {
    PlayerTier.DUO -> ElectricPink
    PlayerTier.TRIO -> SunburstYellow
    PlayerTier.QUAD -> CobaltBlue
    PlayerTier.PARTY_PLUS -> MintGreen
}

fun tierSoftColor(tier: PlayerTier): Color = when (tier) {
    PlayerTier.DUO -> ElectricPinkSoft
    PlayerTier.TRIO -> SunburstYellowSoft
    PlayerTier.QUAD -> CobaltBlueSoft
    PlayerTier.PARTY_PLUS -> MintGreenSoft
}
