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
    }
}
