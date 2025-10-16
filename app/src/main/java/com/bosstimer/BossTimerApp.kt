package com.bosstimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.time.Duration.Companion.minutes

// =================================================================================
// 1. Data Model & Logic (Boss and ViewModel)
// =================================================================================

/**
 * Boss 数据模型
 * @param id 唯一标识符
 * @param name Boss 名称
 * @param intervalMinutes 刷新间隔（分钟）
 * @param lastKilledTime 上次击杀时间（Unix 毫秒时间戳）
 * @param notes 备注
 */
data class Boss(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val intervalMinutes: Int,
    val lastKilledTime: Long = 0L,
    val notes: String = ""
) {
    /** 下次刷新时间（Unix 毫秒时间戳） */
    val nextRefreshTime: Long
        get() = lastKilledTime + intervalMinutes.minutes.inWholeMilliseconds

    /** 距离下次刷新剩余时间（毫秒），始终为正值或 0 */
    val remainingTimeMillis: Long
        get() {
            val remaining = nextRefreshTime - System.currentTimeMillis()
            return maxOf(0L, remaining)
        }
}

/**
 * 应用状态和业务逻辑管理
 * 实际项目中应将数据持久化逻辑（如 Room 或 SharedPreferences）放在此处
 */
class BossTimerViewModel : ViewModel() {
    // 使用 SnapshotStateList 确保 Compose 能够响应列表内部的变化
    private val _bosses = mutableStateListOf<Boss>()
    // 排序后的 Boss 列表，供 UI 订阅
    private val _sortedBosses = MutableStateFlow<List<Boss>>(emptyList())
    val sortedBosses: StateFlow<List<Boss>> = _sortedBosses.asStateFlow()

    // 计时器 Job，用于控制计时的开始/停止
    private var timerJob: Job? = null

    init {
        // 初始化示例数据
        _bosses.addAll(
            listOf(
                Boss(name = "世界Boss", intervalMinutes = 120, lastKilledTime = System.currentTimeMillis() - 60.minutes.inWholeMilliseconds, notes = "周六限定"),
                Boss(name = "野外Boss A", intervalMinutes = 30, lastKilledTime = System.currentTimeMillis() - 10.minutes.inWholeMilliseconds),
                Boss(name = "副本Boss Z", intervalMinutes = 60),
            )
        )
        // 启动时立即排序并开始计时器
        sortAndPublishBosses()
        startTimer()
    }

    /**
     * 添加新的 Boss 或更新现有 Boss
     */
    fun addOrUpdateBoss(boss: Boss) {
        val index = _bosses.indexOfFirst { it.id == boss.id }
        if (index != -1) {
            _bosses[index] = boss
        } else {
            _bosses.add(boss)
        }
        // 任何数据变化都需要重新排序和发布
        sortAndPublishBosses()
    }

    /**
     * 删除 Boss
     */
    fun deleteBoss(boss: Boss) {
        _bosses.remove(boss)
        sortAndPublishBosses()
    }

    /**
     * 记录 Boss 被击杀，更新上次击杀时间为当前时间
     */
    fun recordKill(boss: Boss) {
        val updatedBoss = boss.copy(lastKilledTime = System.currentTimeMillis())
        addOrUpdateBoss(updatedBoss)
    }

    /**
     * 排序 Boss 列表并发布给 UI
     * 排序规则：按剩余倒计时时间升序 (剩余时间少的排在前面)
     */
    private fun sortAndPublishBosses() {
        val sortedList = _bosses.sortedWith(compareBy(Boss::remainingTimeMillis))
        _sortedBosses.value = sortedList
    }

    /**
     * 启动计时器：每秒更新一次 Boss 列表，以刷新倒计时
     */
    fun startTimer() {
        if (timerJob?.isActive == true) return // 避免重复启动
        timerJob = viewModelScope.launch {
            while (true) {
                // 每秒重新计算所有 Boss 的剩余时间并重新排序
                sortAndPublishBosses()
                delay(1000L) // 暂停 1 秒
            }
        }
    }

    /**
     * 暂停计时器
     */
    fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * 重置所有 Boss 的计时（将 lastKilledTime 设为 0）
     */
    fun resetAllTimers() {
        _bosses.replaceAll { it.copy(lastKilledTime = 0L) }
        sortAndPublishBosses()
    }

    // 确保 ViewModel 销毁时停止计时器
    override fun onCleared() {
        super.onCleared()
        pauseTimer()
    }
}

// =================================================================================
// 2. UI Components (Compose)
// =================================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BossTimerTheme {
                BossTimerAppScreen()
            }
        }
    }
}

/**
 * 主应用屏幕
 */
@Composable
fun BossTimerAppScreen(viewModel: BossTimerViewModel = viewModel()) {
    val bosses by viewModel.sortedBosses.collectAsState()
    var showAddDialog by remember { mutableStateOf<Boss?>(null) } // null: 关闭, Boss: 编辑/新建

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Boss 刷新计时器", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                actions = {
                    IconButton(onClick = { viewModel.pauseTimer() }) {
                        Icon(Icons.Filled.Pause, contentDescription = "暂停计时")
                    }
                    IconButton(onClick = { viewModel.startTimer() }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "开始计时")
                    }
                    IconButton(onClick = { viewModel.resetAllTimers() }) {
                        Icon(Icons.Filled.Restore, contentDescription = "重置全部")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = Boss(name = "", intervalMinutes = 60) },
                icon = { Icon(Icons.Filled.Add, contentDescription = "添加Boss") },
                text = { Text("添加 Boss") }
            )
        }
    ) { padding ->
        BossList(
            bosses = bosses,
            onKill = viewModel::recordKill,
            onEdit = { showAddDialog = it },
            onDelete = viewModel::deleteBoss,
            modifier = Modifier.padding(padding)
        )

        // 添加/编辑 Boss 弹窗
        showAddDialog?.let { bossToEdit ->
            AddEditBossDialog(
                initialBoss = bossToEdit,
                onDismiss = { showAddDialog = null },
                onConfirm = {
                    viewModel.addOrUpdateBoss(it)
                    showAddDialog = null
                }
            )
        }
    }
}

/**
 * Boss 列表
 */
@Composable
fun BossList(
    bosses: List<Boss>,
    onKill: (Boss) -> Unit,
    onEdit: (Boss) -> Unit,
    onDelete: (Boss) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bosses.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无 Boss，点击右下角按钮添加！", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 8.dp),
        contentPadding = PaddingValues(bottom = 80.dp) // 避免 FAB 遮挡
    ) {
        items(bosses, key = { it.id }) { boss ->
            BossItem(
                boss = boss,
                onKill = onKill,
                onEdit = onEdit,
                onDelete = onDelete,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

/**
 * 单个 Boss 列表项
 */
@Composable
fun BossItem(
    boss: Boss,
    onKill: (Boss) -> Unit,
    onEdit: (Boss) -> Unit,
    onDelete: (Boss) -> Unit,
    modifier: Modifier = Modifier
) {
    val remaining = boss.remainingTimeMillis
    val isReady = remaining <= 0L
    val color = when {
        isReady -> MaterialTheme.colorScheme.error // 红色，已刷新
        remaining < 10.minutes.inWholeMilliseconds -> MaterialTheme.colorScheme.tertiary // 橙色，即将刷新
        else -> MaterialTheme.colorScheme.primary // 蓝色，正常计时
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onKill(boss) }, // 点击卡片即记录击杀
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Boss 名称和备注
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = boss.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
                if (boss.notes.isNotBlank()) {
                    Text(
                        text = boss.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // 计时和操作区
            Column(horizontalAlignment = Alignment.End) {
                // 剩余时间或已刷新提示
                AnimatedVisibility(
                    visible = isReady,
                    enter = fadeIn(tween(500)),
                    exit = fadeOut(tween(500))
                ) {
                    Text(
                        text = "已刷新！",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                AnimatedVisibility(
                    visible = !isReady,
                    enter = fadeIn(tween(500)),
                    exit = fadeOut(tween(500))
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        // 倒计时显示 (HH:MM:SS)
                        Text(
                            text = formatDuration(remaining),
                            color = color,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        // 下次刷新时间
                        Text(
                            text = "刷新: ${formatTime(boss.nextRefreshTime)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 操作按钮
                Row {
                    IconButton(onClick = { onEdit(boss) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { onDelete(boss) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * 添加/编辑 Boss 弹窗
 */
@Composable
fun AddEditBossDialog(
    initialBoss: Boss,
    onDismiss: () -> Unit,
    onConfirm: (Boss) -> Unit
) {
    var name by remember { mutableStateOf(initialBoss.name) }
    var interval by remember { mutableStateOf(initialBoss.intervalMinutes.toString()) }
    var notes by remember { mutableStateOf(initialBoss.notes) }
    val isEditMode = initialBoss.lastKilledTime != 0L || initialBoss.name.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (isEditMode && initialBoss.name.isNotBlank()) "编辑 Boss" else "添加 Boss",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Boss 名称 (必填)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = interval,
                    onValueChange = { interval = it.filter { char -> char.isDigit() } },
                    label = { Text("刷新间隔 (分钟, 必填)") },
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注 (可选)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val intervalInt = interval.toIntOrNull() ?: 0
                            if (name.isNotBlank() && intervalInt > 0) {
                                onConfirm(initialBoss.copy(
                                    name = name,
                                    intervalMinutes = intervalInt,
                                    notes = notes
                                ))
                            }
                        },
                        enabled = name.isNotBlank() && (interval.toIntOrNull() ?: 0) > 0
                    ) {
                        Text(if (isEditMode) "保存" else "添加")
                    }
                }
            }
        }
    }
}

// =================================================================================
// 3. Utility Functions
// =================================================================================

/**
 * 将毫秒数格式化为 HH:MM:SS 格式的倒计时
 */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return String.format("%02d:%02d:%02d", hours, minutes, seconds)
}

/**
 * 将 Unix 毫秒时间戳格式化为 HH:mm:ss 格式的时间
 */
private fun formatTime(millis: Long): String {
    if (millis <= 0) return "N/A"
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(millis))
}


// =================================================================================
// 4. Compose Material3 Theme
// =================================================================================

@Composable
fun BossTimerTheme(
    content: @Composable () -> Unit
) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF673AB7), // Deep Purple
        secondary = Color(0xFFFF5722), // Deep Orange
        tertiary = Color(0xFFFFC107), // Amber (for warning)
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        error = Color(0xFFF44336), // Red (for ready boss)
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography(),
        content = content
    )
}
