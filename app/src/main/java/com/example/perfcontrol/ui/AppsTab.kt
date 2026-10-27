package com.example.perfcontrol.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.perfcontrol.core.ShizukuState
import kotlinx.coroutines.delay

@Composable
fun AppsTab(vm: AppViewModel) {
    var sub by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = sub, edgePadding = 0.dp) {
            listOf("العمليات", "الثقيلة", "الإقلاع", "التجميد", "الإشعارات").forEachIndexed { i, t ->
                Tab(selected = sub == i, onClick = { sub = i }, text = { Text(t) })
            }
        }
        when (sub) {
            0 -> ProcessesTab(vm)
            1 -> HeavyTab(vm)
            2 -> StartupTab(vm)
            3 -> FreezeTab(vm)
            else -> ProtectTab(vm)
        }
    }
}

@Composable
fun HeavyTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val heavy by vm.heavy.collectAsState()
    var confirmAll by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        while (true) {
            vm.refreshSample()
            delay(2500)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        if (state != ShizukuState.Ready) {
            item { SoftCard { Text("يحتاج Shizuku", fontWeight = FontWeight.SemiBold); Caption("لا يمكن قراءة استهلاك التطبيقات بدونه.") } }
        } else if (heavy.isEmpty()) {
            item {
                SoftCard {
                    Text("لا توجد تطبيقات ثقيلة", fontWeight = FontWeight.SemiBold)
                    Caption("لا يوجد تطبيق يتجاوز 400 ميغابايت أو 10% من المعالج الآن.")
                }
            }
        } else {
            item { PrimaryButton("إيقاف كل التطبيقات الثقيلة (${heavy.size})") { confirmAll = true } }
            items(heavy, key = { it.packageName }) { h ->
                val label = remember(h.packageName) { vm.device.label(h.packageName) }
                SoftCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.SemiBold)
                            Caption("${h.reason} — ${"%.0f".format(h.rssKb / 1024.0)} MB — ${"%.1f".format(h.cpuPercent)}%")
                        }
                        TextButton(onClick = { vm.stopHeavy(listOf(h.packageName)) }) { Text("إيقاف") }
                    }
                }
            }
        }
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("إيقاف التطبيقات الثقيلة؟") },
            text = { Text(heavy.joinToString("\n") { vm.device.label(it.packageName) }) },
            confirmButton = {
                TextButton(onClick = { confirmAll = false; vm.stopHeavy(heavy.map { it.packageName }) }) { Text("إيقاف") }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
fun FreezeTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val apps by vm.allUserApps.collectAsState()
    val frozen by vm.frozen.collectAsState()
    var pending by remember { mutableStateOf<String?>(null) }
    val ready = state == ShizukuState.Ready

    LaunchedEffect(state) { vm.loadFreeze() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item {
            SoftCard {
                Text("تجميد التطبيقات", fontWeight = FontWeight.SemiBold)
                Caption("التطبيق المجمّد يُعطَّل بالكامل: لا يعمل ولا يظهر في الشاشة الرئيسية ولا يستهلك شيئاً. يمكنك إعادته بزر واحد.")
                if (!ready) Caption("يحتاج Shizuku جاهزاً.")
            }
        }
        if (frozen.isNotEmpty()) {
            item { SectionTitle("المجمّدة الآن (${frozen.size})") }
            items(frozen.toList(), key = { "f_" + it }) { pkg ->
                val label = remember(pkg) { vm.device.label(pkg) }
                SoftCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.SemiBold)
                            Caption(pkg)
                        }
                        TextButton(enabled = ready, onClick = { vm.setFrozen(pkg, false) }) { Text("إعادة") }
                    }
                }
            }
        }
        item { SectionTitle("تطبيقاتك") }
        items(apps.filter { it.pkg !in frozen }, key = { "a_" + it.pkg }) { app ->
            SoftCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.SemiBold)
                        Caption(app.pkg)
                    }
                    TextButton(enabled = ready, onClick = { pending = app.pkg }) { Text("تجميد") }
                }
            }
        }
    }

    pending?.let { pkg ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("تجميد ${vm.device.label(pkg)}؟") },
            text = { Text("سيتوقف التطبيق ويختفي حتى تعيده من هذه الشاشة. لا يُنصح بتجميد تطبيق تنتظر إشعاراته.") },
            confirmButton = { TextButton(onClick = { pending = null; vm.setFrozen(pkg, true) }) { Text("تجميد") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
fun ProtectTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val apps by vm.allUserApps.collectAsState()
    val prot by vm.notifProtected.collectAsState()
    val ready = state == ShizukuState.Ready

    LaunchedEffect(state) { vm.loadProtect() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item {
            SoftCard {
                Text("حماية الإشعارات", fontWeight = FontWeight.SemiBold)
                Caption("اختر التطبيقات المهمة (مثل واتساب). لن يوقفها رفع الأداء، ولن يقتلها توفير الطاقة العميق، فتصلك إشعاراتها دائماً.")
                if (!ready) Caption("يحتاج Shizuku جاهزاً.")
            }
        }
        items(apps, key = { it.pkg }) { app ->
            ToggleCard(app.label, app.pkg, app.pkg in prot, ready) { vm.setNotifProtected(app.pkg, it) }
        }
    }
}
