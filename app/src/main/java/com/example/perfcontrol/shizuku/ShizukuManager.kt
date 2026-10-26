package com.example.perfcontrol.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.example.perfcontrol.IShellService
import com.example.perfcontrol.core.ShizukuState
import com.example.perfcontrol.core.resolveShizukuState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

class ShizukuManager(private val context: Context) {
    private val _state = MutableStateFlow(compute())
    val state: StateFlow<ShizukuState> = _state

    @Volatile
    private var service: IShellService? = null
    private var binding = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IShellService.Stub.asInterface(binder)
            binding = false
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            binding = false
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
    }

    fun currentService(): IShellService? = service

    fun refresh() {
        _state.value = compute()
        if (_state.value == ShizukuState.Ready) bind()
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Exception) {
        }
    }

    private fun bind() {
        if (service != null || binding) return
        try {
            binding = true
            val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(false)
                .version(1)
            Shizuku.bindUserService(args, connection)
        } catch (_: Exception) {
            binding = false
        }
    }

    private fun compute(): ShizukuState {
        val installed = try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
        val running = try { Shizuku.pingBinder() } catch (_: Exception) { false }
        val granted = running && try {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        return resolveShizukuState(installed, running, granted)
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val REQUEST_CODE = 4242
    }
}
