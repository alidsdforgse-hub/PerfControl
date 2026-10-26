package com.example.perfcontrol.core

class Cleaner(private val runner: CommandRunner) {
    suspend fun trimAllCaches(): CommandResult = runner.run("cmd package trim-caches 999G")
}
