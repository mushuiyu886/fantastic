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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ResourcePackDescriptor
import com.example.data.local.CachedResourcePackEntity
import com.example.data.local.PartyMatchRecordEntity
import com.example.data.local.SessionSnapshotEntity
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VaultScreen(
    latestSnapshot: SessionSnapshotEntity?,
    allPackDescriptors: List<ResourcePackDescriptor>,
    cachedPacks: List<CachedResourcePackEntity>,
    matchHistory: List<PartyMatchRecordEntity>,
    onRestoreSnapshot: () -> Unit,
    onPreloadPack: (String) -> Unit,
    onDeletePack: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val cachedMap = cachedPacks.associateBy { it.packId }
    val totalCachedKb = (cachedPacks.sumOf { it.sizeBytes } / 1024f)

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
            item {
                Column {
                    Text(
                        text = "离线缓存与聚会战绩",
                        style = MaterialTheme.typography.headlineMedium,
                        color = DeepInk
                    )
                    Text(
                        text = "本地 Room 数据库全离线持久化 · 断网照样流畅开黑",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DeepInk.copy(alpha = 0.72f)
                    )
                }
            }

            // 1. Offline Session Snapshot Recovery Guard
            item {
                PopCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = CobaltBlueSoft
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🛡️ 断线重连状态快照", style = MaterialTheme.typography.titleMedium, color = DeepInk)
                            PopTag(
                                text = if (latestSnapshot != null) "已保护" else "待记录",
                                containerColor = if (latestSnapshot != null) MintGreen else PureWhite
                            )
                        }
                        if (latestSnapshot != null) {
                            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                .format(Date(latestSnapshot.updatedAt))
                            Text(
                                text = "最近房间：${latestSnapshot.roomCode} · ${latestSnapshot.playerCount}人 · 同步帧 #${latestSnapshot.seqNumber}（保存于 $timeStr）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DeepInk
                            )
                            PopButton(
                                text = "从离线快照一键恢复房间",
                                onClick = onRestoreSnapshot,
                                containerColor = CobaltBlue,
                                icon = Icons.Filled.Restore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vault_restore_snapshot_btn")
                            )
                        } else {
                            Text(
                                text = "开启房间后将自动实时写入本地快照，防止弱网或切后台中断聚会。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DeepInk.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }

            // 2. 4P+ Dynamic Resource Pack Offline Cache Manager
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📦 4人+ 动态资源包缓存", style = MaterialTheme.typography.titleMedium, color = DeepInk)
                    PopTag(
                        text = "已缓存 ${cachedPacks.size}/${allPackDescriptors.size} · %.1fKB".format(totalCachedKb),
                        containerColor = SunburstYellowSoft
                    )
                }
            }

            items(allPackDescriptors, key = { it.packId }) { desc ->
                val cached = cachedMap[desc.packId]
                PopCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (cached != null) MintGreenSoft else PureWhite,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(desc.emoji, fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(desc.title, style = MaterialTheme.typography.titleSmall, color = DeepInk)
                            Text(
                                text = "适配 ${desc.minPlayers}-${desc.maxPlayers} 人 · ${desc.category}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CobaltBlue
                            )
                            Text(
                                text = if (cached != null) "离线就绪 · ${cached.itemCount}组题卡 (${cached.sizeBytes / 1024}KB)" else "未缓存 · 开局时自动按需加载",
                                style = MaterialTheme.typography.labelSmall,
                                color = DeepInk.copy(alpha = 0.75f)
                            )
                        }
                        if (cached != null) {
                            IconButton(
                                onClick = { onDeletePack(desc.packId) },
                                modifier = Modifier.testTag("vault_delete_pack_${desc.packId}")
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "清理缓存", tint = FlameOrange)
                            }
                        } else {
                            PopButton(
                                text = "预载",
                                onClick = { onPreloadPack(desc.packId) },
                                containerColor = SunburstYellow,
                                contentColor = DeepInk,
                                icon = Icons.Filled.Download,
                                modifier = Modifier.testTag("vault_preload_pack_${desc.packId}")
                            )
                        }
                    }
                }
            }

            // 3. Party Leaderboard & Match History
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🏆 本地聚会战绩榜", style = MaterialTheme.typography.titleMedium, color = DeepInk)
                    if (matchHistory.isNotEmpty()) {
                        PopTag(
                            text = "清空战绩",
                            containerColor = ElectricPinkSoft,
                            modifier = Modifier
                                .clickable(onClick = onClearHistory)
                                .testTag("vault_clear_history")
                        )
                    }
                }
            }

            if (matchHistory.isEmpty()) {
                item {
                    PopCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = PureWhite
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.EmojiEvents,
                                contentDescription = null,
                                tint = SunburstYellow,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                "还没有聚会战绩记录",
                                style = MaterialTheme.typography.titleMedium,
                                color = DeepInk
                            )
                            Text(
                                "回到「派对大厅」任意畅玩一局，MVP 与高光战绩会自动离线珍藏在这里！",
                                style = MaterialTheme.typography.bodyMedium,
                                color = DeepInk.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(matchHistory, key = { it.id }) { record ->
                    val timeStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(Date(record.playedAt))
                    PopCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = PureWhite,
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(if (record.localWon) SunburstYellow else ElectricPinkSoft)
                                        .border(2.dp, DeepInk, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(record.mvpEmoji, fontSize = 20.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "${record.gameTitle} · ${record.playerCount}人局",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = DeepInk
                                    )
                                    Text(
                                        "MVP: ${record.mvpName} · ${record.summaryNote}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CobaltBlue
                                    )
                                    Text(
                                        timeStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DeepInk.copy(alpha = 0.55f)
                                    )
                                }
                            }
                            PopTag(
                                text = "${record.mvpScore}分",
                                containerColor = if (record.localWon) MintGreen else SunburstYellowSoft
                            )
                        }
                    }
                }
            }
        }
    }
}
