package com.example.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.example.ui.theme.WarmCream
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

/**
 * 4P 2v2 Physical Co-op Game #1: 2v2 体感天平接力 (2v2 Tilt Balance Relay)
 * You + Teammate combine tilt vectors (accelerometer + tap nudges) to keep the team orb
 * inside the shifting safe corridor and reach 100% before the Rival Blue Team!
 */
@Composable
fun TiltBalanceRelayGame(
    players: List<PartyPlayer>,
    tilt: TiltTelemetry,
    haptics: PartyHaptics,
    isPaused: Boolean,
    onMatchFinished: (MatchResultSummary) -> Unit
) {
    val quad = remember(players) {
        val base = players.take(4).toMutableList()
        while (base.size < 4) {
            base.add(PartyPlayer("bot_q_${base.size}", "队友${base.size + 1}", "🦄", base.size, isBot = true))
        }
        base.toList()
    }
    val redTeam = listOf(quad[0], quad[1])
    val blueTeam = listOf(quad[2], quad[3])

    var orbX by remember { mutableFloatStateOf(0.5f) }
    var gateCenterX by remember { mutableFloatStateOf(0.5f) }
    var gateTargetX by remember { mutableFloatStateOf(0.5f) }
    var manualNudge by remember { mutableFloatStateOf(0f) }
    var redProgress by remember { mutableFloatStateOf(0f) }
    var blueProgress by remember { mutableFloatStateOf(0f) }
    var teammateVoiceHint by remember { mutableStateOf("📣 队友喊话：保持居中，准备往右偏！") }
    var finished by remember { mutableStateOf(false) }

    // 60fps co-op balance simulation
    LaunchedEffect(isPaused, finished) {
        if (isPaused || finished) return@LaunchedEffect
        var lastNanos = 0L
        var hintTimer = 0f
        while (!finished && !isPaused) {
            withFrameNanos { nanos ->
                if (lastNanos == 0L) {
                    lastNanos = nanos
                    return@withFrameNanos
                }
                val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceIn(0.004f, 0.032f)
                lastNanos = nanos

                // Shift safe corridor smoothly
                val diffGate = gateTargetX - gateCenterX
                if (abs(diffGate) < 0.03f) {
                    gateTargetX = Random.nextFloat() * 0.62f + 0.19f
                } else {
                    gateCenterX += diffGate * dt * 1.15f
                }

                // Teammate gently assists toward gate center while local player steers with real tilt + nudge
                val teammateAssist = (gateCenterX - orbX) * 0.45f
                val combinedSteer = (tilt.rollX * 0.95f) + manualNudge + teammateAssist
                orbX = (orbX + combinedSteer * dt * 0.78f).coerceIn(0.08f, 0.92f)
                manualNudge *= 0.88f

                // Score progress when orb is inside safe corridor
                val inZone = abs(orbX - gateCenterX) < 0.16f
                if (inZone) {
                    redProgress = (redProgress + dt * 0.082f).coerceAtMost(1f)
                } else {
                    redProgress = (redProgress + dt * 0.015f).coerceAtMost(1f)
                }

                // Rival team steady progress with slight variance
                blueProgress = (blueProgress + dt * (0.058f + Random.nextFloat() * 0.018f)).coerceAtMost(1f)

                hintTimer += dt
                if (hintTimer > 1.8f) {
                    hintTimer = 0f
                    teammateVoiceHint = when {
                        gateCenterX < orbX - 0.08f -> "📣 ${redTeam[1].name}：快一起向左倾斜！"
                        gateCenterX > orbX + 0.08f -> "📣 ${redTeam[1].name}：快一起向右倾斜！"
                        else -> "🔥 ${redTeam[1].name}：稳住了！保持同步加速冲刺！"
                    }
                    if (inZone) haptics.lightTap()
                }

                if (redProgress >= 1f || blueProgress >= 1f) {
                    finished = true
                    val redWon = redProgress >= blueProgress
                    val rankings = listOf(
                        redTeam[0].copy(score = (redProgress * 100).toInt()),
                        redTeam[1].copy(score = (redProgress * 100).toInt()),
                        blueTeam[0].copy(score = (blueProgress * 100).toInt()),
                        blueTeam[1].copy(score = (blueProgress * 100).toInt())
                    ).sortedByDescending { it.score }
                    val mvp = rankings.first()
                    onMatchFinished(
                        MatchResultSummary(
                            gameId = GameId.TILT_BALANCE,
                            gameTitle = "2v2 体感天平",
                            playerCount = 4,
                            mvpName = if (redWon) "粉队（${redTeam[0].name}&${redTeam[1].name}）" else "蓝队",
                            mvpEmoji = if (redWon) "🏆" else "🔵",
                            mvpScore = mvp.score,
                            localWon = redWon,
                            rankings = rankings,
                            note = "粉队 ${(redProgress * 100).toInt()}% vs 蓝队 ${(blueProgress * 100).toInt()}%"
                        )
                    )
                }
            }
        }
    }

    val inSafeZone = abs(orbX - gateCenterX) < 0.16f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        PopCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "💗 粉队 (${redTeam[0].name}+${redTeam[1].name})",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricPink
                    )
                    Text("${(redProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = ElectricPink)
                }
                LinearProgressIndicator(
                    progress = { redProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = ElectricPink,
                    trackColor = ElectricPinkSoft
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "💙 蓝队 (${blueTeam[0].name}+${blueTeam[1].name})",
                        style = MaterialTheme.typography.titleSmall,
                        color = CobaltBlue
                    )
                    Text("${(blueProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = CobaltBlue)
                }
                LinearProgressIndicator(
                    progress = { blueProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = CobaltBlue,
                    trackColor = CobaltBlueSoft
                )
            }
        }

        // Balance Track Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(if (inSafeZone) MintGreenSoft else FlameOrangeSoft)
                .border(3.dp, DeepInk, RoundedCornerShape(24.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val laneWidth = w * 0.32f
                val laneLeft = (gateCenterX * w - laneWidth / 2f).coerceIn(0f, w - laneWidth)

                // Safe glowing corridor
                drawRoundRect(
                    color = MintGreen.copy(alpha = 0.45f),
                    topLeft = Offset(laneLeft, 20f),
                    size = Size(laneWidth, h - 40f),
                    cornerRadius = CornerRadius(24f, 24f)
                )
                drawRoundRect(
                    color = DeepInk,
                    topLeft = Offset(laneLeft, 20f),
                    size = Size(laneWidth, h - 40f),
                    cornerRadius = CornerRadius(24f, 24f),
                    style = Stroke(5f)
                )

                // Horizontal balance rail
                drawLine(DeepInk, Offset(24f, h * 0.62f), Offset(w - 24f, h * 0.62f), strokeWidth = 8f)

                // Team energy orb
                val orbCenter = Offset(orbX * w, h * 0.62f)
                drawCircle(
                    color = if (inSafeZone) SunburstYellow else FlameOrange,
                    radius = w * 0.085f,
                    center = orbCenter
                )
                drawCircle(
                    color = DeepInk,
                    radius = w * 0.085f,
                    center = orbCenter,
                    style = Stroke(6f)
                )
            }

            PopTag(
                text = teammateVoiceHint,
                containerColor = PureWhite,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
            )
            PopTag(
                text = if (inSafeZone) "⚡ 同步加速中！" else "⚠️ 偏离安全轨！快倾斜拉回",
                containerColor = if (inSafeZone) MintGreen else FlameOrange,
                contentColor = if (inSafeZone) DeepInk else PureWhite,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
            )
        }

        // Tactile Nudge Fallback + Tilt Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PopButton(
                text = "左倾微调",
                onClick = {
                    manualNudge = -1.35f
                    haptics.lightTap()
                },
                containerColor = CobaltBlue,
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                modifier = Modifier
                    .weight(1f)
                    .testTag("balance_nudge_left")
            )
            PopButton(
                text = "右倾微调",
                onClick = {
                    manualNudge = 1.35f
                    haptics.lightTap()
                },
                containerColor = ElectricPink,
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                modifier = Modifier
                    .weight(1f)
                    .testTag("balance_nudge_right")
            )
        }
    }
}

/**
 * 4P Competitive Game #2: 四方撞色抢拍 (Quad Color Clash - Stroop Arena)
 */
@Composable
fun QuadColorClashGame(
    players: List<PartyPlayer>,
    haptics: PartyHaptics,
    isPaused: Boolean,
    onMatchFinished: (MatchResultSummary) -> Unit
) {
    val quad = remember(players) {
        val base = players.take(4).toMutableList()
        while (base.size < 4) {
            base.add(PartyPlayer("bot_c_${base.size}", "玩家${base.size + 1}", "🐯", base.size, isBot = true))
        }
        base.toList()
    }
    val palette = listOf(
        Triple("电光粉", ElectricPink, 0),
        Triple("潮流蓝", CobaltBlue, 1),
        Triple("薄荷绿", MintGreen, 2),
        Triple("柠檬黄", SunburstYellow, 3)
    )

    val scores = remember { mutableStateListOf(0, 0, 0, 0) }
    var round by remember { mutableIntStateOf(1) }
    val totalRounds = 10
    var wordIdx by remember { mutableIntStateOf(0) }
    var inkIdx by remember { mutableIntStateOf(1) }
    var combo by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf("看清【字体实际颜色】，别被文字内容骗了！") }

    fun nextQuestion() {
        wordIdx = Random.nextInt(0, 4)
        inkIdx = (wordIdx + Random.nextInt(1, 4)) % 4
    }

    LaunchedEffect(Unit) { nextQuestion() }

    // Bots also race on each round
    LaunchedEffect(round, isPaused) {
        if (isPaused || round > totalRounds) return@LaunchedEffect
        val botDelay = Random.nextLong(1400L, 2600L)
        delay(botDelay)
        val botWinner = Random.nextInt(1, 4)
        scores[botWinner] += 10
        feedback = "⚡ ${quad[botWinner].name} 抢先拍中！"
        nextQuestion()
        round++
    }

    LaunchedEffect(round) {
        if (round > totalRounds) {
            val rankings = quad.mapIndexed { idx, p -> p.copy(score = scores[idx]) }
                .sortedByDescending { it.score }
            val mvp = rankings.first()
            onMatchFinished(
                MatchResultSummary(
                    gameId = GameId.QUAD_CLASH,
                    gameTitle = "四方撞色抢拍",
                    playerCount = 4,
                    mvpName = mvp.name,
                    mvpEmoji = mvp.avatarEmoji,
                    mvpScore = mvp.score,
                    localWon = mvp.id == quad[0].id,
                    rankings = rankings,
                    note = "最高连击 $combo 次！"
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 4-player score strip
        PopCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                quad.forEachIndexed { idx, p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(p.avatarEmoji, fontSize = 20.sp)
                        Text(p.name.take(3), style = MaterialTheme.typography.labelSmall)
                        Text(
                            "${scores[idx]}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (idx == 0) ElectricPink else DeepInk
                        )
                    }
                }
            }
        }

        // Giant Stroop Card
        PopCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 14.dp),
            containerColor = WarmCream
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopTag("第 ${round.coerceAtMost(totalRounds)} / $totalRounds 题", SunburstYellow)
                    if (combo >= 2) {
                        PopTag("🔥 ${combo}连击！", ElectricPink, PureWhite)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "请点击【字体本身的颜色】",
                    style = MaterialTheme.typography.labelLarge,
                    color = DeepInk
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(PureWhite)
                        .border(3.dp, DeepInk, RoundedCornerShape(22.dp))
                        .padding(horizontal = 36.dp, vertical = 22.dp)
                ) {
                    Text(
                        text = palette[wordIdx].first,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 46.sp),
                        color = palette[inkIdx].second
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = feedback,
                    style = MaterialTheme.typography.bodyLarge,
                    color = DeepInk,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 2x2 Color Tap Buttons
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0 to 1, 2 to 3).forEach { (a, b) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(a, b).forEach { colorIdx ->
                        val (label, col, _) = palette[colorIdx]
                        PopButton(
                            text = label,
                            onClick = {
                                if (round <= totalRounds) {
                                    if (colorIdx == inkIdx) {
                                        combo++
                                        val pts = 12 + (combo.coerceAtMost(3) * 3)
                                        scores[0] += pts
                                        haptics.bumpMatch()
                                        feedback = if (combo >= 3) "❄️ 三连击冰冻对手！+$pts 分" else "🎯 漂亮！+$pts 分"
                                    } else {
                                        combo = 0
                                        scores[0] = (scores[0] - 4).coerceAtLeast(0)
                                        haptics.bombExplode()
                                        feedback = "😵 看花眼啦！-4 分"
                                    }
                                    nextQuestion()
                                    round++
                                }
                            },
                            containerColor = col,
                            contentColor = if (colorIdx == 3 || colorIdx == 2) DeepInk else PureWhite,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("clash_color_$colorIdx")
                        )
                    }
                }
            }
        }
    }
}
