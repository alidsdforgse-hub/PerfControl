package com.example.perfcontrol.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.perfcontrol.core.ShizukuState
import com.example.perfcontrol.shizuku.ShizukuManager

private fun stateText(s: ShizukuState) = when (s) {
    ShizukuState.NotInstalled -> "Shizuku غير مثبّت على الجهاز."
    ShizukuState.NotRunning -> "Shizuku مثبّت لكنه لا يعمل. شغّله أولاً."
    ShizukuState.NoPermission -> "Shizuku يعمل، لكن التطبيق لم يحصل على الإذن بعد."
    ShizukuState.Ready -> "Shizuku جاهز."
}

@Composable
fun ShizukuBanner(state: ShizukuState, onSetup: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stateText(state),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onSetup) { Text("إعداد") }
        }
    }
}

@Composable
fun SetupScreen(state: ShizukuState, vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("إعداد Shizuku", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item { SoftCard { Text("الحالة", fontWeight = FontWeight.SemiBold); Text(stateText(state)) } }
        item {
            SoftCard {
                Text("الخطوات على جهاز شاومي (أندرويد 11 وما بعده)", fontWeight = FontWeight.SemiBold)
                Text(
                    "1) ثبّت تطبيق Shizuku.\n" +
                        "2) فعّل خيارات المطوّر، ثم فعّل \"التصحيح اللاسلكي\" واربط الجهاز من داخل Shizuku.\n" +
                        "3) في خيارات المطوّر فعّل أيضاً \"تصحيح USB (إعدادات الأمان)\"، وإلا ترفض واجهة شاومي بعض الأوامر.\n" +
                        "4) شغّل Shizuku، ثم اضغط \"طلب الإذن\" أدناه ووافق.",
                )
                Caption("بعد كل إعادة تشغيل للجهاز يلزم تشغيل Shizuku من جديد.")
            }
        }
        item {
            PrimaryButton("فتح Shizuku") {
                ctx.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?.let { runCatching { ctx.startActivity(it) } }
            }
        }
        item { PrimaryButton("طلب الإذن", enabled = state == ShizukuState.NoPermission) { vm.requestPermission() } }
        item { OutlinedButton(onClick = { vm.refreshShizuku() }, modifier = Modifier.fillMaxWidth()) { Text("تحديث الحالة") } }
        item { TextButton(onClick = onBack) { Text("رجوع") } }
    }
}

@Composable
fun SettingsTab(vm: AppViewModel, onSetup: () -> Unit) {
    val compat by vm.compat.collectAsState()
    val apps by vm.allUserApps.collectAsState()
    val excl by vm.exclusions.collectAsState()
    val state by vm.shizukuState.collectAsState()
    val theme by vm.themeMode.collectAsState()
    LaunchedEffect(Unit) { vm.loadExclusions() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item { SectionTitle("المظهر") }
        item {
            SoftCard {
                Text("وضع الألوان", fontWeight = FontWeight.SemiBold)
                ChoiceRow(listOf("تلقائي", "فاتح", "داكن"), theme, true) { vm.setTheme(it) }
            }
        }

        item { SectionTitle("Shizuku") }
        item {
            SoftCard {
                Text(stateText(state))
                OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) { Text("إعداد Shizuku") }
            }
        }

        item { SectionTitle("فحص التوافق") }
        item { Caption("يشغّل أوامر قراءة آمنة فقط (لا يوقف أي تطبيق) ليريك ما يعمل على جهازك.") }
        item {
            PrimaryButton(
                if (state == ShizukuState.Ready) "تشغيل الفحص" else "يحتاج Shizuku جاهزاً",
                enabled = state == ShizukuState.Ready,
            ) { vm.runCompat() }
        }
        compat?.let { list ->
            items(list) { c ->
                SoftCard {
                    Text((if (c.ok) "✔ " else "✘ ") + c.name, fontWeight = FontWeight.SemiBold)
                    Caption(c.command)
                    if (!c.ok) Caption(c.detail)
                }
            }
        }

        item { SectionTitle("التطبيقات المستثناة من الإيقاف") }
        item { Caption("التطبيقات المحدَّدة هنا لن تُوقَف أبداً (مثل تطبيقات المراسلة).") }
        items(apps, key = { it.pkg }) { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = app.pkg in excl, onCheckedChange = { vm.toggleExclusion(app.pkg, it) })
                Text(app.label)
            }
        }
        item { Caption("متحكم الأداء 1.2") }
    }
}
