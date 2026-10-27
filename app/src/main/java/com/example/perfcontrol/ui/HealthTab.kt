package com.example.perfcontrol.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import com.example.perfcontrol.core.Severity
import com.example.perfcontrol.core.ShizukuState
import kotlinx.coroutines.delay

@Composable
fun HealthTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val report by vm.health.collectAsState()
    val heavy by vm.heavy.collectAsState()
    val uptime by vm.uptimeHours.collectAsState()
    val alerts by vm.alertsEnabled.collectAsState()
    var confirmAll by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setAlerts(true) else vm.notifyUser("لن تعمل التنبيهات بدون إذن الإشعارات")
    }

    LaunchedEffect(state) {
        while (true) {
            vm.refreshHealth()
            delay(3000)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item {
            val r = report
            if (r == null) {
                Text("جارٍ الفحص...")
            } else {
                InfoCard(
                    "حالة الجهاز: ${r.label} (${r.score}/100)",
                    "مضى ${uptime / 24} يوم و${uptime % 24} ساعة على آخر تشغيل",
                )
            }
        }
        report?.let { r ->
            if (r.issues.isEmpty()) {
                item { InfoCard("لا توجد مشاكل", "كل المؤشرات ضمن الحدود الطبيعية.") }
            }
            items(r.issues, key = { it.key }) { issue ->
                val mark = when (issue.severity) {
                    Severity.Critical -> "✘ "
                    Severity.Warning -> "⚠ "
                    Severity.Info -> ""
                }
                InfoCard(mark + issue.message, "")
            }
        }

        item { Text("التطبيقات الثقيلة", style = MaterialTheme.typography.titleLarge) }
        if (state != ShizukuState.Ready) {
            item { InfoCard("يحتاج Shizuku", "لا يمكن قراءة استهلاك التطبيقات بدونه.") }
        } else if (heavy.isEmpty()) {
            item { InfoCard("لا توجد تطبيقات ثقيلة", "لا يوجد تطبيق يتجاوز 400 ميغابايت أو 10% من المعالج الآن.") }
        } else {
            item {
                Button(onClick = { confirmAll = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("إيقاف كل التطبيقات الثقيلة (${heavy.size})")
                }
            }
            items(heavy, key = { it.packageName }) { h ->
                val label = remember(h.packageName) { vm.device.label(h.packageName) }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            Text("${h.reason} — ${"%.0f".format(h.rssKb / 1024.0)} MB — ${"%.1f".format(h.cpuPercent)}%")
                        }
                        TextButton(onClick = { vm.stopHeavy(listOf(h.packageName)) }) { Text("إيقاف") }
                    }
                }
            }
        }

        item { Text("التنبيهات الذكية", style = MaterialTheme.typography.titleLarge) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تنبيهني عند ارتفاع الحرارة أو انخفاض الذاكرة أو امتلاء التخزين أو طول مدة التشغيل", Modifier.weight(1f))
                        Switch(
                            checked = alerts,
                            onCheckedChange = { on ->
                                if (!on) {
                                    vm.setAlerts(false)
                                } else if (Build.VERSION.SDK_INT >= 33) {
                                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    vm.setAlerts(true)
                                }
                            },
                        )
                    }
                    Text(
                        "الفحص كل 15 دقيقة تقريباً. لضمان عمله في شاومي: الإعدادات ← التطبيقات ← متحكم الأداء ← توفير الطاقة ← بدون قيود.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = { vm.testAlert() }, modifier = Modifier.fillMaxWidth()) { Text("إرسال تنبيه تجريبي") }
        }
        item {
            OutlinedButton(onClick = { vm.official.openAppDetails(vm.selfPackage()) }, modifier = Modifier.fillMaxWidth()) {
                Text("فتح إعدادات التطبيق (توفير الطاقة)")
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
