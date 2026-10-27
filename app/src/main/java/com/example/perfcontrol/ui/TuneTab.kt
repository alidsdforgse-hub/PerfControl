package com.example.perfcontrol.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import com.example.perfcontrol.core.Verdict
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
    var showPicker by remember { mutableStateOf(false) }
    var benchPkg by remember { mutableStateOf<String?>(null) }
    val gameMin by vm.gameMinutes.collectAsState()
    val cleanH by vm.cleanHours.collectAsState()
    val shortcuts by vm.shortcutsOn.collectAsState()
    val rows by vm.benchRows.collectAsState()
    val stage by vm.benchStage.collectAsState()
    val apps by vm.allUserApps.collectAsState()
    val busy by vm.busy.collectAsState()
    val ready = state == ShizukuState.Ready

    LaunchedEffect(state) { vm.loadTune(); vm.loadExclusions() }

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

        item { SectionTitle("الأتمتة") }
        item {
            SoftCard {
                Text("مؤقت وضع الألعاب", fontWeight = FontWeight.SemiBold)
                Caption("يُوقف وضع الألعاب تلقائياً بعد المدة ويعيد إعداداتك الأصلية.")
                ChoiceRow(
                    listOf("بدون", "30 د", "60 د", "120 د"),
                    listOf(0, 30, 60, 120).indexOf(gameMin).takeIf { it >= 0 },
                    true,
                ) { vm.setGameMinutes(listOf(0, 30, 60, 120)[it]) }
            }
        }
        item {
            SoftCard {
                Text("تنظيف تلقائي مجدول", fontWeight = FontWeight.SemiBold)
                Caption("ينظّف كاش كل التطبيقات في الخلفية. يحتاج Shizuku يعمل وقت التنفيذ، وفي شاومي اجعل بطارية التطبيق \"بلا قيود\".")
                ChoiceRow(
                    listOf("إيقاف", "6 س", "12 س", "24 س"),
                    listOf(0, 6, 12, 24).indexOf(cleanH).takeIf { it >= 0 },
                    true,
                ) { vm.setCleanHours(listOf(0, 6, 12, 24)[it]) }
            }
        }
        item {
            ToggleCard(
                "اختصارات في الإشعارات",
                "إشعار دائم بأزرار: وضع الألعاب، رفع الأداء، تنظيف. كما يمكنك إضافة مربع \"وضع الألعاب\" من لوحة الإعدادات السريعة.",
                shortcuts,
                true,
            ) { vm.setShortcuts(it) }
        }

        item { SectionTitle("اختبار قبل / بعد") }
        item {
            SoftCard {
                Text("قِس الفرق الحقيقي", fontWeight = FontWeight.SemiBold)
                Caption("يقيس الذاكرة المتاحة وعدد العمليات وسرعة فتح تطبيق تختاره، ثم يرفع الأداء ويقيس ثانية ويعرض الفرق بالأرقام. سيفتح التطبيق المختار عدة مرات ويغلقه.")
                OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(benchPkg?.let { vm.device.label(it) } ?: "اختيار تطبيق لقياس سرعة فتحه (اختياري)")
                }
                PrimaryButton(stage ?: "ابدأ الاختبار", enabled = ready && !busy) { vm.runBenchmark(benchPkg) }
                rows?.let { list ->
                    list.forEach { r ->
                        val mark = when (r.verdict) {
                            Verdict.Better -> "✔ أفضل"
                            Verdict.Same -> "= مماثل"
                            Verdict.Worse -> "✖ أسوأ"
                        }
                        Column {
                            Text(r.label, fontWeight = FontWeight.SemiBold)
                            Caption("قبل: ${r.before}   ←   بعد: ${r.after}   ($mark)")
                        }
                    }
                    Caption("ملاحظة: النظام قد يعيد تشغيل بعض التطبيقات المُوقَفة، والأرقام تتفاوت قليلاً من مرة لأخرى.")
                }
            }
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

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("اختر تطبيقاً") },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        TextButton(onClick = { benchPkg = null; showPicker = false }) { Text("بدون (قياس الذاكرة فقط)") }
                    }
                    items(apps, key = { it.pkg }) { a ->
                        TextButton(onClick = { benchPkg = a.pkg; showPicker = false }) { Text(a.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("إغلاق") } },
        )
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
