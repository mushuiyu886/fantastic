package com.example.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CharadePromptItem
import com.example.data.DynamicPackPayload
import com.example.data.GameId
import com.example.data.MatchResultSummary
import com.example.data.PartyPlayer
import com.example.data.WordPairItem
import com.example.hardware.PartyHaptics
import com.example.hardware.TiltTelemetry
import com.example.ui.theme.AvatarBadge
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CobaltBlueSoft
import com.example.ui.theme.DeepInk
import com.example.ui.theme.ElectricPink
import com.example.ui.theme.ElectricPinkSoft
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.FlameOrangeSoft
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
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 5-8P Dynamic Resource Pack Game #1: 派对卧底大侦探 (Undercover Spy)
 * Scales Civilian/Undercover/Mr.White roles to exact player count (5-8P),
 * uses dynamically loaded word pack, anti-peek press-and-hold + tilt-to-hide card,
 * face-to-face speaking timer, and live synchronized voting.
 */
@Composable
fun UndercoverSpyGame(
    players: List<PartyPlayer>,
    dynamicPack: DynamicPackPayload?,
    tilt: TiltTelemetry,
    haptics: PartyHaptics,
    onMatchFinished: (MatchResultSummary) -> Unit
) {
    val roster = remember(players) {
        val list = players.toMutableList()
        while (list.size < 5) {
            list.add(PartyPlayer("bot_spy_${list.size}", "派对友${list.size + 1}", "🐼", list.size, isBot = true))
        }
        list.take(8)
    }

    val wordPair = remember(dynamicPack) {
        dynamicPack?.wordPairs?.randomOrNull()
            ?: WordPairItem("鸳鸯火锅", "麻辣香锅", "热辣")
    }

    // Dynamic role assignment scaled by exact player count (5P/6P/7P/8P)
    val spyIndices = remember(roster.size) {
        val spyCount = if (roster.size >= 7) 2 else 1
        roster.indices.shuffled().take(spyCount).toSet()
    }
    val whiteIndex = remember(roster.size, spyIndices) {
        if (roster.size == 6 || roster.size == 8) {
            roster.indices.firstOrNull { it !in spyIndices } ?: -1
        } else -1
    }

    val eliminated = remember { mutableStateListOf<Int>() }
    // Stage: PEEK -> SPEAK -> VOTE -> REVEAL
    var stage by remember { mutableStateOf("PEEK") }
    var isHoldingCard by remember { mutableStateOf(false) }
    var speakTimerSec by remember { mutableIntStateOf(20) }
    var currentSpeakerIdx by remember { mutableIntStateOf(0) }
    var voteResultBanner by remember { mutableStateOf<String?>(null) }

    val localWord = when {
        0 in spyIndices -> wordPair.spyWord
        0 == whiteIndex -> "【白板 · 请靠演技蒙混过关】"
        else -> wordPair.civilianWord
    }
    val localRoleTitle = when {
        0 in spyIndices -> "卧底身份"
        0 == whiteIndex -> "神秘白板"
        else -> "正义平民"
    }

    // Anti-peek tilt guard: if phone tilts face-down, automatically hide secret word
    val showSecretWord = isHoldingCard && !tilt.faceDown

    LaunchedEffect(stage, currentSpeakerIdx) {
        if (stage == "SPEAK") {
            speakTimerSec = 20
            while (speakTimerSec > 0 && stage == "SPEAK") {
                delay(1000L)
                speakTimerSec--
            }
        }
    }

    fun checkVictoryOrNextRound() {
        val aliveSpies = spyIndices.count { it !in eliminated }
        val aliveCivilians = roster.indices.count { it !in eliminated && it !in spyIndices }
        if (aliveSpies == 0 || aliveSpies >= aliveCivilians) {
            val civiliansWon = aliveSpies == 0
            val localIsSpy = 0 in spyIndices
            val localWon = if (localIsSpy) !civiliansWon else civiliansWon
            val rankings = roster.mapIndexed { idx, p ->
                val isSpy = idx in spyIndices
                val won = if (isSpy) !civiliansWon else civiliansWon
                p.copy(score = if (won) 50 else 15)
            }.sortedByDescending { it.score }
            val mvp = rankings.first()
            onMatchFinished(
                MatchResultSummary(
                    gameId = GameId.UNDERCOVER_SPY,
                    gameTitle = "派对卧底大侦探",
                    playerCount = roster.size,
                    mvpName = if (civiliansWon) "平民阵营" else "卧底阵营",
                    mvpEmoji = if (civiliansWon) "🛡️" else "🕵️",
                    mvpScore = mvp.score,
                    localWon = localWon,
                    rankings = rankings,
                    note = "平民词「${wordPair.civilianWord}」vs 卧底词「${wordPair.spyWord}」"
                )
            )
        } else {
            stage = "SPEAK"
            currentSpeakerIdx = roster.indices.first { it !in eliminated }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Dynamic Pack & Role Distribution Header
        PopCard(modifier = Modifier.fillMaxWidth(), containerColor = LilacPopSoft) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "动态词包：${dynamicPack?.title ?: "聚会热梗包"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = DeepInk
                    )
                    Text(
                        "${roster.size}人动态配置：${spyIndices.size}卧底${if (whiteIndex >= 0) "+1白板" else ""} · 词类「${wordPair.hintTag}」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepInk.copy(alpha = 0.8f)
                    )
                }
                PopTag(
                    text = when (stage) {
                        "PEEK" -> "1.秘密看词"
                        "SPEAK" -> "2.线下发言"
                        else -> "3.同步投票"
                    },
                    containerColor = SunburstYellow
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        when (stage) {
            "PEEK" -> {
                PopCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHoldingCard = true
                                    haptics.lightTap()
                                    tryAwaitRelease()
                                    isHoldingCard = false
                                }
                            )
                        }
                        .testTag("spy_peek_card"),
                    containerColor = if (showSecretWord) SunburstYellow else PureWhite
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (showSecretWord) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = "秘密词卡状态",
                            tint = DeepInk,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        if (showSecretWord) {
                            PopTag(localRoleTitle, ElectricPink, PureWhite)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = localWord,
                                style = MaterialTheme.typography.displayLarge,
                                color = DeepInk,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(14.dp))
                            Text("松手或扣下手机立即自动隐藏！", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Text("🕵️", fontSize = 62.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "长按此卡片偷看你的秘密词",
                                style = MaterialTheme.typography.headlineMedium,
                                color = DeepInk,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "内置防偷看传感器：手机倒扣或松手即瞬隐",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DeepInk.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                PopButton(
                    text = "记住了，进入面对面轮流发言！",
                    onClick = {
                        haptics.bumpMatch()
                        stage = "SPEAK"
                        currentSpeakerIdx = 0
                    },
                    containerColor = MintGreen,
                    contentColor = DeepInk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spy_start_speak")
                )
            }

            "SPEAK" -> {
                val speaker = roster[currentSpeakerIdx]
                PopCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    containerColor = WarmCream
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        PopTag("面对面口头描述 · 禁止直接说出词语", SunburstYellow)
                        Spacer(Modifier.height(18.dp))
                        AvatarBadge(speaker.avatarEmoji, speaker.colorIndex, size = 76.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "轮到 ${speaker.name} 发言",
                            style = MaterialTheme.typography.headlineMedium,
                            color = DeepInk
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${speakTimerSec}s",
                            style = MaterialTheme.typography.displayLarge,
                            color = if (speakTimerSec <= 5) FlameOrange else CobaltBlue
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (speaker.isLocal) "请用一句话模糊形容你的词语，别被卧底猜到！"
                            else "（AI 线索提示：“这个东西线下聚会经常碰到，大家都很熟！”）",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PopButton(
                        text = "下一位发言",
                        onClick = {
                            haptics.lightTap()
                            val alive = roster.indices.filter { it !in eliminated }
                            val pos = alive.indexOf(currentSpeakerIdx)
                            if (pos + 1 < alive.size) {
                                currentSpeakerIdx = alive[pos + 1]
                            } else {
                                stage = "VOTE"
                            }
                        },
                        containerColor = CobaltBlue,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("spy_next_speaker")
                    )
                    PopButton(
                        text = "直接开票",
                        onClick = {
                            haptics.bumpMatch()
                            stage = "VOTE"
                        },
                        containerColor = ElectricPink,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("spy_go_vote")
                    )
                }
            }

            else -> {
                Column(modifier = Modifier.weight(1f)) {
                    voteResultBanner?.let { msg ->
                        PopCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            containerColor = SunburstYellowSoft
                        ) {
                            Text(msg, style = MaterialTheme.typography.titleSmall, color = DeepInk)
                        }
                    }
                    Text(
                        "👇 点击你怀疑是卧底的玩家进行同步投票：",
                        style = MaterialTheme.typography.titleSmall,
                        color = DeepInk,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        itemsIndexed(roster) { idx, player ->
                            val isOut = idx in eliminated
                            PopCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("spy_vote_player_$idx"),
                                containerColor = if (isOut) FlameOrangeSoft.copy(alpha = 0.5f) else PureWhite,
                                onClick = if (!isOut) {
                                    {
                                        haptics.bombExplode()
                                        eliminated.add(idx)
                                        val wasSpy = idx in spyIndices
                                        voteResultBanner = if (wasSpy) {
                                            "🎯 票选放逐 ${player.name} —— 果然是卧底！"
                                        } else {
                                            "😱 误伤！${player.name} 其实是无辜平民！"
                                        }
                                        checkVictoryOrNextRound()
                                    }
                                } else null
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AvatarBadge(player.avatarEmoji, player.colorIndex, size = 38.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(player.name, style = MaterialTheme.typography.titleMedium)
                                    }
                                    PopTag(
                                        text = if (isOut) "已出局" else "投TA出局",
                                        containerColor = if (isOut) FlameOrange else ElectricPinkSoft,
                                        contentColor = if (isOut) PureWhite else DeepInk
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 5-8P Dynamic Resource Pack Game #2: 疯狂比划团队接力 (Team Heads-Up Relay)
 * Red vs Blue teams! Hold phone facing friends; tilt DOWN = Correct (+1), tilt UP = Pass!
 */
@Composable
fun TeamHeadsUpRelayGame(
    players: List<PartyPlayer>,
    dynamicPack: DynamicPackPayload?,
    tilt: TiltTelemetry,
    haptics: PartyHaptics,
    isPaused: Boolean,
    onMatchFinished: (MatchResultSummary) -> Unit
) {
    val roster = remember(players) {
        val list = players.toMutableList()
        while (list.size < 5) {
            list.add(PartyPlayer("bot_hu_${list.size}", "队友${list.size + 1}", "⚡", list.size, isBot = true))
        }
        list.take(8)
    }
    val prompts = remember(dynamicPack) {
        (dynamicPack?.charades ?: listOf(
            CharadePromptItem("挤早高峰地铁", "日常演技", "简单"),
            CharadePromptItem("拆盲盒抽到隐藏款", "表情管理", "简单"),
            CharadePromptItem("企鹅在冰面打滑", "动物模仿", "简单"),
            CharadePromptItem("拉面师傅甩拉面", "职业动作", "简单"),
            CharadePromptItem("电影慢动作躲子弹", "动作大片", "中等")
        )).shuffled()
    }

    var promptIdx by remember { mutableIntStateOf(0) }
    var redScore by remember { mutableIntStateOf(0) }
    var blueScore by remember { mutableIntStateOf(0) }
    var timeLeftSec by remember { mutableIntStateOf(35) }
    var tiltCooldown by remember { mutableStateOf(false) }
    var flashBanner by remember { mutableStateOf<String?>(null) }

    fun markCorrect() {
        redScore += 1
        promptIdx = (promptIdx + 1) % prompts.size
        flashBanner = "✅ 答对！红队 +1 分"
        haptics.bumpMatch()
    }

    fun markPass() {
        promptIdx = (promptIdx + 1) % prompts.size
        flashBanner = "⏭️ 已跳过，切换下一题！"
        haptics.lightTap()
    }

    // Real accelerometer pitch detection: tilt forward (>0.62) = Correct, tilt back (<-0.62) = Pass
    LaunchedEffect(tilt.pitchY) {
        if (tiltCooldown || timeLeftSec <= 0 || isPaused) return@LaunchedEffect
        if (tilt.pitchY > 0.68f) {
            tiltCooldown = true
            markCorrect()
            delay(900L)
            tiltCooldown = false
        } else if (tilt.pitchY < -0.68f) {
            tiltCooldown = true
            markPass()
            delay(900L)
            tiltCooldown = false
        }
    }

    LaunchedEffect(flashBanner) {
        if (flashBanner != null) {
            delay(750L)
            flashBanner = null
        }
    }

    // Match timer + rival Blue team simulated relay progress
    LaunchedEffect(isPaused) {
        if (isPaused) return@LaunchedEffect
        while (timeLeftSec > 0 && !isPaused) {
            delay(1000L)
            timeLeftSec--
            if (timeLeftSec % 5 == 0 && Random.nextFloat() < 0.72f) {
                blueScore++
            }
        }
        if (timeLeftSec <= 0) {
            val redWon = redScore >= blueScore
            val rankings = roster.mapIndexed { idx, p ->
                val isRed = idx % 2 == 0
                p.copy(score = if (isRed) redScore * 15 else blueScore * 15)
            }.sortedByDescending { it.score }
            onMatchFinished(
                MatchResultSummary(
                    gameId = GameId.TEAM_HEADS_UP,
                    gameTitle = "疯狂比划接力",
                    playerCount = roster.size,
                    mvpName = if (redWon) "红队狂欢团" else "蓝队闪电团",
                    mvpEmoji = if (redWon) "❤️" else "💙",
                    mvpScore = maxOf(redScore, blueScore) * 15,
                    localWon = redWon,
                    rankings = rankings,
                    note = "红队 $redScore 题 vs 蓝队 $blueScore 题（词包：${dynamicPack?.title ?: "动作包"}）"
                )
            )
        }
    }

    val currentPrompt = prompts[promptIdx % prompts.size]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        PopCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("❤️ 红队（我方）", style = MaterialTheme.typography.labelLarge, color = ElectricPink)
                    Text("$redScore 题", style = MaterialTheme.typography.headlineMedium, color = ElectricPink)
                }
                PopTag("⏱️ ${timeLeftSec}s", if (timeLeftSec <= 8) FlameOrange else SunburstYellow)
                Column(horizontalAlignment = Alignment.End) {
                    Text("💙 蓝队（对手）", style = MaterialTheme.typography.labelLarge, color = CobaltBlue)
                    Text("$blueScore 题", style = MaterialTheme.typography.headlineMedium, color = CobaltBlue)
                }
            }
        }

        // Giant Face-Out Party Word Card
        PopCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 14.dp),
            containerColor = when {
                flashBanner?.startsWith("✅") == true -> MintGreen
                flashBanner?.startsWith("⏭️") == true -> SunburstYellow
                else -> ElectricPinkSoft
            }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PopTag(currentPrompt.category, PureWhite)
                    PopTag("难度：${currentPrompt.difficulty}", SunburstYellow)
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = currentPrompt.prompt,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp),
                    color = DeepInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(22.dp))
                Text(
                    text = flashBanner ?: "📱 屏幕朝向队友比划！前倾手机=答对，后仰=跳过",
                    style = MaterialTheme.typography.titleSmall,
                    color = DeepInk,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PopButton(
                text = "跳过（后仰）",
                onClick = { markPass() },
                containerColor = SunburstYellow,
                contentColor = DeepInk,
                icon = Icons.Filled.SkipNext,
                modifier = Modifier
                    .weight(1f)
                    .testTag("headsup_pass_button")
            )
            PopButton(
                text = "答对！（前倾）",
                onClick = { markCorrect() },
                containerColor = MintGreen,
                contentColor = DeepInk,
                icon = Icons.Filled.Check,
                modifier = Modifier
                    .weight(1.3f)
                    .testTag("headsup_correct_button")
            )
        }
    }
}
