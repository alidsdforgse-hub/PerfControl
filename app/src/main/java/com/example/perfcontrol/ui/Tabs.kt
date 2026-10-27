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
internal fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

internal val listPadding = PaddingValues(16.dp)
internal val listSpacing = Arrangement.spacedBy(12.dp)

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
