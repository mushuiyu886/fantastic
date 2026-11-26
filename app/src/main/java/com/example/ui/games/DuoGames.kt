package com.example.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameId
import com.example.data.MatchResultSummary
import com.example.data.PartyPlayer
import com.example.hardware.PartyHaptics
import com.example.hardware.TiltTelemetry
import com.example.ui.theme.AvatarBadge
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CobaltBlueSoft
import com.example.ui.theme.DeepInk
import com.example.ui.theme.ElectricPink
import com.example.ui.theme.ElectricPinkSoft
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.MintGreen
import com.example.ui.theme.MintGreenSoft
import com.example.ui.theme.PopButton
import com.example.ui.theme.PopCard
import com.example.ui.theme.PopTag
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SunburstYellow
import com.example.ui.theme.SunburstYellowSoft
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * 2P High-Skill Game #1: 极速霓虹弹球 (60fps Neon Air Hockey)
 */
@Composable
fun AirHockeyGame(
    players: List<PartyPlayer>,
    haptics: PartyHaptics,
    isPaused: Boolean,
    onMatchFinished: (MatchResultSummary) -> Unit
) {
    val me = players.firstOrNull { it.isLocal } ?: players.first()
    val rival = players.firstOrNull { !it.isLocal } ?: PartyPlayer("ai_2p", "对手", "🐼", 1, isBot = true)
    val winTarget = 5

    var myScore by remember { mutableIntStateOf(0) }
    var rivalScore by remember { mutableIntStateOf(0) }
    var matchEnded by remember { mutableStateOf(false) }

    // Normalized table coordinates: X in [0f, 1f], Y in [0f, 1f]
    var puckX by remember { mutableFloatStateOf(0.5f) }
    var puckY by remember { mutableFloatStateOf(0.5f) }
    var puckVx by remember { mutableFloatStateOf(0.28f) }
    var puckVy by remember { mutableFloatStateOf(0.45f) }

    var myX by remember { mutableFloatStateOf(0.5f) }
    var myY by remember { mutableFloatStateOf(0.84f) }
    var prevMyX by remember { mutableFloatStateOf(0.5f) }
    var prevMyY by remember { mutableFloatStateOf(0.84f) }

    var rivalX by remember { mutableFloatStateOf(0.5f) }
    var rivalY by remember { mutableFloatStateOf(0.16f) }

    var dashBoostReady by remember { mutableStateOf(true) }
    var dashActiveFrames by remember { mutableIntStateOf(0) }
    var goalFlashText by remember { mutableStateOf<String?>(null) }

    val trail = remember { ArrayDeque<Offset>(10) }

    // 60fps physics loop
    LaunchedEffect(isPaused, matchEnded) {
        if (isPaused || matchEnded) return@LaunchedEffect
        var lastNanos = 0L
        while (!matchEnded && !isPaused) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) {
                    lastNanos = nanos
                    return@withFrameNanos
                }
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.032f)
                lastNanos = nanos

                // Update trail
                if (trail.size >= 8) trail.removeFirst()
                trail.addLast(Offset(puckX, puckY))

                // Player velocity for impulse
                val myVx = (myX - prevMyX) / dt
                val myVy = (myY - prevMyY) / dt
                prevMyX = myX
                prevMyY = myY

                // Rival AI tracking with skill-capped speed so player can beat it with angled shots
                val targetX = if (puckY < 0.56f) puckX else 0.5f + (puckX - 0.5f) * 0.45f
                val targetY = if (puckY < 0.42f && puckVy < 0f) (puckY - 0.04f).coerceIn(0.1f, 0.42f) else 0.16f
                val rivalStep = 0.72f * dt
                val dxR = targetX - rivalX
                val dyR = targetY - rivalY
                val distR = hypot(dxR, dyR).coerceAtLeast(0.0001f)
                val prevRX = rivalX
                val prevRY = rivalY
                if (distR > 0.005f) {
                    rivalX = (rivalX + (dxR / distR) * minOf(rivalStep, distR)).coerceIn(0.11f, 0.89f)
                    rivalY = (rivalY + (dyR / distR) * minOf(rivalStep, distR)).coerceIn(0.10f, 0.44f)
                }
                val rivalVx = (rivalX - prevRX) / dt
                val rivalVy = (rivalY - prevRY) / dt

                // Move puck
                puckX += puckVx * dt
                puckY += puckVy * dt

                // Subtle friction
                puckVx *= 0.996f
                puckVy *= 0.996f

                val puckR = 0.048f
                val strikerR = 0.085f
                val goalMinX = 0.30f
                val goalMaxX = 0.70f

                // Side wall bounce
                if (puckX < puckR) {
                    puckX = puckR
                    puckVx = abs(puckVx) * 0.96f
                    haptics.lightTap()
                } else if (puckX > 1f - puckR) {
                    puckX = 1f - puckR
                    puckVx = -abs(puckVx) * 0.96f
                    haptics.lightTap()
                }

                // Top/bottom wall bounce or GOAL!
                if (puckY < puckR) {
                    if (puckX in goalMinX..goalMaxX) {
                        // Local player scored!
                        myScore += 1
                        haptics.bumpMatch()
                        goalFlashText = "🔥 暴扣得分！"
                        puckX = 0.5f; puckY = 0.5f
                        puckVx = if (Random.nextBoolean()) 0.25f else -0.25f
                        puckVy = 0.42f
                    } else {
                        puckY = puckR
                        puckVy = abs(puckVy) * 0.96f
                    }
                } else if (puckY > 1f - puckR) {
                    if (puckX in goalMinX..goalMaxX) {
                        rivalScore += 1
                        haptics.bombExplode()
                        goalFlashText = "💥 对手破门！"
                        puckX = 0.5f; puckY = 0.5f
                        puckVx = if (Random.nextBoolean()) 0.25f else -0.25f
                        puckVy = -0.42f
                    } else {
                        puckY = 1f - puckR
                        puckVy = -abs(puckVy) * 0.96f
                    }
                }

                // Striker collisions
                fun resolveHit(sx: Float, sy: Float, svx: Float, svy: Float, boost: Float) {
                    val dx = puckX - sx
                    val dy = puckY - sy
                    val dist = hypot(dx, dy)
                    val minDist = puckR + strikerR
                    if (dist < minDist && dist > 0.0001f) {
                        val nx = dx / dist
                        val ny = dy / dist
                        puckX = sx + nx * (minDist + 0.006f)
                        puckY = sy + ny * (minDist + 0.006f)
                        val speed = hypot(puckVx, puckVy).coerceIn(0.68f, 1.45f) * boost
                        puckVx = (nx * speed + svx * 0.32f).coerceIn(-1.65f, 1.65f)
                        puckVy = (ny * speed + svy * 0.32f).coerceIn(-1.85f, 1.85f)
                        haptics.paddleHit()
                    }
                }

                val myBoost = if (dashActiveFrames > 0) 1.45f else 1.08f
                if (dashActiveFrames > 0) dashActiveFrames--
                resolveHit(myX, myY, myVx, myVy, myBoost)
                resolveHit(rivalX, rivalY, rivalVx, rivalVy, 1.04f)

                if (myScore >= winTarget || rivalScore >= winTarget) {
                    matchEnded = true
                    val localWon = myScore >= winTarget
                    val rankings = listOf(
                        me.copy(score = myScore * 20),
                        rival.copy(score = rivalScore * 20)
                    ).sortedByDescending { it.score }
                    val mvp = rankings.first()
                    onMatchFinished(
                        MatchResultSummary(
                            gameId = GameId.AIR_HOCKEY,
                            gameTitle = "极速霓虹弹球",
                            playerCount = 2,
                            mvpName = mvp.name,
                            mvpEmoji = mvp.avatarEmoji,
                            mvpScore = mvp.score,
                            localWon = localWon,
                            rankings = rankings,
                            note = "最终比分 $myScore : $rivalScore"
                        )
                    )
                }
            }
        }
    }

    LaunchedEffect(goalFlashText) {
        if (goalFlashText != null) {
            delay(850L)
            goalFlashText = null
        }
    }

    LaunchedEffect(dashBoostReady) {
        if (!dashBoostReady) {
            delay(3500L)
            dashBoostReady = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Scoreboard Strip
        PopCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = PureWhite
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarBadge(rival.avatarEmoji, rival.colorIndex, size = 36.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(rival.name, style = MaterialTheme.typography.titleSmall, color = DeepInk)
                        Text("蓝方球门", style = MaterialTheme.typography.labelSmall, color = CobaltBlue)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(SunburstYellowSoft)
                        .border(2.dp, DeepInk, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$rivalScore",
                        style = MaterialTheme.typography.headlineMedium,
                        color = CobaltBlue
                    )
                    Text(
                        text = " : ",
                        style = MaterialTheme.typography.headlineMedium,
                        color = DeepInk
                    )
                    Text(
                        text = "$myScore",
                        style = MaterialTheme.typography.headlineMedium,
                        color = ElectricPink
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(me.name, style = MaterialTheme.typography.titleSmall, color = DeepInk)
                        Text("粉方球门", style = MaterialTheme.typography.labelSmall, color = ElectricPink)
                    }
                    Spacer(Modifier.width(8.dp))
                    AvatarBadge(me.avatarEmoji, me.colorIndex, size = 36.dp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Interactive Air Hockey Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(PureWhite)
                .border(3.dp, DeepInk, RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val nx = (change.position.x / size.width.toFloat()).coerceIn(0.10f, 0.90f)
                        val ny = (change.position.y / size.height.toFloat()).coerceIn(0.54f, 0.91f)
                        myX = nx
                        myY = ny
                    }
                }
                .testTag("air_hockey_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Half-court dopamine tint
                drawRect(color = CobaltBlueSoft.copy(alpha = 0.45f), size = Size(w, h / 2f))
                drawRect(
                    color = ElectricPinkSoft.copy(alpha = 0.45f),
                    topLeft = Offset(0f, h / 2f),
                    size = Size(w, h / 2f)
                )

                // Center line & circle
                drawLine(DeepInk, Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 5f)
                drawCircle(
                    color = SunburstYellow,
                    radius = w * 0.14f,
                    center = Offset(w / 2f, h / 2f)
                )
                drawCircle(
                    color = DeepInk,
                    radius = w * 0.14f,
                    center = Offset(w / 2f, h / 2f),
                    style = Stroke(width = 5f)
                )

                // Top & bottom goal pockets
                val goalLeft = w * 0.30f
                val goalWidth = w * 0.40f
                drawRoundRect(
                    color = CobaltBlue,
                    topLeft = Offset(goalLeft, -10f),
                    size = Size(goalWidth, 26f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                drawRoundRect(
                    color = ElectricPink,
                    topLeft = Offset(goalLeft, h - 16f),
                    size = Size(goalWidth, 26f),
                    cornerRadius = CornerRadius(14f, 14f)
                )

                // Puck motion trail
                trail.forEachIndexed { idx, pt ->
                    val alpha = (idx + 1).toFloat() / (trail.size + 1) * 0.4f
                    drawCircle(
                        color = SunburstYellow.copy(alpha = alpha),
                        radius = w * 0.042f * ((idx + 2).toFloat() / (trail.size + 2)),
                        center = Offset(pt.x * w, pt.y * h)
                    )
                }

                // Rival striker (top)
                val sr = w * 0.085f
                drawCircle(CobaltBlue, radius = sr, center = Offset(rivalX * w, rivalY * h))
                drawCircle(DeepInk, radius = sr, center = Offset(rivalX * w, rivalY * h), style = Stroke(6f))
                drawCircle(PureWhite, radius = sr * 0.42f, center = Offset(rivalX * w, rivalY * h))

                // Player striker (bottom)
                val playerColor = if (dashActiveFrames > 0) SunburstYellow else ElectricPink
                drawCircle(playerColor, radius = sr, center = Offset(myX * w, myY * h))
                drawCircle(DeepInk, radius = sr, center = Offset(myX * w, myY * h), style = Stroke(6f))
                drawCircle(PureWhite, radius = sr * 0.42f, center = Offset(myX * w, myY * h))

                // Puck
                val pr = w * 0.048f
                drawCircle(SunburstYellow, radius = pr, center = Offset(puckX * w, puckY * h))
                drawCircle(DeepInk, radius = pr, center = Offset(puckX * w, puckY * h), style = Stroke(5f))
            }

            goalFlashText?.let { flash ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SunburstYellow)
                        .border(2.5.dp, DeepInk, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(flash, style = MaterialTheme.typography.headlineMedium, color = DeepInk)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "👆 拖动下半场击球器微操对攻（率先5分胜）",
                style = MaterialTheme.typography.bodyMedium,
                color = DeepInk,
                modifier = Modifier.weight(1f)
            )
            PopButton(
                text = if (dashBoostReady) "冲刺暴扣" else "蓄力中...",
                onClick = {
                    if (dashBoostReady) {
                        dashBoostReady = false
                        dashActiveFrames = 26
                        haptics.bumpMatch()
                    }
                },
                enabled = dashBoostReady,
                containerColor = SunburstYellow,
                contentColor = DeepInk,
                icon = Icons.Filled.Bolt,
                modifier = Modifier.testTag("air_hockey_dash_button")
            )
        }
    }
}

/**
 * 2P High-Skill Game #2: 微操节拍对决 (Precision Reflex + Fakeout + Tilt Shield)
 */
@Composable
fun ReflexDuelGame(
    players: List<PartyPlayer>,
    tilt: TiltTelemetry,
    haptics: PartyHaptics,
    isPaused: Boolean,
    onMatchFinished: (MatchResultSummary) -> Unit
) {
    val me = players.firstOrNull { it.isLocal } ?: players.first()
    val rival = players.firstOrNull { !it.isLocal } ?: PartyPlayer("ai_2p", "闪电阿杰", "⚡", 1, isBot = true)

    var round by remember { mutableIntStateOf(1) }
    val totalRounds = 7
    var myScore by remember { mutableIntStateOf(0) }
    var rivalScore by remember { mutableIntStateOf(0) }

    // Phase: WAIT, GO, FAKEOUT, RESULT
    var phase by remember { mutableStateOf("WAIT") }
    var roundMessage by remember { mutableStateOf("稳住手指！看到电光绿立即出击，红叉假动作别点！") }
    var signalStartMs by remember { mutableLongStateOf(0L) }
    var shieldArmed by remember { mutableStateOf(false) }

    LaunchedEffect(tilt.rollX) {
        if (abs(tilt.rollX) > 0.35f) {
            shieldArmed = true
        }
    }

    LaunchedEffect(round, isPaused) {
        if (isPaused || round > totalRounds) return@LaunchedEffect
        phase = "WAIT"
        shieldArmed = false
        roundMessage = "第 $round/$totalRounds 回合 · 屏住呼吸..."
        delay(Random.nextLong(1100L, 2400L))

        val isFakeout = Random.nextFloat() < 0.30f
        if (isFakeout) {
            phase = "FAKEOUT"
            roundMessage = "🚫 假动作陷阱！千万别点！"
            haptics.lightTap()
            delay(1300L)
            if (phase == "FAKEOUT") {
                myScore += 10
                roundMessage = "🎯 完美识破假动作！+10分"
                phase = "RESULT"
                haptics.bumpMatch()
                delay(1000L)
                round++
            }
        } else {
            phase = "GO"
            signalStartMs = System.currentTimeMillis()
            roundMessage = "⚡ 立即出击！"
            haptics.paddleHit()
            val botReactionMs = Random.nextLong(330L, 680L)
            delay(botReactionMs)
            if (phase == "GO") {
                rivalScore += 15
                roundMessage = "💨 ${rival.name} 抢先出手（${botReactionMs}ms）！"
                phase = "RESULT"
                haptics.bombExplode()
                delay(1100L)
                round++
            }
        }
    }

    LaunchedEffect(round) {
        if (round > totalRounds) {
            val rankings = listOf(
                me.copy(score = myScore),
                rival.copy(score = rivalScore)
            ).sortedByDescending { it.score }
            val mvp = rankings.first()
            onMatchFinished(
                MatchResultSummary(
                    gameId = GameId.REFLEX_DUEL,
                    gameTitle = "微操节拍对决",
                    playerCount = 2,
                    mvpName = mvp.name,
                    mvpEmoji = mvp.avatarEmoji,
                    mvpScore = mvp.score,
                    localWon = myScore >= rivalScore,
                    rankings = rankings,
                    note = "7回合微操对决 · $myScore vs $rivalScore"
                )
            )
        }
    }

    val arenaColor = when (phase) {
        "GO" -> MintGreen
        "FAKEOUT" -> FlameOrange
        "RESULT" -> SunburstYellowSoft
        else -> WarmCream
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        PopCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("${me.avatarEmoji} ${me.name}", style = MaterialTheme.typography.titleSmall)
                    Text("$myScore 分", style = MaterialTheme.typography.headlineMedium, color = ElectricPink)
                }
                PopTag("回合 ${round.coerceAtMost(totalRounds)} / $totalRounds", SunburstYellow)
                Column(horizontalAlignment = Alignment.End) {
                    Text("${rival.avatarEmoji} ${rival.name}", style = MaterialTheme.typography.titleSmall)
                    Text("$rivalScore 分", style = MaterialTheme.typography.headlineMedium, color = CobaltBlue)
                }
            }
        }

        PopCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
            containerColor = arenaColor
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(136.dp)
                        .clip(CircleShape)
                        .background(PureWhite)
                        .border(4.dp, DeepInk, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (phase) {
                            "GO" -> "⚡"
                            "FAKEOUT" -> "🚫"
                            "RESULT" -> "✨"
                            else -> "👀"
                        },
                        fontSize = 58.sp
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = roundMessage,
                    style = MaterialTheme.typography.titleLarge,
                    color = DeepInk,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                PopTag(
                    text = if (shieldArmed) "🛡️ 倾斜举盾已就绪（命中额外+5）" else "💡 左右倾斜手机可激活防反护盾",
                    containerColor = if (shieldArmed) MintGreenSoft else PureWhite
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PopButton(
                text = "举盾蓄力",
                onClick = {
                    shieldArmed = true
                    haptics.lightTap()
                },
                containerColor = CobaltBlue,
                icon = Icons.Filled.Shield,
                modifier = Modifier
                    .weight(1f)
                    .testTag("reflex_shield_button")
            )
            PopButton(
                text = "极限出击！",
                onClick = {
                    when (phase) {
                        "GO" -> {
                            val reaction = (System.currentTimeMillis() - signalStartMs).coerceAtLeast(1L)
                            val bonus = if (shieldArmed) 5 else 0
                            val pts = (if (reaction < 320L) 20 else 15) + bonus
                            myScore += pts
                            roundMessage = "🔥 极限命中！${reaction}ms (+${pts}分)"
                            phase = "RESULT"
                            haptics.bumpMatch()
                            round++
                        }
                        "FAKEOUT" -> {
                            rivalScore += 12
                            roundMessage = "😵 踩中假动作陷阱！对手 +12分"
                            phase = "RESULT"
                            haptics.bombExplode()
                            round++
                        }
                        "WAIT" -> {
                            rivalScore += 10
                            roundMessage = "⚠️ 抢跑犯规！对手 +10分"
                            phase = "RESULT"
                            haptics.bombExplode()
                            round++
                        }
                    }
                },
                containerColor = ElectricPink,
                icon = Icons.Filled.Bolt,
                modifier = Modifier
                    .weight(1.4f)
                    .testTag("reflex_strike_button")
            )
        }
    }
}
