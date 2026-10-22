package com.siroha.relifemotion.priv

import android.os.ParcelFileDescriptor
import com.siroha.relifemotion.IPrivilegedService
import java.io.File
import kotlin.system.exitProcess

/** Berjalan di proses Shizuku (uid shell/root). */
class PrivilegedService : IPrivilegedService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String?): String {
        return try {
            val p = ProcessBuilder("sh", "-c", command ?: "true").redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            val code = p.waitFor()
            "$code\n$out"
        } catch (e: Throwable) {
            "-1\n${e.message}"
        }
    }

    override fun openFile(path: String?): ParcelFileDescriptor? = try {
        ParcelFileDescriptor.open(File(path ?: ""), ParcelFileDescriptor.MODE_READ_ONLY)
    } catch (e: Throwable) {
        null
    }
}
