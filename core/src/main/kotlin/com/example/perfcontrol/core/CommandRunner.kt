package com.example.perfcontrol.core

sealed interface CommandResult {
    data class Success(val output: String) : CommandResult
    data class Failure(val reason: String, val exitCode: Int? = null) : CommandResult
}

interface CommandRunner {
    suspend fun run(command: String): CommandResult
}

/** يتحقق أن اسم الحزمة آمن قبل وضعه في أمر شل. */
private val PACKAGE_REGEX = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*$")

fun isValidPackageName(name: String): Boolean = PACKAGE_REGEX.matches(name)
