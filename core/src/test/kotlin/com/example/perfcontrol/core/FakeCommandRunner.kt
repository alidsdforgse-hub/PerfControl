package com.example.perfcontrol.core

class FakeCommandRunner(
    val responses: MutableMap<String, CommandResult> = mutableMapOf(),
    private val handler: ((String) -> CommandResult?)? = null,
) : CommandRunner {
    val log = mutableListOf<String>()

    override suspend fun run(command: String): CommandResult {
        log += command
        return handler?.invoke(command) ?: responses[command] ?: CommandResult.Failure("unmapped")
    }
}

class MemoryStore : StateStore {
    val map = mutableMapOf<String, String>()
    override fun saveOriginal(pkg: String, mode: String) { map[pkg] = mode }
    override fun original(pkg: String): String? = map[pkg]
    override fun clear(pkg: String) { map.remove(pkg) }
}
