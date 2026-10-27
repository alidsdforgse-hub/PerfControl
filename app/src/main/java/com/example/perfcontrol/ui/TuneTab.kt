package com.example.perfcontrol.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.perfcontrol.core.ShizukuState

@Composable
fun TuneTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val anim by vm.animChoice.collectAsState()
    val refresh by vm.refreshChoice.collectAsState()
    val saver by vm.batterySaver.collectAsState()
    val cacheBytes by vm.ownCacheBytes.collectAsState()
    var showOptimize by remember { mutableStateOf(false) }
    val ready = state == ShizukuState.Ready

    LaunchedEffect(state) { vm.loadTune() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        if (!ready) item { Caption("معظم خيارات هذه الصفحة تحتاج Shizuku جاهزاً.") }

        item { SectionTitle("الرسوم والشاشة") }
        item {
            SoftCard {
                Text("سرعة الرسوم المتحركة", fontWeight = FontWeight.SemiBold)
                Caption("إيقافها أو تسريعها يجعل الجهاز يبدو أسرع.")
                ChoiceRow(listOf("إيقاف", "سريع", "عادي"), anim, ready) { vm.setAnim(it) }
            }
        }
        item {
            SoftCard {
                Text("معدل تحديث الشاشة", fontWeight = FontWeight.SemiBold)
                Caption("الأعلى أنعم، والأقل يوفّر البطارية. \"تلقائي\" يعيد ضبط النظام.")
                ChoiceRow(listOf("تلقائي", "60", "90", "120"), refresh, ready) { vm.setRefresh(it) }
            }
        }

        item { SectionTitle("الطاقة") }
        item {
            ToggleCard("توفير الطاقة (النظام)", "يشغّل وضع توفير الطاقة في أندرويد.", saver, ready) { vm.setBatterySaver(it) }
        }

        item { SectionTitle("الصيانة") }
        item {
            ActionCard(
                "تحسين التطبيقات",
                "يعيد تحسين تشغيل كل التطبيقات لتفتح أسرع. تستغرق عدة دقائق.",
                "ابدأ التحسين",
                ready,
            ) { showOptimize = true }
        }
        item {
            ActionCard(
                "صيانة التخزين (TRIM)",
                "يشغّل صيانة التخزين لإبقاء القرص سريعاً مع الوقت.",
                "ابدأ الصيانة",
                ready,
            ) { vm.maintainStorage() }
        }
        item {
            SoftCard {
                Text("تنظيف الكاش", fontWeight = FontWeight.SemiBold)
                Caption("كاش هذا التطبيق: ${"%.1f".format(cacheBytes / 1024.0 / 1024.0)} MB")
                OutlinedButton(onClick = { vm.clearOwnCache() }, modifier = Modifier.fillMaxWidth()) {
                    Text("تنظيف كاش هذا التطبيق")
                }
                if (ready) {
                    OutlinedButton(onClick = { vm.trimAll() }, modifier = Modifier.fillMaxWidth()) {
                        Text("تنظيف كاش كل التطبيقات")
                    }
                } else {
                    OutlinedButton(onClick = { vm.official.openStorageSettings() }, modifier = Modifier.fillMaxWidth()) {
                        Text("فتح شاشة التخزين")
                    }
                }
            }
        }
    }

    if (showOptimize) {
        AlertDialog(
            onDismissRequest = { showOptimize = false },
            title = { Text("تحسين التطبيقات") },
            text = { Text("سيعيد النظام تحسين تشغيل كل التطبيقات. قد يستغرق عدة دقائق وقد يسخن الجهاز قليلاً. أبقِ التطبيق مفتوحاً حتى ينتهي.") },
            confirmButton = { TextButton(onClick = { showOptimize = false; vm.optimizeApps() }) { Text("ابدأ") } },
            dismissButton = { TextButton(onClick = { showOptimize = false }) { Text("إلغاء") } },
        )
    }
}
