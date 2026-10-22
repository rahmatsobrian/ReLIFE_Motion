package com.siroha.relifemotion.priv

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.siroha.relifemotion.Graph
import com.siroha.relifemotion.IPrivilegedService
import com.siroha.relifemotion.data.AccessMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.TimeUnit

data class ExecResult(val code: Int, val out: String) {
    val ok: Boolean get() = code == 0
}

/** Akses non-root / Shizuku / root dalam satu antarmuka. */
object Privileged {
    private lateinit var app: Context

    var shizukuRunning by mutableStateOf(false); private set
    var shizukuGranted by mutableStateOf(false); private set
    var rootAvailable by mutableStateOf<Boolean?>(null); private set

    private var service: IPrivilegedService? = null
    private var pendingBind: CompletableDeferred<IPrivilegedService?>? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = if (binder != null && binder.pingBinder()) IPrivilegedService.Stub.asInterface(binder) else null
            service = s
            pendingBind?.complete(s)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun init(ctx: Context) {
        app = ctx.applicationContext
        try {
            Shizuku.addBinderReceivedListenerSticky { refreshShizuku() }
            Shizuku.addBinderDeadListener { shizukuRunning = false; shizukuGranted = false; service = null }
            Shizuku.addRequestPermissionResultListener { _, _ -> refreshShizuku() }
        } catch (e: Throwable) { /* Shizuku tidak ada */ }
        refreshShizuku()
    }

    fun refreshShizuku() {
        try {
            shizukuRunning = Shizuku.pingBinder()
            shizukuGranted = shizukuRunning && !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            shizukuRunning = false; shizukuGranted = false
        }
    }

    fun requestShizuku() {
        try {
            if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(4242)
        } catch (e: Throwable) { }
    }

    suspend fun probeRoot(): Boolean = withContext(Dispatchers.IO) {
        val ok = runRoot("id").let { it.ok && it.out.contains("uid=0") }
        rootAvailable = ok
        ok
    }

    private fun runRoot(cmd: String): ExecResult = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(20, TimeUnit.SECONDS)) { p.destroy(); ExecResult(-1, "timeout") }
        else ExecResult(p.exitValue(), out)
    } catch (e: Throwable) {
        ExecResult(-1, e.message ?: "su tidak tersedia")
    }

    private fun runPlain(cmd: String): ExecResult = try {
        val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        ExecResult(p.exitValue(), out)
    } catch (e: Throwable) {
        ExecResult(-1, e.message ?: "gagal")
    }

    private suspend fun shizukuService(): IPrivilegedService? {
        service?.let { if (it.asBinder().pingBinder()) return it }
        if (!shizukuGranted) return null
        val d = CompletableDeferred<IPrivilegedService?>()
        pendingBind = d
        try {
            val args = Shizuku.UserServiceArgs(ComponentName(app.packageName, PrivilegedService::class.java.name))
                .processNameSuffix("priv").daemon(false).debuggable(false).version(1)
            Shizuku.bindUserService(args, conn)
        } catch (e: Throwable) {
            return null
        }
        return withTimeoutOrNull(8000) { d.await() }
    }

    suspend fun exec(cmd: String): ExecResult = withContext(Dispatchers.IO) {
        when (Graph.settings.accessMode) {
            AccessMode.ROOT -> runRoot(cmd)
            AccessMode.SHIZUKU -> {
                val s = shizukuService()
                if (s == null) ExecResult(-1, "Shizuku belum aktif / izin belum diberikan")
                else {
                    val r = s.exec(cmd)
                    val code = r.substringBefore('\n').toIntOrNull() ?: -1
                    ExecResult(code, r.substringAfter('\n', ""))
                }
            }
            AccessMode.NONROOT -> runPlain(cmd)
        }
    }

    /** Salin berkas dari path terbatas ke cache aplikasi. */
    suspend fun copyToCache(path: String): File? = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "imports").apply { mkdirs() }
        val dest = File(dir, File(path).name)
        try {
            when (Graph.settings.accessMode) {
                AccessMode.NONROOT -> File(path).inputStream().use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                AccessMode.SHIZUKU -> {
                    val s = shizukuService() ?: return@withContext null
                    val pfd = s.openFile(path) ?: return@withContext null
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { i ->
                        dest.outputStream().use { o -> i.copyTo(o) }
                    }
                }
                AccessMode.ROOT -> {
                    val p = ProcessBuilder("su", "-c", "cat '${path.replace("'", "'\\''")}'").start()
                    p.inputStream.use { i -> dest.outputStream().use { o -> i.copyTo(o) } }
                    p.waitFor()
                }
            }
            if (dest.length() > 0) dest else null
        } catch (e: Throwable) {
            null
        }
    }

    /** Daftar isi direktori: pasangan (nama, isDir). */
    suspend fun list(path: String): List<Pair<String, Boolean>> {
        val r = exec("ls -1p '${path.replace("'", "'\\''")}'")
        if (!r.ok && r.out.isBlank()) return emptyList()
        return r.out.lines().filter { it.isNotBlank() && !it.startsWith("ls:") }
            .map { if (it.endsWith("/")) it.dropLast(1) to true else it to false }
            .sortedWith(compareBy({ !it.second }, { it.first.lowercase() }))
    }

    suspend fun grantMediaPermissions(): ExecResult {
        val pkg = app.packageName
        val perms = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            perms += listOf("READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO")
        } else {
            perms += "READ_EXTERNAL_STORAGE"
            if (Build.VERSION.SDK_INT <= 28) perms += "WRITE_EXTERNAL_STORAGE"
        }
        val cmd = perms.joinToString(" ; ") { "pm grant $pkg android.permission.$it" }
        return exec(cmd)
    }
}
