package com.example.perfcontrol.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stateText(state), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onSetup) { Text("إعداد") }
        }
    }
}

@Composable
fun SetupScreen(state: ShizukuState, vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("إعداد Shizuku", style = MaterialTheme.typography.titleLarge) }
        item { Text("الحالة: " + stateText(state)) }
        item {
            Text(
                "الخطوات على جهاز شاومي (أندرويد 11 وما بعده):\n" +
                    "1) ثبّت تطبيق Shizuku.\n" +
                    "2) فعّل خيارات المطوّر، ثم فعّل \"التصحيح اللاسلكي\" واربط الجهاز من داخل Shizuku.\n" +
                    "3) في خيارات المطوّر فعّل أيضاً \"تصحيح USB (إعدادات الأمان)\"، وإلا ترفض واجهة شاومي بعض الأوامر.\n" +
                    "4) شغّل Shizuku، ثم اضغط \"طلب الإذن\" أدناه ووافق.\n" +
                    "ملاحظة: بعد كل إعادة تشغيل للجهاز يلزم تشغيل Shizuku من جديد.",
            )
        }
        item {
            Button(
                onClick = {
                    ctx.packageManager.getLaunchIntentForPackage(ShizukuManager.SHIZUKU_PACKAGE)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.let { runCatching { ctx.startActivity(it) } }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("فتح Shizuku") }
        }
        item {
            Button(
                onClick = { vm.requestPermission() },
                enabled = state == ShizukuState.NoPermission,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("طلب الإذن") }
        }
        item { OutlinedButton(onClick = { vm.refreshShizuku() }, modifier = Modifier.fillMaxWidth()) { Text("تحديث الحالة") } }
        item { TextButton(onClick = onBack) { Text("رجوع") } }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val compat by vm.compat.collectAsState()
    val apps by vm.allUserApps.collectAsState()
    val excl by vm.exclusions.collectAsState()
    val state by vm.shizukuState.collectAsState()
    LaunchedEffect(Unit) { vm.loadExclusions() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Text("رجوع") } }
        item { Text("فحص التوافق", style = MaterialTheme.typography.titleLarge) }
        item {
            Text("يشغّل أوامر قراءة آمنة فقط (لا يوقف أي تطبيق) ليريك ما يعمل على جهازك.")
        }
        item {
            Button(
                onClick = { vm.runCompat() },
                enabled = state == ShizukuState.Ready,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state == ShizukuState.Ready) "تشغيل الفحص" else "يحتاج Shizuku جاهزاً") }
        }
        compat?.let { list ->
            items(list) { c ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text((if (c.ok) "✔ " else "✘ ") + c.name, style = MaterialTheme.typography.titleSmall)
                        Text(c.command, style = MaterialTheme.typography.bodySmall)
                        if (!c.ok) Text(c.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Text("التطبيقات المستثناة من الإيقاف", style = MaterialTheme.typography.titleLarge) }
        item { Text("التطبيقات المحدَّدة هنا لن تُوقَف أبداً (مثل تطبيقات المراسلة).") }
        items(apps, key = { it.pkg }) { app ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = app.pkg in excl, onCheckedChange = { vm.toggleExclusion(app.pkg, it) })
                Text(app.label)
            }
        }
    }
}
