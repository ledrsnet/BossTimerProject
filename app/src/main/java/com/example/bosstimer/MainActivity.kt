package com.example.bosstimer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

data class Boss(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Boss",
    var intervalMinutes: Int = 5,
    var remainingMillis: Long? = null,
    var lastStartTimestamp: Long? = null
) {
    fun nextSpawnTime(): Long? {
        val rem = remainingMillis ?: return null
        return System.currentTimeMillis() + rem
    }
}

class BossRepository(private val storageFile: File) {
    private val gson = Gson()

    fun loadAll(): MutableList<Boss> {
        if (!storageFile.exists()) return mutableListOf()
        return try {
            val text = storageFile.readText()
            val typ = object : TypeToken<MutableList<Boss>>() {}.type
            gson.fromJson<MutableList<Boss>>(text, typ) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveAll(list: List<Boss>) {
        try {
            storageFile.writeText(gson.toJson(list))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class TimerEngine(private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<Map<String, Long>>(emptyMap())
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun start(bosses: List<Boss>) {
        stop()
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val map = mutableMapOf<String, Long>()
                for (b in bosses) {
                    val rem = b.remainingMillis
                    if (rem != null && rem > 0) {
                        val newRem = max(0L, rem - 1000L)
                        b.remainingMillis = newRem
                        b.lastStartTimestamp = now
                        map[b.id] = newRem
                    }
                }
                _state.value = map
                delay(1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = emptyMap()
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var repo: BossRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = TimerEngine(scope)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = File(filesDir, "bosses.json")
        repo = BossRepository(file)
        val initial = repo.loadAll()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    BossTimerApp(initial, repo, engine, scope)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.stop()
        scope.cancel()
    }
}

@Composable
fun BossTimerApp(initial: MutableList<Boss>, repo: BossRepository, engine: TimerEngine, scope: CoroutineScope) {
    var bosses by remember { mutableStateOf(initial) }
    val nowFlow = remember { mutableStateOf(System.currentTimeMillis()) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            nowFlow.value = System.currentTimeMillis()
            delay(1000L)
        }
    }

    LaunchedEffect(engine) {
        engine.state.collect { map ->
            bosses = bosses.map { b ->
                if (map.containsKey(b.id)) b.copy(remainingMillis = map[b.id]) else b
            }.toMutableList()
        }
    }

    fun persist() { repo.saveAll(bosses) }

    fun startTimer(b: Boss) {
        if (b.remainingMillis == null || b.remainingMillis == 0L) {
            b.remainingMillis = b.intervalMinutes * 60L * 1000L
        }
        b.lastStartTimestamp = System.currentTimeMillis()
        persist()
        engine.start(bosses)
    }

    fun stopTimer(b: Boss) {
        b.remainingMillis = null
        b.lastStartTimestamp = null
        persist()
        if (bosses.none { it.remainingMillis != null && it.remainingMillis!! > 0 }) {
            engine.stop()
        }
    }

    fun deleteTimerOnly(b: Boss) {
        b.remainingMillis = null
        b.lastStartTimestamp = null
        persist()
        if (bosses.none { it.remainingMillis != null && it.remainingMillis!! > 0 }) {
            engine.stop()
        }
    }

    fun addBoss(name: String, interval: Int) {
        bosses = (bosses + Boss(name = name, intervalMinutes = interval)).toMutableList()
        persist()
    }

    fun clearAllTimers() {
        bosses.forEach {
            it.remainingMillis = null
            it.lastStartTimestamp = null
        }
        persist()
        engine.stop()
    }

    val sorted = bosses.sortedWith(compareBy<Boss> { it.remainingMillis ?: Long.MAX_VALUE }.thenBy { it.name })

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("BOSS计时器", fontSize = 20.sp) })
        },
        content = { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("当前时间: ${formatTime(nowFlow.value)}")
                    Row {
                        Button(onClick = { clearAllTimers() }, modifier = Modifier.height(36.dp)) {
                            Text("清空计时")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { showAdd = true }, modifier = Modifier.height(36.dp)) {
                            Text("添加Boss")
                        }
                    }
                }

                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text("BOSS", modifier = Modifier.weight(2f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("间隔", modifier = Modifier.weight(1f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("剩余", modifier = Modifier.weight(1f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("刷新", modifier = Modifier.weight(1f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("操作", modifier = Modifier.weight(1f), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }

                Divider()

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(sorted) { index, boss ->
                        BossRow(
                            boss = boss,
                            now = nowFlow.value,
                            onStart = { startTimer(boss); persist() },
                            onStop = { stopTimer(boss); persist() },
                            onDeleteTimer = { deleteTimerOnly(boss); persist() },
                            onRemove = {
                                bosses = bosses.toMutableList().also { it.remove(boss) }
                                persist()
                            }
                        )
                        Divider()
                    }
                }
            }
        }
    )

    if (showAdd) {
        AddBossDialog(onAdd = { name, interval -> addBoss(name, interval); showAdd = false }, onCancel = { showAdd = false })
    }
}

@Composable
fun BossRow(
    boss: Boss,
    now: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDeleteTimer: () -> Unit,
    onRemove: () -> Unit
) {
    val remaining = boss.remainingMillis ?: 0L
    val remText = if (boss.remainingMillis == null) "--:--" else formatDuration(remaining)
    val nextTime = boss.nextSpawnTime()?.let { formatTime(it) } ?: "--:--"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(boss.name, modifier = Modifier.weight(2f), fontSize = 14.sp)
        Text("${boss.intervalMinutes}m", modifier = Modifier.weight(1f), fontSize = 14.sp)
        Text(remText, modifier = Modifier.weight(1f), fontSize = 14.sp, color = if (boss.remainingMillis!=null && boss.remainingMillis!! <= 60_000L) Color.Red else Color.Unspecified)
        Text(nextTime, modifier = Modifier.weight(1f), fontSize = 12.sp)
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            if (boss.remainingMillis == null || boss.remainingMillis == 0L) {
                TextButton(onClick = onStart, modifier = Modifier.height(28.dp)) { Text("开始", fontSize = 13.sp) }
            } else {
                TextButton(onClick = onStop, modifier = Modifier.height(28.dp)) { Text("停止", fontSize = 13.sp) }
            }
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = onDeleteTimer, modifier = Modifier.height(28.dp)) { Text("删除计时", fontSize = 12.sp) }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onRemove) {
                Icon(imageVector = androidx.compose.material.icons.Icons.Default.Delete, contentDescription = "删除Boss")
            }
        }
    }
}

@Composable
fun AddBossDialog(onAdd: (String, Int) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("5") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("添加 Boss") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") })
                OutlinedTextField(value = interval, onValueChange = { interval = it }, label = { Text("间隔(分钟)") })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val iv = interval.toIntOrNull() ?: 5
                if (name.isBlank()) return@TextButton
                onAdd(name.trim(), iv)
            }) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        }
    )
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val m = (s / 60) % 60
    val sec = s % 60
    return String.format("%02d:%02d", m, sec)
}

fun formatTime(ms: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
}
