package com.example.perfcontrol.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.perfcontrol.core.Severity
import kotlinx.coroutines.delay

@Composable
fun HealthTab(vm: AppViewModel) {
    val report by vm.health.collectAsState()
    val uptime by vm.uptimeHours.collectAsState()
    val alerts by vm.alertsEnabled.collectAsState()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setAlerts(true) else vm.notifyUser("لن تعمل التنبيهات بدون إذن الإشعارات")
    }

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshHealth()
            delay(3000)
        }
    }

    val battery by vm.batteryDetail.collectAsState()
    val capacity by vm.batteryCapacity.collectAsState()
    val chargeOn by vm.chargeAlertOn.collectAsState()
    val limit by vm.chargeLimit.collectAsState()
    val logOn by vm.logOn.collectAsState()
    val log by vm.logEntries.collectAsState()
    var range by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(1) }
    LaunchedEffect(Unit) { vm.loadBattery(); vm.loadLog() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item {
            HeroCard {
                Text("الصحة العامة", color = Color.White.copy(alpha = 0.75f))
                Text(
                    report?.let { "${it.label} · ${it.score}/100" } ?: "جارٍ الفحص...",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "مضى ${uptime / 24} يوم و${uptime % 24} ساعة على آخر تشغيل",
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
        report?.let { r ->
            if (r.issues.isEmpty()) {
                item { SoftCard { Text("لا توجد مشاكل", fontWeight = FontWeight.SemiBold); Caption("كل المؤشرات ضمن الحدود الطبيعية.") } }
            }
            items(r.issues, key = { it.key }) { issue ->
                val mark = when (issue.severity) {
                    Severity.Critical -> "✘ "
                    Severity.Warning -> "⚠ "
                    Severity.Info -> ""
                }
                SoftCard { Text(mark + issue.message) }
            }
        }

        item { SectionTitle("التنبيهات الذكية") }
        item {
            ToggleCard(
                "تنبيهات الجهاز",
                "إشعار عند ارتفاع الحرارة أو انخفاض الذاكرة أو امتلاء التخزين أو طول مدة التشغيل. الفحص كل 15 دقيقة تقريباً.",
                alerts,
                true,
            ) { on ->
                if (!on) {
                    vm.setAlerts(false)
                } else if (Build.VERSION.SDK_INT >= 33) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.setAlerts(true)
                }
            }
        }
        item { Caption("لضمان عملها في شاومي: الإعدادات ← التطبيقات ← متحكم الأداء ← توفير الطاقة ← بدون قيود.") }
        item { OutlinedButton(onClick = { vm.testAlert() }, modifier = Modifier.fillMaxWidth()) { Text("إرسال تنبيه تجريبي") } }
        item {
            OutlinedButton(onClick = { vm.official.openAppDetails(vm.selfPackage()) }, modifier = Modifier.fillMaxWidth()) {
                Text("فتح إعدادات التطبيق")
            }
        }

        item { SectionTitle("البطارية") }
        item {
            SoftCard {
                val b = battery
                if (b == null) {
                    Caption("تعذر قراءة بيانات البطارية.")
                } else {
                    Text("حالة البطارية: ${b.health}", fontWeight = FontWeight.SemiBold)
                    Caption("المستوى ${b.level}% · الحرارة ${"%.1f".format(b.tempC)}°C · الجهد ${b.voltageMv} mV · ${b.plugged}")
                    capacity?.let { Caption("السعة القصوى الحالية: $it% من سعة المصنع") }
                    b.cycles?.let { Caption("عدد دورات الشحن: $it") }
                    if (capacity == null && b.cycles == null) Caption("السعة وعدد الدورات لا تظهر إلا إذا سمح بها جهازك (تحتاج Shizuku جاهزاً أيضاً).")
                }
            }
        }
        item {
            ToggleCard(
                "تنبيه الشحن",
                "ينبّهك مرة واحدة عند بلوغ النسبة أدناه أثناء الشحن، لتفصل الشاحن وتحمي البطارية.",
                chargeOn,
                true,
            ) { vm.setChargeAlert(it) }
        }
        item {
            ChoiceRow(
                listOf("70%", "80%", "85%", "90%"),
                listOf(70, 80, 85, 90).indexOf(limit).takeIf { it >= 0 },
                true,
            ) { vm.setChargeLimit(listOf(70, 80, 85, 90)[it]) }
        }

        item { SectionTitle("سجل الأداء") }
        item {
            ToggleCard(
                "تسجيل الأداء تلقائياً",
                "يحفظ الحرارة والبطارية والذاكرة كل 15 دقيقة تقريباً لمدة 7 أيام، على جهازك فقط.",
                logOn,
                true,
            ) { vm.setLogEnabled(it) }
        }
        item {
            val now = System.currentTimeMillis()
            val windowMs = listOf(6L, 24L, 168L)[range] * 3600_000L
            val shown = com.example.perfcontrol.core.PerfLog.downsample(log.filter { now - it.ts <= windowMs }, 60)
            SoftCard {
                ChoiceRow(listOf("6 ساعات", "24 ساعة", "7 أيام"), range, true) { range = it }
                if (shown.size < 2) {
                    Caption("لا توجد بيانات كافية بعد. يتجمع السجل مع الوقت (نقطة كل 15 دقيقة)، وقد توقفه شاومي إن كانت بطارية التطبيق مقيّدة.")
                } else {
                    val sum = com.example.perfcontrol.core.PerfLog.summary(shown)!!
                    val fmt = java.text.SimpleDateFormat("d/M HH:mm", java.util.Locale.US)
                    Caption("أعلى حرارة ${"%.1f".format(sum.maxTemp)}°C عند ${fmt.format(java.util.Date(sum.maxTempAt))} · المتوسط ${"%.1f".format(sum.avgTemp)}°C · أدنى بطارية ${sum.minBattery}%")
                    LineChart("الحرارة", "%.1f°C".format(shown.last().tempC), shown.map { it.tempC }, (shown.maxOf { it.tempC }).coerceAtLeast(45f), Bad)
                    LineChart("البطارية", "${shown.last().batteryPct}%", shown.map { it.batteryPct.toFloat() }, 100f, Good)
                    LineChart("الذاكرة", "${shown.last().ramUsedPct}%", shown.map { it.ramUsedPct.toFloat() }, 100f, Warn)
                }
                OutlinedButton(onClick = { vm.clearLog() }, modifier = Modifier.fillMaxWidth()) { Text("مسح السجل") }
            }
        }
    }
}
