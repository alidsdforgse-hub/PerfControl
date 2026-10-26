# PerfControl Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** بناء مشروع Android Studio لتطبيق "متحكم الأداء" يتحكم في أداء جهاز Poco F5 Pro (أندرويد 15) عبر Shizuku.

**Architecture:** وحدة `:core` بلغة Kotlin بحتة (بدون اعتماد على أندرويد) تحوي المحللات والمنطق والوحدات الخمس، وتتحدث مع النظام فقط عبر واجهة `CommandRunner`. وحدة `:app` تحوي Shizuku وخدمة الشل ومساعدات أندرويد وواجهة Compose. هذا الفصل يجعل كل المنطق قابلاً للاختبار على JVM دون جهاز.

**Tech Stack:** Kotlin 1.9.24، Jetpack Compose (BOM 2024.06.00)، Gradle 8.7 مع AGP 8.5.2، Shizuku `dev.rikka.shizuku:api` و`provider` بالإصدار 13.1.5، JUnit 4.13.2، kotlinx-coroutines.

**Spec:** `docs/superpowers/specs/2026-10-26-perfcontrol-design.md`

## Global Constraints

- الحزمة الأساسية: `com.example.perfcontrol`، وحزمة `:core` هي `com.example.perfcontrol.core`.
- minSdk 26، targetSdk وcompileSdk 35.
- الواجهة عربية بتخطيط من اليمين إلى اليسار (`supportsRtl="true"` و`LayoutDirection.Rtl`).
- الأذونات: `KILL_BACKGROUND_PROCESSES`، `PACKAGE_USAGE_STATS`، `QUERY_ALL_PACKAGES`.
- الأوامر حرفياً: `am force-stop <pkg>`، `cmd package trim-caches 999G`، `cmd appops get <pkg> RUN_ANY_IN_BACKGROUND`، `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`، `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND default`.
- أمر قراءة العمليات: `top -b -n 1 -m 30 -o PID,RES,%CPU,ARGS`.
- التطبيقات المحمية دائماً: `android`، `com.android.systemui`، `com.android.phone`، `com.miui.securitycenter`، `com.miui.home`، `com.xiaomi.xmsf`، `com.miui.powerkeeper`، `moe.shizuku.privileged.api`، التطبيق نفسه، والشاشة الرئيسية ولوحة المفاتيح وتطبيقا الاتصال والرسائل الافتراضيان.
- لا تُعدّ عملية تغيير حالة (بدء التشغيل) ناجحة حتى تؤكدها قراءة الحالة بعد التنفيذ.
- لا يُنفَّذ أي أمر مدمّر داخل شاشة فحص التوافق.

## Review Focus

- ناتج `top` فارغ أو مبتور أو بأسطر تالفة: يعيد المحلل قائمة فارغة أو يتخطى السطر ولا يتعطل. (Task 2)
- أسماء عمليات بلواحق مثل `com.app:service` أو `com.app:remote`: تُنسب لحزمتها الأساسية ولا تظهر كتطبيقات مستقلة. (Task 2)
- طلب إيقاف تطبيق محمي أو التطبيق نفسه، حتى لو غاب من الاستثناءات: يُتجاوز ولا يصل أمره للنظام. (Task 3, 6)
- `appops set` ينجح ظاهرياً لكن الحالة لا تتغير (متوقع على شاومي): تُعاد نتيجة فشل صريحة لا نجاح. (Task 8)
- توقف Shizuku أو انتهاء مهلة الأمر في منتصف عملية جماعية: يُسجَّل الباقي فاشلاً بسبب واضح وتُعاد حصيلة جزئية دون استثناء. (Task 6, 10)

---

## File Structure

```
PerfControl/
  settings.gradle.kts, build.gradle.kts, gradle.properties
  core/build.gradle.kts
  core/src/main/kotlin/com/example/perfcontrol/core/
    CommandRunner.kt        CommandResult + CommandRunner
    Parsers.kt              ProcStatParser, TopParser, MemInfoParser, basePackage
    AppProtection.kt        قوائم الحماية
    MonitorRepository.kt    قياس لحظي
    ProcessManager.kt       إيقاف إجباري + BatchResult
    Cleaner.kt              تقليص الكاش
    StartupManager.kt       تقييد/تراجع بدء التشغيل + StateStore
    Booster.kt              التسريع
    CompatibilityChecker.kt فحص التوافق
    ShizukuState.kt         تحويل الحالة
  core/src/test/kotlin/com/example/perfcontrol/core/   اختبار لكل ملف أعلاه + FakeCommandRunner.kt
  app/build.gradle.kts, AndroidManifest.xml, res/
  app/src/main/aidl/com/example/perfcontrol/IShellService.aidl
  app/src/main/java/com/example/perfcontrol/
    shizuku/ShizukuManager.kt, ShellService.kt, ShizukuCommandRunner.kt
    android/DeviceInfo.kt, OwnCache.kt, PrefsStateStore.kt, OfficialApis.kt
    ui/ MainActivity.kt, SetupScreen.kt, PerformanceTab.kt, ProcessesTab.kt, CleanupTab.kt, StartupTab.kt, SettingsScreen.kt, AppViewModel.kt
  README.md
```

أمر الاختبار في كل مهمة: `./gradlew :core:test --tests "<الصنف>"`.

---

### Task 1: هيكل المشروع والأنواع الأساسية

**Files:**
- Create: ملفات Gradle أعلاه وملفات `:app` الهيكلية (manifest، themes، strings، `local` فارغة) و`gradle/wrapper/gradle-wrapper.properties` (Gradle 8.7)
- Create: `core/.../CommandRunner.kt`
- Test: `core/src/test/.../FakeCommandRunner.kt`, `CommandResultTest.kt`

**Interfaces:**
- Produces: `sealed interface CommandResult { data class Success(val output: String); data class Failure(val reason: String, val exitCode: Int? = null) }`، و`interface CommandRunner { suspend fun run(command: String): CommandResult }`، و`class FakeCommandRunner(val responses: MutableMap<String, CommandResult>, val log: MutableList<String>) : CommandRunner` (الأمر غير المعرّف يعيد `Failure("unmapped")`).

- [ ] **Step 1:** إن كان `gradle` و`java 17` متاحين نفّذ `git init` في `PerfControl` ثم `gradle wrapper --gradle-version 8.7`، وإلا اترك المعالج لـ Android Studio وسجّل ذلك في README.
- [ ] **Step 2:** اكتب `CommandResultTest.isSuccess` يتحقق أن `Success("x")` و`Failure("y")` يُفرَّقان عبر `when`، و`FakeCommandRunnerTest.unmappedCommandFails`.
- [ ] **Step 3:** شغّل `./gradlew :core:test` وتأكد من الفشل لعدم وجود الأنواع.
- [ ] **Step 4:** اكتب `CommandRunner.kt` و`FakeCommandRunner.kt` وملفات Gradle (`:core` وحدة `kotlin("jvm")` مع jvmToolchain 17، و`:app` بإعداد spec القسم 10 وتفعيل `buildFeatures { aidl = true; compose = true }`).
- [ ] **Step 5:** شغّل الاختبارين وتأكد من النجاح، ثم `git add -A && git commit -m "chore: project skeleton and command types"`.

### Task 2: المحللات (Parsers)

**Files:** Create `core/.../Parsers.kt`، Test `ParsersTest.kt`

**Interfaces:**
- Produces: `data class CpuSample(val idle: Long, val total: Long)`، `data class MemInfo(val totalKb: Long, val availableKb: Long)`، `data class ProcessInfo(val pid: Int, val packageName: String, val cpuPercent: Double, val rssKb: Long)`، `fun basePackage(processName: String): String`، `object ProcStatParser { fun parse(text: String): CpuSample?; fun usagePercent(prev: CpuSample, cur: CpuSample): Double }`، `object TopParser { fun parse(text: String): List<ProcessInfo> }`، `object MemInfoParser { fun parse(text: String): MemInfo?; fun freedKb(before: MemInfo, after: MemInfo): Long }`.

- [ ] **Step 1:** اكتب الاختبارات بنصوص ثابتة: `cpuUsageBetweenSamples` (خطا `cpu` متتاليان، فرق الخمول 75 من إجمالي 100 يعطي 25.0)، `cpuUsageZeroTotalDeltaIsZero`، `cpuParseGarbageReturnsNull`، `topParsesRowsAfterHeader` (سطر الرأس يحوي `PID`، وRES بلواحق `512K` و`1.5M` و`2G` تُحوَّل إلى KB)، `topEmptyOrTruncatedReturnsEmptyList`، `topSkipsMalformedRow`، `basePackageStripsColonSuffix` (`com.app:remote` تعطي `com.app`)، `topMergesProcessesOfSamePackage` (مجموع RSS والمعالج)، `memInfoParsesTotalAndAvailable`، `freedKbNeverNegative`.
- [ ] **Step 2:** شغّل `./gradlew :core:test --tests "*ParsersTest"` وتأكد من الفشل.
- [ ] **Step 3:** نفّذ الواجهات أعلاه. يُتحمل ناتج `top` باختلاف المسافات، ويُهمل أي سطر لا يطابق الأعمدة الأربعة. ناتج `usagePercent` محصور بين 0 و100.
- [ ] **Step 4:** شغّل الاختبار وتأكد من النجاح، ثم commit.

### Task 3: قوائم الحماية

**Files:** Create `core/.../AppProtection.kt`، Test `AppProtectionTest.kt`

**Interfaces:**
- Produces: `data class ProtectionContext(val dynamic: Set<String>, val userExclusions: Set<String>)` و`class AppProtection(ctx: ProtectionContext) { fun isProtected(pkg: String): Boolean }` و`val FIXED_PROTECTED: Set<String>` بالقيم الحرفية من Global Constraints (دون التطبيق نفسه، فهو ضمن `dynamic`).

- [ ] **Step 1:** اكتب `fixedPackagesAreProtected`، `dynamicPackagesAreProtected`، `userExclusionsAreProtected`، `ordinaryAppIsNotProtected`، `suffixedProcessResolvesToBasePackage` (`com.miui.home:x` محمي).
- [ ] **Step 2:** شغّل وتأكد من الفشل.
- [ ] **Step 3:** نفّذ `isProtected` بعد تطبيق `basePackage` على المُدخل.
- [ ] **Step 4:** شغّل وتأكد من النجاح، ثم commit.

### Task 4: المراقبة

**Files:** Create `core/.../MonitorRepository.kt`، Test `MonitorRepositoryTest.kt`

**Interfaces:**
- Consumes: `CommandRunner`، `ProcStatParser`، `TopParser`، `MemInfoParser`.
- Produces: `data class MonitorSample(val cpuPercent: Double?, val processes: List<ProcessInfo>, val mem: MemInfo?)` و`class MonitorRepository(runner: CommandRunner) { suspend fun sample(): MonitorSample }`. أول عينة تعطي `cpuPercent = null` لعدم وجود عينة سابقة، والعينات اللاحقة تحسب الفرق. الأوامر: `cat /proc/stat`، `top -b -n 1 -m 30 -o PID,RES,%CPU,ARGS`، `cat /proc/meminfo`.

- [ ] **Step 1:** اكتب `firstSampleHasNoCpuPercent`، `secondSampleComputesCpuPercent`، `failedCommandYieldsNullOrEmptyNotException` (كل أمر فاشل يترك حقله فارغاً).
- [ ] **Step 2:** شغّل وتأكد من الفشل.
- [ ] **Step 3:** نفّذ `sample()`؛ يحتفظ المستودع بآخر `CpuSample`.
- [ ] **Step 4:** شغّل وتأكد من النجاح، ثم commit.

### Task 5: التنظيف

**Files:** Create `core/.../Cleaner.kt`، Test `CleanerTest.kt`

**Interfaces:**
- Produces: `class Cleaner(runner: CommandRunner) { suspend fun trimAllCaches(): CommandResult }` ينفّذ `cmd package trim-caches 999G` ويعيد نتيجته كما هي.

- [ ] **Step 1:** اكتب `trimCachesRunsExactCommand` و`trimCachesPropagatesFailure`.
- [ ] **Step 2:** شغّل وتأكد من الفشل.
- [ ] **Step 3:** نفّذ الصنف.
- [ ] **Step 4:** شغّل وتأكد من النجاح، ثم commit.

### Task 6: إدارة العمليات

**Files:** Create `core/.../ProcessManager.kt`، Test `ProcessManagerTest.kt`

**Interfaces:**
- Consumes: `CommandRunner`، `AppProtection`.
- Produces: `data class BatchResult(val succeeded: List<String>, val skippedProtected: List<String>, val failed: Map<String, String>)` و`class ProcessManager(runner: CommandRunner, protection: AppProtection) { suspend fun forceStop(packages: List<String>): BatchResult }`.

- [ ] **Step 1:** اكتب `protectedPackagesAreSkippedAndNeverReachRunner` (يتحقق من `log`)، `suffixedProtectedProcessIsSkipped`، `failureOnOneContinuesWithRest`، `runnerFailureMidBatchMarksRemainingFailedWithReason`، `duplicatePackagesStoppedOnce`، `emptyListReturnsEmptyResult`.
- [ ] **Step 2:** شغّل وتأكد من الفشل.
- [ ] **Step 3:** نفّذ `forceStop` بتحويل كل عنصر إلى حزمته الأساسية، وإزالة المكرر، واستدعاء `am force-stop <pkg>` لغير المحمي.
- [ ] **Step 4:** شغّل وتأكد من النجاح، ثم commit.

### Task 7: بدء التشغيل

**Files:** Create `core/.../StartupManager.kt`، Test `StartupManagerTest.kt`

**Interfaces:**
- Produces: `interface StateStore { fun saveOriginal(pkg: String, mode: String); fun original(pkg: String): String?; fun clear(pkg: String) }`، `data class StartupResult(val ok: Boolean, val message: String)`، `fun parseAppOpMode(output: String): String?` (يقرأ `RUN_ANY_IN_BACKGROUND: allow` فيعيد `allow`)، `class StartupManager(runner: CommandRunner, store: StateStore) { suspend fun restrict(pkg: String, op: String = "RUN_ANY_IN_BACKGROUND"): StartupResult; suspend fun restore(pkg: String, op: String = "RUN_ANY_IN_BACKGROUND"): StartupResult }`. الثابت التجريبي `const val XIAOMI_AUTOSTART_OP = "AUTO_START"` (اسمه يُتحقق منه في Task 9).

- [ ] **Step 1:** اكتب `restrictSavesOriginalThenSetsIgnoreThenVerifies`، `restrictFailsWhenReadBackStillAllow` (يحاكي شاومي: `set` ينجح و`get` يعيد `allow`)، `restrictDoesNotOverwriteSavedOriginalWhenAlreadyRestricted`، `restoreSetsDefaultAndClearsStore`، `restoreWithoutSavedOriginalFailsClearly`، `parseAppOpModeHandlesGarbage` (يعيد null).
- [ ] **Step 2:** شغّل وتأكد من الفشل.
- [ ] **Step 3:** نفّذ المنطق؛ يعيد `ok=false` ورسالة عربية واضحة حين لا تؤكد القراءة اللاحقة التغيير، ولا يحفظ الحالة الأصلية إلا حين تكون القراءة الأولى ناجحة.
- [ ] **Step 4:** شغّل وتأكد من النجاح، ثم commit.

### Task 8: التسريع

**Files:** Create `core/.../Booster.kt`، Test `BoosterTest.kt`

**Interfaces:**
- Consumes: `ProcessManager`، `Cleaner`، `MemInfoParser`، `CommandRunner`.
- Produces: `data class BoostReport(val freedKb: Long, val batch: BatchResult, val cacheTrimmed: Boolean)` و`class Booster(runner: CommandRunner, processes: ProcessManager, cleaner: Cleaner, protection: AppProtection) { fun plan(candidates: List<String>): List<String>; suspend fun boost(packages: List<String>): BoostReport }`. `plan` يستبعد المحمي ليعرضه التطبيق للتأكيد، و`boost` يقيس `/proc/meminfo` قبل وبعد.

- [ ] **Step 1:** اكتب `planExcludesProtected`، `boostOrderIsMeasureStopTrimMeasure` (بترتيب `log`)، `freedKbIsDifferenceOfAvailable`، `freedKbIsZeroWhenMemoryGotWorse`، `trimFailureStillReportsStoppedApps`.
- [ ] **Step 2:** شغّل وتأكد من الفشل.
- [ ] **Step 3:** نفّذ الصنف.
- [ ] **Step 4:** شغّل وتأكد من النجاح، ثم commit.

### Task 9: فحص التوافق وحالة Shizuku

**Files:** Create `core/.../CompatibilityChecker.kt`، `core/.../ShizukuState.kt`، Test `CompatibilityCheckerTest.kt`, `ShizukuStateTest.kt`

**Interfaces:**
- Produces: `data class CheckItem(val name: String, val command: String, val ok: Boolean, val detail: String)`، `class CompatibilityChecker(runner: CommandRunner, selfPackage: String) { suspend fun run(): List<CheckItem> }`، `enum class ShizukuState { NotInstalled, NotRunning, NoPermission, Ready }`، `fun resolveShizukuState(installed: Boolean, running: Boolean, granted: Boolean): ShizukuState`.
- الأوامر الآمنة التي يشغّلها الفاحص فقط: `top -b -n 1 -m 3 -o PID,RES,%CPU,ARGS`، `cat /proc/stat`، `cat /proc/meminfo`، `cmd package trim-caches 1`، `cmd appops get <self> RUN_ANY_IN_BACKGROUND`، `cmd appops get <self> AUTO_START`.

- [ ] **Step 1:** اكتب `reportsOneItemPerCommand`، `failureCarriesReasonInDetail`، `neverRunsForceStopOrSet` (لا يظهر `force-stop` ولا `appops set` في `log`)، `stateNotInstalledWinsOverOthers`، `stateReadyOnlyWhenAllTrue`.
- [ ] **Step 2:** شغّل وتأكد من الفشل.
- [ ] **Step 3:** نفّذ الصنفين.
- [ ] **Step 4:** شغّل وتأكد من النجاح، ثم commit.

### Task 10: طبقة Shizuku في التطبيق

**Files:** Create `app/.../shizuku/ShizukuManager.kt`، `ShellService.kt`، `ShizukuCommandRunner.kt`، `app/src/main/aidl/com/example/perfcontrol/IShellService.aidl`؛ Modify `AndroidManifest.xml` (إضافة `ShizukuProvider`)

**Interfaces:**
- Consumes: `CommandRunner`، `resolveShizukuState`.
- Produces: `interface IShellService { String exec(String command); }` بناتج نصي مع رمز الخروج في السطر الأخير، `class ShizukuManager(context: Context) { val state: StateFlow<ShizukuState>; fun requestPermission(); suspend fun bind(): IShellService? }`، `class ShizukuCommandRunner(service: () -> IShellService?, timeoutMs: Long = 15000) : CommandRunner` يعيد `Failure` عند غياب الخدمة أو انتهاء المهلة أو أي استثناء بدل رميه، و`object FallbackRunner : CommandRunner` يعيد دائماً `Failure("Shizuku غير متاح")`.

- [ ] **Step 1:** لا اختبار آلي (يحتاج جهازاً). اكتب الملفات بالتوقيعات أعلاه، واستخدم `Shizuku.bindUserService` مع `UserServiceArgs` (لا `Shizuku.newProcess`).
- [ ] **Step 2:** تحقق أن `:core:test` ما زال ناجحاً، وإن توفر Android SDK نفّذ `./gradlew :app:assembleDebug` وتأكد من نجاح البناء.
- [ ] **Step 3:** commit.

### Task 11: مساعدات أندرويد

**Files:** Create `app/.../android/DeviceInfo.kt`، `OwnCache.kt`، `PrefsStateStore.kt`، `OfficialApis.kt`

**Interfaces:**
- Produces: `class DeviceInfo(ctx: Context) { fun dynamicProtected(): Set<String>; fun bootCapableApps(): List<AppEntry> }` (الشاشة الرئيسية، لوحة المفاتيح من `DEFAULT_INPUT_METHOD`، الاتصال والرسائل الافتراضيان، حزمة التطبيق نفسه؛ والتطبيقات غير النظامية ذات `RECEIVE_BOOT_COMPLETED`)، `data class AppEntry(val pkg: String, val label: String)`، `class OwnCache(ctx: Context) { fun sizeBytes(): Long; fun clear(): Long }`، `class PrefsStateStore(ctx: Context) : StateStore` مع `exclusions(): Set<String>` و`setExclusions(Set<String>)`، `class OfficialApis(ctx: Context)` فيها `hasUsageAccess()`، `mostUsedApps(): List<AppEntry>`، `openAppDetails(pkg)`، `openStorageSettings()`، `openMiuiAutostart()` (تحاول فتح شاشة التشغيل التلقائي في تطبيق الأمان، وتفتح تفاصيل التطبيق إن لم تنجح).

- [ ] **Step 1:** اكتب الملفات بالتوقيعات أعلاه. التعامل مع أي استثناء يكون بإرجاع قيمة فارغة لا بتعطل.
- [ ] **Step 2:** تحقق من البناء كما في Task 10 Step 2، ثم commit.

### Task 12: الواجهة

**Files:** Create `app/.../ui/` (الملفات المذكورة في File Structure)

**Interfaces:**
- Consumes: كل ما سبق. `AppViewModel` يجمع الوحدات ويختار `ShizukuCommandRunner` حين تكون الحالة `Ready` وإلا يستخدم `FallbackRunner` والمسار الرسمي من `OfficialApis`.

- [ ] **Step 1:** `MainActivity` بـ `CompositionLocalProvider(LocalLayoutDirection provides Rtl)` وشريط تنقل بأربعة تبويبات (أيقونات `Home`، `AutoMirrored.List`، `Delete`، `Settings`) وقائمة إعدادات تفتح `SettingsScreen` (فحص التوافق وإدارة الاستثناءات).
- [ ] **Step 2:** `SetupScreen` تظهر فوق التبويبات حين تكون الحالة غير `Ready`، وتشرح خطوات شاومي الأربع من spec القسم 8 وتحوي زر طلب الإذن.
- [ ] **Step 3:** `PerformanceTab` تحدّث `MonitorRepository.sample()` كل ثانية أثناء ظهورها فقط وزر "رفع الأداء" يفتح حواراً بقائمة `Booster.plan` ثم يعرض `BoostReport`. `ProcessesTab` قائمة العمليات مع زر إيقاف وتأكيد. `CleanupTab` حجم كاش التطبيق وزرا التنظيف. `StartupTab` القائمة مع تقييد وتراجع، وإن فشلت مرحلة شاومي التجريبية يعرض الرسالة ويقترح `openMiuiAutostart()`.
- [ ] **Step 4:** تحقق من البناء كما في Task 10 Step 2، ثم commit.

### Task 13: README والتسليم

**Files:** Create `README.md`

- [ ] **Step 1:** اكتب README عربياً: فتح المشروع في Android Studio، مزامنة Gradle، تشغيل الاختبارات، التثبيت على الجهاز، إعداد Shizuku على شاومي، ثم تشغيل شاشة فحص التوافق وإرسال نتيجتها.
- [ ] **Step 2:** شغّل `./gradlew :core:test` كاملاً وتأكد من نجاح كل الاختبارات، وإن لم يتوفر Gradle أو JDK هنا فاذكر ذلك صراحة في الرد للمستخدم دون ادعاء النجاح.
- [ ] **Step 3:** اضغط `PerfControl` في ملف zip وأرسله للمستخدم، ثم commit نهائي.
