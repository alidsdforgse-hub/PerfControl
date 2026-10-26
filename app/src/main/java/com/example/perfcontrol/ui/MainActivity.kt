package com.example.perfcontrol.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.perfcontrol.PerfApp
import com.example.perfcontrol.core.ShizukuState
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    var crash by remember { mutableStateOf(readCrash()) }
                    val text = crash
                    if (text != null) {
                        CrashScreen(text) {
                            File(filesDir, PerfApp.CRASH_FILE).delete()
                            crash = null
                        }
                    } else {
                        AppRoot(vm)
                    }
                }
            }
        }
    }

    private fun readCrash(): String? =
        runCatching { File(filesDir, PerfApp.CRASH_FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    override fun onResume() {
        super.onResume()
        vm.refreshShizuku()
        vm.loadFallback()
    }
}

private enum class Screen { Main, Setup, Settings }
private data class TabItem(val title: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("الأداء", Icons.Default.Home),
    TabItem("العمليات", Icons.Default.Info),
    TabItem("التنظيف", Icons.Default.Delete),
    TabItem("بدء التشغيل", Icons.Default.PlayArrow),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf(Screen.Main) }
    val state by vm.shizukuState.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snack.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("متحكم الأداء") },
                actions = {
                    IconButton(onClick = { screen = Screen.Settings }) {
                        Icon(Icons.Default.Settings, contentDescription = "الإعدادات")
                    }
                },
            )
        },
        bottomBar = {
            if (screen == Screen.Main) {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.title) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (screen) {
                Screen.Setup -> SetupScreen(state, vm) { screen = Screen.Main }
                Screen.Settings -> SettingsScreen(vm) { screen = Screen.Main }
                Screen.Main -> {
                    if (state != ShizukuState.Ready) ShizukuBanner(state) { screen = Screen.Setup }
                    when (tab) {
                        0 -> PerformanceTab(vm)
                        1 -> ProcessesTab(vm)
                        2 -> CleanupTab(vm)
                        else -> StartupTab(vm)
                    }
                }
            }
        }
    }
}

@Composable
fun CrashScreen(trace: String, onContinue: () -> Unit) {
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("حدث خطأ في المرة السابقة. صوّر هذه الشاشة وأرسلها للمطوّر:")
        Text(trace)
        Button(onClick = onContinue) { Text("متابعة") }
    }
}
