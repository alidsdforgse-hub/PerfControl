package com.example.perfcontrol.core

data class BatchResult(
    val succeeded: List<String>,
    val skippedProtected: List<String>,
    val failed: Map<String, String>,
)

class ProcessManager(
    private val runner: CommandRunner,
    private val protection: AppProtection,
) {
    suspend fun forceStop(packages: List<String>): BatchResult {
        val succeeded = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = linkedMapOf<String, String>()
        for (pkg in packages.map(::basePackage).distinct()) {
            if (protection.isProtected(pkg)) {
                skipped += pkg
                continue
            }
            if (!isValidPackageName(pkg)) {
                failed[pkg] = "اسم حزمة غير صالح"
                continue
            }
            try {
                when (val r = runner.run("am force-stop $pkg")) {
                    is CommandResult.Success -> succeeded += pkg
                    is CommandResult.Failure -> failed[pkg] = r.reason
                }
            } catch (e: Exception) {
                failed[pkg] = e.message ?: "خطأ غير معروف"
            }
        }
        return BatchResult(succeeded, skipped, failed)
    }
}
