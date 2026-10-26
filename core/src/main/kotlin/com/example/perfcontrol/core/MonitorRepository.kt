package com.example.perfcontrol.core

data class MonitorSample(
    val cpuPercent: Double?,
    val processes: List<ProcessInfo>,
    val mem: MemInfo?,
)

class MonitorRepository(private val runner: CommandRunner) {
    private var prev: CpuSample? = null

    suspend fun sample(): MonitorSample {
        var cpu: Double? = null
        val stat = (runner.run("cat /proc/stat") as? CommandResult.Success)?.let { ProcStatParser.parse(it.output) }
        if (stat != null) {
            prev?.let { cpu = ProcStatParser.usagePercent(it, stat) }
            prev = stat
        }
        val procs = (runner.run("top -b -n 1 -m 30 -o PID,RES,%CPU,ARGS") as? CommandResult.Success)
            ?.let { TopParser.parse(it.output) } ?: emptyList()
        val mem = (runner.run("cat /proc/meminfo") as? CommandResult.Success)
            ?.let { MemInfoParser.parse(it.output) }
        return MonitorSample(cpu, procs, mem)
    }
}
