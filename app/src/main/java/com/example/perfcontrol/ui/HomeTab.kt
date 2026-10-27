package com.example.perfcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.perfcontrol.core.ShizukuState
import kotlinx.coroutines.delay

@Composable
fun HomeTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val sample by vm.sample.collectAsState()
    val stats by vm.stats.collectAsState()
    val report by vm.health.collectAsState()
    val gameOn by vm.gameEnabled.collectAsState()
    val saveOn by vm.powerSaveEnabled.collectAsState()
    val hist by vm.history.collectAsState()
    var showBoost by remember { mutableStateOf(false) }
    val ready = state == ShizukuState.Ready

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshSample()
            vm.refreshHealth()
            delay(1500)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item {
            HeroCard {
                Text("متحكم الأداء", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    report?.let { "حالة الجهاز: ${it.label}" } ?: "جارٍ الفحص...",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                report?.let { Text("${it.score} من 100", color = Color.White.copy(alpha = 0.75f)) }

                val cpu = sample?.cpuPercent
                val s = stats
                val ramUsed = s?.let { 100 - (it.ramAvailMb * 100 / it.ramTotalMb.coerceAtLeast(1)).toInt() } ?: 0
                val stoUsed = s?.let { (it.storageUsedGb * 100 / it.storageTotalGb.coerceAtLeast(0.1)).toInt() } ?: 0
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    GaugeRing(
                        progress = ((cpu ?: 0.0) / 100.0).toFloat(),
                        value = cpu?.let { "%.0f%%".format(it) } ?: "—",
                        label = "المعالج",
                        textColor = Color.White,
                        trackColor = Color.White.copy(alpha = 0.18f),
                    )
                    GaugeRing(
                        progress = ramUsed / 100f,
                        value = "$ramUsed%",
                        label = "الذاكرة",
                        textColor = Color.White,
                        trackColor = Color.White.copy(alpha = 0.18f),
                    )
                    GaugeRing(
                        progress = stoUsed / 100f,
                        value = "$stoUsed%",
                        label = "التخزين",
                        textColor = Color.White,
                        trackColor = Color.White.copy(alpha = 0.18f),
                    )
                }
                if (!ready) {
                    Text("نسبة المعالج تحتاج Shizuku", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            val s = stats
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "البطارية",
                    s?.let { "${it.batteryPercent}%" } ?: "—",
                    s?.let { "${"%.1f".format(it.tempC)}°C" + if (it.charging) " · يشحن" else "" } ?: "",
                    Modifier.weight(1f),
                )
                StatTile(
                    "الشبكة",
                    s?.let { "↓ ${it.rxKbps} KB/s" } ?: "—",
                    s?.let { "↑ ${it.txKbps} KB/s" } ?: "",
                    Modifier.weight(1f),
                )
            }
        }
        item { PrimaryButton("رفع الأداء الآن") { showBoost = true } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniToggle("وضع الألعاب", gameOn, ready, Modifier.weight(1f)) { vm.toggleGame() }
                MiniToggle("توفير الطاقة", saveOn, ready, Modifier.weight(1f)) { vm.togglePowerSave() }
            }
        }
        item { LiveCharts(hist) }
        val top = sample?.processes?.sortedByDescending { it.cpuPercent }?.take(3).orEmpty()
        if (top.isNotEmpty()) {
            item { SectionTitle("الأكثر استهلاكاً الآن") }
            items(top, key = { it.packageName }) { p ->
                val label = remember(p.packageName) { vm.device.label(p.packageName) }
                SoftCard {
                    Text(label, fontWeight = FontWeight.SemiBold)
                    Caption("المعالج ${"%.1f".format(p.cpuPercent)}% — الذاكرة ${"%.0f".format(p.rssKb / 1024.0)} MB")
                }
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
