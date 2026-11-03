package com.example.perfcontrol.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.perfcontrol.core.ShizukuState
import kotlinx.coroutines.delay

@Composable
fun AppsTab(vm: AppViewModel) {
    var sub by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = sub, edgePadding = 0.dp, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
            listOf("الألعاب", "العمليات", "الثقيلة", "الإقلاع", "التجميد", "الإشعارات").forEachIndexed { i, t ->
                Tab(selected = sub == i, onClick = { sub = i }, text = { Text(t) })
            }
        }
        when (sub) {
            0 -> GamesTab(vm)
            1 -> ProcessesTab(vm)
            2 -> HeavyTab(vm)
            3 -> StartupTab(vm)
            4 -> FreezeTab(vm)
            else -> ProtectTab(vm)
        }
    }
}

@Composable
fun HeavyTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val heavy by vm.heavy.collectAsState()
    var confirmAll by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        while (true) {
            vm.refreshSample()
            delay(2500)
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        if (state != ShizukuState.Ready) {
            item { SoftCard { Text("يحتاج Shizuku", fontWeight = FontWeight.SemiBold); Caption("لا يمكن قراءة استهلاك التطبيقات بدونه.") } }
        } else if (heavy.isEmpty()) {
            item {
                SoftCard {
                    Text("لا توجد تطبيقات ثقيلة", fontWeight = FontWeight.SemiBold)
                    Caption("لا يوجد تطبيق يتجاوز 400 ميغابايت أو 10% من المعالج الآن.")
                }
            }
        } else {
            item { PrimaryButton("إيقاف كل التطبيقات الثقيلة (${heavy.size})") { confirmAll = true } }
            items(heavy, key = { it.packageName }) { h ->
                val label = remember(h.packageName) { vm.device.label(h.packageName) }
                SoftCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.SemiBold)
                            Caption("${h.reason} — ${"%.0f".format(h.rssKb / 1024.0)} MB — ${"%.1f".format(h.cpuPercent)}%")
                        }
                        TextButton(onClick = { vm.stopHeavy(listOf(h.packageName)) }) { Text("إيقاف") }
                    }
                }
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

@Composable
fun FreezeTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val apps by vm.allUserApps.collectAsState()
    val frozen by vm.frozen.collectAsState()
    var pending by remember { mutableStateOf<String?>(null) }
    val ready = state == ShizukuState.Ready

    LaunchedEffect(state) { vm.loadFreeze() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item {
            SoftCard {
                Text("تجميد التطبيقات", fontWeight = FontWeight.SemiBold)
                Caption("التطبيق المجمّد يُعطَّل بالكامل: لا يعمل ولا يظهر في الشاشة الرئيسية ولا يستهلك شيئاً. يمكنك إعادته بزر واحد.")
                if (!ready) Caption("يحتاج Shizuku جاهزاً.")
            }
        }
        if (frozen.isNotEmpty()) {
            item { SectionTitle("المجمّدة الآن (${frozen.size})") }
            items(frozen.toList(), key = { "f_" + it }) { pkg ->
                val label = remember(pkg) { vm.device.label(pkg) }
                SoftCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, fontWeight = FontWeight.SemiBold)
                            Caption(pkg)
                        }
                        TextButton(enabled = ready, onClick = { vm.setFrozen(pkg, false) }) { Text("إعادة") }
                    }
                }
            }
        }
        item { SectionTitle("تطبيقاتك") }
        items(apps.filter { it.pkg !in frozen }, key = { "a_" + it.pkg }) { app ->
            SoftCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.SemiBold)
                        Caption(app.pkg)
                    }
                    TextButton(enabled = ready, onClick = { pending = app.pkg }) { Text("تجميد") }
                }
            }
        }
    }

    pending?.let { pkg ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("تجميد ${vm.device.label(pkg)}؟") },
            text = { Text("سيتوقف التطبيق ويختفي حتى تعيده من هذه الشاشة. لا يُنصح بتجميد تطبيق تنتظر إشعاراته.") },
            confirmButton = { TextButton(onClick = { pending = null; vm.setFrozen(pkg, true) }) { Text("تجميد") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
fun ProtectTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val apps by vm.allUserApps.collectAsState()
    val prot by vm.notifProtected.collectAsState()
    val ready = state == ShizukuState.Ready

    LaunchedEffect(state) { vm.loadProtect() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item {
            SoftCard {
                Text("حماية الإشعارات", fontWeight = FontWeight.SemiBold)
                Caption("اختر التطبيقات المهمة (مثل واتساب). لن يوقفها رفع الأداء، ولن يقتلها توفير الطاقة العميق، فتصلك إشعاراتها دائماً.")
                if (!ready) Caption("يحتاج Shizuku جاهزاً.")
            }
        }
        items(apps, key = { it.pkg }) { app ->
            ToggleCard(app.label, app.pkg, app.pkg in prot, ready) { vm.setNotifProtected(app.pkg, it) }
        }
    }
}

@Composable
fun GamesTab(vm: AppViewModel) {
    val state by vm.shizukuState.collectAsState()
    val apps by vm.allUserApps.collectAsState()
    val games by vm.games.collectAsState()
    val detected by vm.detectedGames.collectAsState()
    val report by vm.lastReport.collectAsState()
    val gameOn by vm.gameEnabled.collectAsState()
    val autoGame by vm.autoGame.collectAsState()
    val dnd by vm.gameDnd.collectAsState()
    val hasUsage by vm.hasUsageAccess.collectAsState()
    val gMode by vm.globalMode.collectAsState()
    val perGame by vm.perGame.collectAsState()
    val overlayOn by vm.overlayOn.collectAsState()
    val overlayPerm by vm.overlayPerm.collectAsState()
    val scale by vm.gameScale.collectAsState()
    var editing by remember { mutableStateOf(false) }
    val ready = state == ShizukuState.Ready

    LaunchedEffect(Unit) { vm.loadGames() }

    val myGames = apps.filter { it.pkg in games }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding, verticalArrangement = listSpacing) {
        item {
            HeroCard {
                Text("مركز الألعاب", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "اضغط على أي لعبة: يشغّل وضع الألعاب ثم يفتحها، وعند الإيقاف تظهر لك تقرير الجلسة.",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                )
                Text(
                    if (gameOn) "وضع الألعاب يعمل الآن" else "وضع الألعاب متوقف",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (gameOn) item { PrimaryButton("إيقاف وضع الألعاب وعرض التقرير") { vm.toggleGame() } }
        item {
            SoftCard {
                Text("وضع الأداء", fontWeight = FontWeight.SemiBold)
                ChoiceRow(com.example.perfcontrol.core.PerfMode.values().map { it.title }, gMode, true) { vm.setGlobalMode(it) }
                Caption(com.example.perfcontrol.core.PerfMode.values()[gMode].desc)
                Caption("هذه إعدادات برمجية (معدل التحديث والرسوم وإيقاف الخلفية)، ولا تتحكم في مروحة أو عتاد الجهاز.")
            }
        }
        item {
            SoftCard {
                Text("تخفيف الضغط على المعالج وكرت الشاشة", fontWeight = FontWeight.SemiBold)
                Caption("دقة الشاشة أثناء اللعب:")
                ChoiceRow(
                    listOf("100%", "90%", "80%", "70%"),
                    listOf(100, 90, 80, 70).indexOf(scale).takeIf { it >= 0 },
                    true,
                ) { vm.setGameScale(listOf(100, 90, 80, 70)[it]) }
                Caption("خفض الدقة يقلل حمل كرت الشاشة (GPU) والحرارة بشكل حقيقي، لكن الصورة تصبح أقل حدّة. يُطبَّق عند الضغط على \"العب\" أو تشغيل وضع الألعاب يدوياً (ولا يُطبَّق في التشغيل التلقائي حتى لا تُعاد تهيئة اللعبة وهي مفتوحة)، ويُعاد تلقائياً عند الإيقاف.")
                Caption("لتخفيف المعالج (CPU): اختر وضع \"هادئ\" (60 هرتز) أو \"متوازن\"؛ تقليل معدل الإطارات يخفف عمل المعالج. لا أستطيع تغيير سرعة المعالج مباشرة لأن ذلك يحتاج صلاحية روت.")
                androidx.compose.material3.OutlinedButton(onClick = { vm.resetDisplay() }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                    Text("إعادة دقة الشاشة الأصلية (إن بقيت منخفضة)")
                }
            }
        }
        item {
            ToggleCard(
                "شريط المعلومات أثناء اللعب",
                if (overlayPerm) "يعرض CPU وGPU (إن قرأهما الجهاز) والحرارة والبطارية والذاكرة والوقت أعلى الشاشة. لا يعرض FPS."
                else "يحتاج إذن \"العرض فوق التطبيقات\". فعّل الخيار وسيفتح الإعداد.",
                overlayOn,
                true,
            ) { vm.setOverlay(it) }
        }
        item {
            ToggleCard(
                "تشغيل تلقائي عند فتح اللعبة",
                if (hasUsage) "يعمل وضع الألعاب وحده عند فتح أي لعبة من قائمتك، ويتوقف بعد خروجك منها بنصف دقيقة تقريباً."
                else "يحتاج إذن \"الوصول إلى بيانات الاستخدام\". اضغط الزر أدناه لمنحه ثم فعّل الخيار.",
                autoGame,
                true,
            ) { vm.setAutoGame(it) }
        }
        if (!hasUsage) {
            item {
                androidx.compose.material3.OutlinedButton(onClick = { vm.official.openUsageAccess() }, modifier = Modifier.fillMaxWidth()) {
                    Text("منح إذن بيانات الاستخدام")
                }
            }
        }
        item {
            ToggleCard(
                "عدم الإزعاج أثناء اللعب",
                "يكتم الإشعارات والمكالمات غير المهمة أثناء وضع الألعاب ويعيدها بعده.",
                dnd,
                true,
            ) { vm.setGameDnd(it) }
        }
        if (report.isNotBlank()) {
            item {
                SoftCard {
                    Text("تقرير آخر جلسة", fontWeight = FontWeight.SemiBold)
                    Caption(report)
                }
            }
        }
        item { SectionTitle("ألعابي") }
        if (myGames.isEmpty()) {
            item { SoftCard { Caption("لم تُضَف ألعاب بعد. اضغط \"تعديل القائمة\" واختر ألعابك.") } }
        }
        items(myGames, key = { "g_" + it.pkg }) { g ->
            SoftCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.label, fontWeight = FontWeight.SemiBold)
                        Caption(if (ready) "تشغيل مع وضع الألعاب" else "تشغيل عادي (Shizuku غير جاهز)")
                    }
                    TextButton(onClick = { vm.launchGame(g.pkg) }) { Text("العب") }
                }
                Caption("وضع هذه اللعبة:")
                ChoiceRow(
                    listOf("افتراضي") + com.example.perfcontrol.core.PerfMode.values().map { it.title },
                    (perGame[g.pkg] ?: -1) + 1,
                    true,
                ) { vm.setGameMode(g.pkg, if (it == 0) null else it - 1) }
            }
        }
        item {
            androidx.compose.material3.OutlinedButton(
                onClick = { editing = !editing },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (editing) "تم" else "تعديل القائمة") }
        }
        if (editing) {
            items(apps, key = { "e_" + it.pkg }) { a ->
                ToggleCard(
                    a.label,
                    if (a.pkg in detected) "لعبة (اكتشفها النظام)" else a.pkg,
                    a.pkg in games,
                    true,
                ) { vm.toggleGameApp(a.pkg, it) }
            }
        }
    }
}
