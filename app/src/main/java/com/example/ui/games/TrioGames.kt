package com.example.ui.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameId
import com.example.data.MatchResultSummary
import com.example.data.PartyPlayer
import com.example.hardware.PartyHaptics
import com.example.ui.theme.AvatarBadge
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CobaltBlueSoft
import com.example.ui.theme.DeepInk
import com.example.ui.theme.ElectricPink
import com.example.ui.theme.ElectricPinkSoft
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.FlameOrangeSoft
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenSoft
import com.example.ui.theme.PopButton
import com.example.ui.theme.PopCard
import com.example.ui.theme.PopTag
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SunburstYellow
import com.example.ui.theme.SunburstYellowSoft
import com.example.ui.theme.WarmCreamAlt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.random.Random

/**
 * 3P Offline Physical Interaction Game #1: 热土豆传炸弹 (Hot Potato Bomb Pass)
 * Shake phone OR flick left/right to pass the ticking bomb around the seated 3-player circle!
 */
@Composable
fun HotPotatoBombGame(
    players: List<PartyPlayer>,
    shakeEvents: SharedFlow<Float>,
    haptics: PartyHaptics,
    isPaused: Boolean,
    onMatchFinished: (MatchResultSummary) -> Unit
) {
    val trio = remember(players) {
        val base = players.take(3).toMutableList()
        while (base.size < 3) {
            base.add(PartyPlayer("bot_trio_${base.size}", "好友${base.size + 1}", "🍋", base.size, isBot = true))
        }
        base.toList()
    }
    // Index 0 = local (bottom), Index 1 = Left neighbor, Index 2 = Right neighbor
    val scores = remember { mutableStateListOf(30, 30, 30) }
    var round by remember { mutableIntStateOf(1) }
    val totalRounds = 3
    var holderIdx by remember { mutableIntStateOf(0) }
    var fuseRemainingMs by remember { mutableIntStateOf(8000) }
    var fuseInitialMs by remember { mutableIntStateOf(8000) }
    var passCount by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("💣 炸弹在你手上！摇晃手机或点左右甩给邻座！") }
    var explodedThisRound by remember { mutableStateOf(false) }

    fun passBomb(targetIndex: Int) {
        if (explodedThisRound || holderIdx != 0) return
        holderIdx = targetIndex
        passCount++
        haptics.bumpMatch()
        statusText = "🚀 已甩给 ${trio[targetIndex].name}！看看TA往哪扔..."
    }

    // Listen for real physical shake gesture to throw bomb!
    LaunchedEffect(Unit) {
        shakeEvents.collectLatest {
            if (holderIdx == 0 && !explodedThisRound) {
                val target = if (Random.nextBoolean()) 1 else 2
                passBomb(target)
            }
        }
    }

    // Fuse tick + AI pass behavior
    LaunchedEffect(round, isPaused) {
        if (isPaused || round > totalRounds) return@LaunchedEffect
        explodedThisRound = false
        val total = Random.nextInt(7000, 11500)
        fuseInitialMs = total
        fuseRemainingMs = total
        holderIdx = Random.nextInt(0, 3)
        statusText = if (holderIdx == 0) {
            "💣 第 $round 轮开局炸弹就在你手上！快甩出去！"
        } else {
            "👀 ${trio[holderIdx].name} 接到了定时炸弹..."
        }

        var botThinkTimer = 0
        var heartbeatAccum = 0

        while (fuseRemainingMs > 0 && !isPaused) {
            val step = 100
            delay(step.toLong())
            fuseRemainingMs = (fuseRemainingMs - step).coerceAtLeast(0)

            val urgency = 1f - (fuseRemainingMs.toFloat() / fuseInitialMs.toFloat())
            val beatInterval = (700 - (urgency * 500)).toInt().coerceAtLeast(180)
            heartbeatAccum += step
            if (heartbeatAccum >= beatInterval) {
                heartbeatAccum = 0
                if (holderIdx == 0) haptics.bombHeartbeat(urgency)
            }

            // Bot passes bomb after short reaction
            if (holderIdx != 0) {
                botThinkTimer += step
                if (botThinkTimer >= Random.nextInt(550, 1250)) {
                    botThinkTimer = 0
                    val nextTarget = when (holderIdx) {
                        1 -> if (Random.nextFloat() < 0.65f) 0 else 2
                        else -> if (Random.nextFloat() < 0.65f) 0 else 1
                    }
                    holderIdx = nextTarget
                    passCount++
                    if (nextTarget == 0) {
                        haptics.paddleHit()
                        statusText = "😱 炸弹甩回给你了！快摇手机或左右甩锅！"
                    } else {
                        statusText = "💨 炸弹飞向了 ${trio[nextTarget].name}！"
                    }
                }
            }
        }

        if (fuseRemainingMs <= 0) {
            explodedThisRound = true
            haptics.bombExplode()
            scores[holderIdx] = (scores[holderIdx] - 15).coerceAtLeast(0)
            statusText = "💥 BOOM！炸弹在 ${trio[holderIdx].avatarEmoji} ${trio[holderIdx].name} 手上引爆！"
            delay(1700L)
            round++
        }
    }

    LaunchedEffect(round) {
        if (round > totalRounds) {
            val rankings = trio.mapIndexed { idx, p -> p.copy(score = scores[idx] + passCount * 2) }
                .sortedByDescending { it.score }
            val mvp = rankings.first()
            onMatchFinished(
                MatchResultSummary(
                    gameId = GameId.HOT_POTATO,
                    gameTitle = "热土豆传炸弹",
                    playerCount = 3,
                    mvpName = mvp.name,
                    mvpEmoji = mvp.avatarEmoji,
                    mvpScore = mvp.score,
                    localWon = mvp.id == trio[0].id,
                    rankings = rankings,
                    note = "共完成 $passCount 次惊险传弹！"
                )
            )
        }
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (holderIdx == 0 && !explodedThisRound) 1.08f else 0.96f,
        label = "bomb_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top neighbors (Left & Right seated friends)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            listOf(1 to "左侧好友", 2 to "右侧好友").forEach { (idx, posLabel) ->
                val hasBomb = holderIdx == idx
                PopCard(
                    modifier = Modifier.weight(1f),
                    containerColor = if (hasBomb) FlameOrangeSoft else PureWhite,
                    borderColor = if (hasBomb) FlameOrange else DeepInk
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AvatarBadge(trio[idx].avatarEmoji, trio[idx].colorIndex, size = 44.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(trio[idx].name, style = MaterialTheme.typography.titleSmall)
                        Text("$posLabel · ${scores[idx]}HP", style = MaterialTheme.typography.labelSmall, color = CobaltBlue)
                        if (hasBomb) {
                            Spacer(Modifier.height(4.dp))
                            PopTag("💣 持有中", FlameOrange, PureWhite)
                        }
                    }
                }
            }
        }

        // Center Bomb Stage
        PopCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 14.dp),
            containerColor = if (explodedThisRound) FlameOrange else if (holderIdx == 0) SunburstYellowSoft else MintGreenSoft
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PopTag("第 ${round.coerceAtMost(totalRounds)} / $totalRounds 轮 · 传弹 $passCount 次", PureWhite)
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .size(142.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(if (explodedThisRound) SunburstYellow else PureWhite)
                        .border(4.dp, DeepInk, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (explodedThisRound) "💥" else "💣",
                        fontSize = 68.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (explodedThisRound) PureWhite else DeepInk,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "📱 支持物理体感：直接猛摇手机也能瞬间甩锅！",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (explodedThisRound) PureWhite else DeepInk.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Directional Pass Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PopButton(
                text = "甩给 ${trio[1].name}",
                onClick = { passBomb(1) },
                enabled = holderIdx == 0 && !explodedThisRound,
                containerColor = CobaltBlue,
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                modifier = Modifier
                    .weight(1f)
                    .testTag("bomb_pass_left")
            )
            PopButton(
                text = "甩给 ${trio[2].name}",
                onClick = { passBomb(2) },
                enabled = holderIdx == 0 && !explodedThisRound,
                containerColor = ElectricPink,
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                modifier = Modifier
                    .weight(1f)
                    .testTag("bomb_pass_right")
            )
        }
    }
}

/**
 * 3P Competitive Arena #2: 三色领地冲刺 (Tri-Color Turf Sprint)
 */
@Composable
fun TriColorTurfGame(
    players: List<PartyPlayer>,
    haptics: PartyHaptics,
    isPaused: Boolean,
    onMatchFinished: (MatchResultSummary) -> Unit
) {
    val trio = remember(players) {
        val base = players.take(3).toMutableList()
        while (base.size < 3) {
            base.add(PartyPlayer("bot_turf_${base.size}", "玩家${base.size + 1}", "⚡", base.size, isBot = true))
        }
        base.toList()
    }
    val tileColors = listOf(ElectricPink, CobaltBlue, SunburstYellow)
    // 16 tiles: -1 = neutral, 0/1/2 = owner index
    val owners = remember { mutableStateListOf<Int>().apply { repeat(16) { add(-1) } } }
    var bonusTileIdx by remember { mutableIntStateOf(5) }
    var timeLeftSec by remember { mutableIntStateOf(22) }
    var burstReady by remember { mutableStateOf(true) }

    LaunchedEffect(isPaused) {
        if (isPaused) return@LaunchedEffect
        while (timeLeftSec > 0 && !isPaused) {
            delay(1000L)
            timeLeftSec--
            if (timeLeftSec % 4 == 0) {
                bonusTileIdx = Random.nextInt(0, 16)
            }
        }
        if (timeLeftSec <= 0) {
            val counts = listOf(0, 1, 2).map { idx -> owners.count { it == idx } * 10 }
            val rankings = trio.mapIndexed { idx, p -> p.copy(score = counts[idx]) }
                .sortedByDescending { it.score }
            val mvp = rankings.first()
            onMatchFinished(
                MatchResultSummary(
                    gameId = GameId.TURF_SPRINT,
                    gameTitle = "三色领地冲刺",
                    playerCount = 3,
                    mvpName = mvp.name,
                    mvpEmoji = mvp.avatarEmoji,
                    mvpScore = mvp.score,
                    localWon = mvp.id == trio[0].id,
                    rankings = rankings,
                    note = "最终领地格数 ${owners.count { it == 0 }} / 16"
                )
            )
        }
    }

    // Bots rapidly paint tiles in real time
    LaunchedEffect(isPaused, timeLeftSec > 0) {
        if (isPaused) return@LaunchedEffect
        while (timeLeftSec > 0 && !isPaused) {
            delay(Random.nextLong(260L, 460L))
            val bot = if (Random.nextBoolean()) 1 else 2
            val target = if (Random.nextFloat() < 0.35f) bonusTileIdx else Random.nextInt(0, 16)
            owners[target] = bot
        }
    }

    LaunchedEffect(burstReady) {
        if (!burstReady) {
            delay(5000L)
            burstReady = true
        }
    }

    val counts = listOf(0, 1, 2).map { idx -> owners.count { it == idx } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        PopCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    trio.forEachIndexed { idx, player ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(tileColors[idx])
                                    .border(1.5.dp, DeepInk, CircleShape)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "${player.name.take(3)} ${counts[idx]}格",
                                style = MaterialTheme.typography.labelMedium,
                                color = DeepInk
                            )
                        }
                    }
                    PopTag("${timeLeftSec}s", if (timeLeftSec <= 5) FlameOrange else SunburstYellow)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { timeLeftSec / 22f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = ElectricPink,
                    trackColor = WarmCreamAlt
                )
            }
        }

        // 4x4 Interactive Dopamine Turf Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            for (r in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (c in 0 until 4) {
                        val idx = r * 4 + c
                        val owner = owners[idx]
                        val isBonus = idx == bonusTileIdx
                        val bg = if (owner in 0..2) tileColors[owner] else PureWhite
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(bg)
                                .border(
                                    width = if (isBonus) 3.5.dp else 2.dp,
                                    color = DeepInk,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    owners[idx] = 0
                                    if (isBonus) {
                                        // Bonus tile paints adjacent cell too!
                                        val neighbor = (idx + 1).coerceAtMost(15)
                                        owners[neighbor] = 0
                                        haptics.bumpMatch()
                                    } else {
                                        haptics.lightTap()
                                    }
                                }
                                .testTag("turf_tile_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    isBonus -> "⭐"
                                    owner in 0..2 -> trio[owner].avatarEmoji
                                    else -> ""
                                },
                                fontSize = 24.sp
                            )
                        }
                    }
                }
            }
        }

        PopButton(
            text = if (burstReady) "⚡ 十字爆涂技能（连占4格）" else "技能充能中...",
            onClick = {
                if (burstReady) {
                    burstReady = false
                    val center = Random.nextInt(5, 11)
                    listOf(center, center - 1, center + 1, (center - 4).coerceAtLeast(0))
                        .distinct()
                        .forEach { if (it in 0..15) owners[it] = 0 }
                    haptics.bumpMatch()
                }
            },
            enabled = burstReady,
            containerColor = MintGreen,
            contentColor = DeepInk,
            icon = Icons.Filled.Bolt,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("turf_burst_button")
        )
    }
}
