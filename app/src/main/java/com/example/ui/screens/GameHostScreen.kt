package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameId
import com.example.data.MatchResultSummary
import com.example.data.RoomConnectionHealth
import com.example.hardware.PartyHaptics
import com.example.hardware.TiltTelemetry
import com.example.ui.games.AirHockeyGame
import com.example.ui.games.HotPotatoBombGame
import com.example.ui.games.QuadColorClashGame
import com.example.ui.games.ReflexDuelGame
import com.example.ui.games.TeamHeadsUpRelayGame
import com.example.ui.games.TiltBalanceRelayGame
import com.example.ui.games.TriColorTurfGame
import com.example.ui.games.UndercoverSpyGame
import com.example.ui.theme.AvatarBadge
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.DeepInk
import com.example.ui.theme.ElectricPink
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.FlameOrangeSoft
import com.example.ui.theme.LatencySignalPill
import com.example.ui.theme.MintGreen
import com.example.ui.theme.PopButton
import com.example.ui.theme.PopCard
import com.example.ui.theme.PopTag
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SunburstYellow
import com.example.ui.theme.SunburstYellowSoft
import com.example.ui.theme.WarmCream
import com.example.viewmodel.PartyUiState
import kotlinx.coroutines.flow.SharedFlow

@Composable
fun GameHostScreen(
    state: PartyUiState,
    tilt: TiltTelemetry,
    shakeEvents: SharedFlow<Float>,
    haptics: PartyHaptics,
    onMatchFinished: (MatchResultSummary) -> Unit,
    onRematch: () -> Unit,
    onExitGame: () -> Unit,
    onTriggerWeakNetworkDrill: () -> Unit
) {
    val activeGame = state.activeGame ?: return
    var rematchNonce by remember { mutableIntStateOf(0) }
    val isReconnecting = state.connectionHealth == RoomConnectionHealth.RECONNECTING

    BackHandler {
        onExitGame()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WarmCream)
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Live Game Top Telemetry Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(PureWhite)
                            .border(2.dp, DeepInk, CircleShape)
                            .clickable(onClick = onExitGame)
                            .testTag("game_exit_back_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "退出对局",
                            tint = DeepInk
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${activeGame.emoji} ${activeGame.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = DeepInk
                        )
                        Text(
                            text = "${state.players.size}人同步 · ${activeGame.tier.title}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CobaltBlue
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LatencySignalPill(
                        label = if (isReconnecting) "重连" else "同步",
                        rttMs = state.averageRttMs,
                        accentColor = if (isReconnecting) FlameOrange else MintGreen
                    )
                    PopTag(
                        text = "弱网测试",
                        containerColor = FlameOrangeSoft,
                        modifier = Modifier
                            .clickable(onClick = onTriggerWeakNetworkDrill)
                            .testTag("game_weak_net_test")
                    )
                }
            }

            // Active Mini-Game Surface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                androidx.compose.runtime.key(activeGame.id, rematchNonce) {
                    when (activeGame.id) {
                        GameId.AIR_HOCKEY -> AirHockeyGame(
                            players = state.players,
                            haptics = haptics,
                            isPaused = isReconnecting,
                            onMatchFinished = onMatchFinished
                        )

                        GameId.REFLEX_DUEL -> ReflexDuelGame(
                            players = state.players,
                            tilt = tilt,
                            haptics = haptics,
                            isPaused = isReconnecting,
                            onMatchFinished = onMatchFinished
                        )

                        GameId.HOT_POTATO -> HotPotatoBombGame(
                            players = state.players,
                            shakeEvents = shakeEvents,
                            haptics = haptics,
                            isPaused = isReconnecting,
                            onMatchFinished = onMatchFinished
                        )

                        GameId.TURF_SPRINT -> TriColorTurfGame(
                            players = state.players,
                            haptics = haptics,
                            isPaused = isReconnecting,
                            onMatchFinished = onMatchFinished
                        )

                        GameId.TILT_BALANCE -> TiltBalanceRelayGame(
                            players = state.players,
                            tilt = tilt,
                            haptics = haptics,
                            isPaused = isReconnecting,
                            onMatchFinished = onMatchFinished
                        )

                        GameId.QUAD_CLASH -> QuadColorClashGame(
                            players = state.players,
                            haptics = haptics,
                            isPaused = isReconnecting,
                            onMatchFinished = onMatchFinished
                        )

                        GameId.UNDERCOVER_SPY -> UndercoverSpyGame(
                            players = state.players,
                            dynamicPack = state.activeDynamicPack,
                            tilt = tilt,
                            haptics = haptics,
                            onMatchFinished = onMatchFinished
                        )

                        GameId.TEAM_HEADS_UP -> TeamHeadsUpRelayGame(
                            players = state.players,
                            dynamicPack = state.activeDynamicPack,
                            tilt = tilt,
                            haptics = haptics,
                            isPaused = isReconnecting,
                            onMatchFinished = onMatchFinished
                        )
                    }
                }
            }
        }

        // Non-destructive Offline Snapshot Reconnect Overlay
        if (isReconnecting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepInk.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                PopCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    containerColor = SunburstYellowSoft
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("📡 信号波动 · 快照守护中", style = MaterialTheme.typography.headlineMedium, color = DeepInk)
                        Text(
                            "本地 Room 已缓存当前帧（Seq #${state.syncSeq}），正在自动重连并恢复同步...",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = FlameOrange,
                            trackColor = PureWhite
                        )
                    }
                }
            }
        }

        // Dopamine Victory / MVP Overlay
        state.lastMatchResult?.let { result ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepInk.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                PopCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    containerColor = PureWhite
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(SunburstYellow)
                                .border(3.dp, DeepInk, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(result.mvpEmoji, fontSize = 38.sp)
                        }
                        PopTag(
                            text = if (result.localWon) "🎉 聚会高光 · 本局获胜！" else "🔥 战况焦灼 · 精彩对决！",
                            containerColor = if (result.localWon) MintGreen else SunburstYellow
                        )
                        Text(
                            text = "本场 MVP：${result.mvpName}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = DeepInk,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = result.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CobaltBlue,
                            textAlign = TextAlign.Center
                        )

                        // Top 4 Rankings
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(WarmCream)
                                .border(1.5.dp, DeepInk, RoundedCornerShape(14.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            result.rankings.take(4).forEachIndexed { idx, p ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "#${idx + 1}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ElectricPink
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        AvatarBadge(p.avatarEmoji, p.colorIndex, size = 28.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(p.name, style = MaterialTheme.typography.titleSmall)
                                    }
                                    Text(
                                        "${p.score} 分",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DeepInk
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PopButton(
                                text = "返回大厅",
                                onClick = onExitGame,
                                containerColor = CobaltBlue,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("result_back_lobby")
                            )
                            PopButton(
                                text = "再来一局",
                                onClick = {
                                    rematchNonce++
                                    onRematch()
                                },
                                containerColor = ElectricPink,
                                icon = Icons.Filled.Refresh,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("result_rematch")
                            )
                        }
                    }
                }
            }
        }
    }
}
