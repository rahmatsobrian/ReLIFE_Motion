package com.siroha.relifemotion;

import android.os.ParcelFileDescriptor;

interface IPrivilegedService {
    // Transaction code wajib untuk Shizuku UserService
    void destroy() = 16777114;

    // Jalankan perintah shell (uid shell/root), hasil: "<exitCode>\n<output>"
    String exec(String command) = 1;

    // Buka berkas (mis. /sdcard/Android/data/...) sebagai file descriptor read-only
    ParcelFileDescriptor openFile(String path) = 2;
}
