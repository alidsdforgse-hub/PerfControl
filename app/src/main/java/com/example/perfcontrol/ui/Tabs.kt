package com.example.perfcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.perfcontrol.core.ShizukuState
import kotlinx.coroutines.delay

private fun mb(kb: Long) = "%.0f MB".format(kb / 1024.0)

@Composable
private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val listPadding = PaddingValues(16.dp)
private val listSpacing = Arrangement.spacedBy(12.dp)

@Composable
fun PerformanceTab(vm: AppViewModel) {
    val sample by vm.sample.collectAsState()
    val stats by vm.stats.collectAsState()
    var showBoost by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshSample()
            delay(1000)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item {
            Button(onClick = { showBoost = true }, modifier = Modifier.fillMaxWidth()) { Text("رفع الأداء") }
        }
        item {
            val cpu = sample?.cpuPercent?.let { "%.0f%%".format(it) } ?: "غير متاح (يحتاج Shizuku)"
            val freq = stats?.cpuFreqMhz?.let { "\nالتردد: $it MHz" } ?: ""
            InfoCard("المعالج", cpu + freq)
        }
        item {
            stats?.let {
                InfoCard("الذاكرة", "المتاح ${it.ramAvailMb} MB من ${it.ramTotalMb} MB")
            }
        }
        item {
            stats?.let {
                InfoCard("التخزين", "%.1f GB مستخدم من %.1f GB".format(it.storageUsedGb, it.storageTotalGb))
            }
        }
        item {
            stats?.let {
                val chg = if (it.charging) " (يشحن)" else ""
                InfoCard("البطارية", "${it.batteryPercent}%$chg\nالحرارة: ${"%.1f".format(it.tempC)} °C")
            }
        }
        item {
            stats?.let { InfoCard("الشبكة", "تنزيل ${it.rxKbps} KB/s\nرفع ${it.txKbps} KB/s") }
        }
        val top = sample?.processes?.sortedByDescending { it.cpuPercent }?.take(5).orEmpty()
        if (top.isNotEmpty()) {
            item { Text("أكثر التطبيقات استهلاكاً", style = MaterialTheme.typography.titleMedium) }
            items(top, key = { it.packageName }) { p ->
                val label = remember(p.packageName) { vm.device.label(p.packageName) }
                InfoCard(label, "المعالج %.1f%% — الذاكرة %s".format(p.cpuPercent, mb(p.rssKb)))
            }
        }
    }

    if (showBoost) {
        val plan = remember { vm.boostPlan() }
        AlertDialog(
            onDismissRequest = { showBoost = false },
            title = { Text("رفع الأداء") },
            text = {
                Text(
                    if (plan.isEmpty()) "لا توجد تطبيقات لإيقافها. هذه الميزة تحتاج Shizuku جاهزاً."
                    else "سيتم إيقاف ${plan.size} تطبيق وتنظيف الكاش:\n" +
                        plan.take(12).joinToString("\n") { vm.device.label(it) },
                )
            },
            confirmButton = {
                TextButton(enabled = plan.isNotEmpty(), onClick = { showBoost = false; vm.boost(plan) }) { Text("تأكيد") }
            },
            dismissButton = { TextButton(onClick = { showBoost = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
fun ProcessesTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val sample by vm.sample.collectAsState()
    val fallback by vm.fallbackApps.collectAsState()
    val hasAccess by vm.hasUsageAccess.collectAsState()
    var target by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        vm.loadFallback()
        if (state == ShizukuState.Ready) {
            while (true) {
                vm.refreshSample()
                delay(2000)
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        if (state == ShizukuState.Ready) {
            val procs = sample?.processes?.sortedByDescending { it.rssKb }.orEmpty()
            if (procs.isEmpty()) item { Text("جارٍ قراءة العمليات...") }
            items(procs, key = { it.packageName }) { p ->
                val label = remember(p.packageName) { vm.device.label(p.packageName) }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            Text("%s — المعالج %.1f%%".format(mb(p.rssKb), p.cpuPercent))
                        }
                        TextButton(onClick = { target = p.packageName }) { Text("إيقاف") }
                    }
                }
            }
        } else {
            item {
                InfoCard(
                    "وضع محدود",
                    "بدون Shizuku يعرض التطبيق التطبيقات الأكثر استخداماً فقط، ويفتح لك صفحة الإيقاف الإجباري الرسمية.",
                )
            }
            if (!hasAccess) {
                item {
                    Button(onClick = { vm.official.openUsageAccess() }, modifier = Modifier.fillMaxWidth()) {
                        Text("منح إذن بيانات الاستخدام")
                    }
                }
            }
            items(fallback, key = { it.pkg }) { app ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(app.label, Modifier.weight(1f))
                        TextButton(onClick = { vm.official.openAppDetails(app.pkg) }) { Text("فتح صفحة الإيقاف") }
                    }
                }
            }
        }
    }

    target?.let { pkg ->
        AlertDialog(
            onDismissRequest = { target = null },
            title = { Text("إيقاف التطبيق؟") },
            text = { Text("سيتم إيقاف ${vm.device.label(pkg)} إجبارياً.") },
            confirmButton = { TextButton(onClick = { vm.forceStop(pkg); target = null }) { Text("إيقاف") } },
            dismissButton = { TextButton(onClick = { target = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
fun CleanupTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val size by vm.ownCacheBytes.collectAsState()
    LaunchedEffect(Unit) { vm.loadCleanup() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item { InfoCard("كاش هذا التطبيق", "%.1f MB".format(size / 1024.0 / 1024.0)) }
        item {
            OutlinedButton(onClick = { vm.clearOwnCache() }, modifier = Modifier.fillMaxWidth()) {
                Text("تنظيف كاش هذا التطبيق")
            }
        }
        if (state == ShizukuState.Ready) {
            item {
                Button(onClick = { vm.trimAll() }, modifier = Modifier.fillMaxWidth()) {
                    Text("تنظيف كاش كل التطبيقات")
                }
            }
        } else {
            item {
                InfoCard("تنظيف كاش بقية التطبيقات", "يحتاج Shizuku. بدونه يمكنك فتح شاشة التخزين في النظام وتنظيفه من هناك.")
            }
            item {
                Button(onClick = { vm.official.openStorageSettings() }, modifier = Modifier.fillMaxWidth()) {
                    Text("فتح شاشة التخزين")
                }
            }
        }
    }
}

@Composable
fun StartupTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val apps by vm.startupApps.collectAsState()
    val restricted by vm.restricted.collectAsState()
    LaunchedEffect(Unit) { vm.loadStartup() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item {
            InfoCard(
                "عن هذه الصفحة",
                "التطبيقات التالية تطلب العمل عند تشغيل الجهاز. التقييد يمنعها من العمل في الخلفية (ضابط النظام). " +
                    "التشغيل التلقائي الخاص بشاومي يُضبط من تطبيق الأمان، وزر أدناه يفتحه لك.",
            )
        }
        item {
            OutlinedButton(onClick = { vm.official.openMiuiAutostart() }, modifier = Modifier.fillMaxWidth()) {
                Text("فتح التشغيل التلقائي (شاومي)")
            }
        }
        if (apps.isEmpty()) item { Text("لا توجد تطبيقات مطابقة.") }
        items(apps, key = { it.pkg }) { app ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, Modifier.weight(1f))
                    if (state == ShizukuState.Ready) {
                        Switch(checked = app.pkg in restricted, onCheckedChange = { vm.setRestricted(app.pkg, it) })
                    } else {
                        TextButton(onClick = { vm.official.openAppDetails(app.pkg) }) { Text("فتح") }
                    }
                }
            }
        }
    }
}
