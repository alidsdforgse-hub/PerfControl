package com.example.perfcontrol.core

enum class ShizukuState { NotInstalled, NotRunning, NoPermission, Ready }

fun resolveShizukuState(installed: Boolean, running: Boolean, granted: Boolean): ShizukuState = when {
    !installed -> ShizukuState.NotInstalled
    !running -> ShizukuState.NotRunning
    !granted -> ShizukuState.NoPermission
    else -> ShizukuState.Ready
}
