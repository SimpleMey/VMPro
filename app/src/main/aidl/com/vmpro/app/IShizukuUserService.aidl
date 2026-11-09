// Shizuku user-service interface. The implementation runs in a process Shizuku spawns with
// shell (or root) privileges, so it can `pm install` without the system install prompt.
package com.vmpro.app;

interface IShizukuUserService {
    // Shizuku reserves this transaction id to stop the service.
    void destroy() = 16777114;

    // Install the APK streamed over [apk] (size bytes). Returns "OK" or "ERR:<message>".
    String install(in ParcelFileDescriptor apk, long size) = 1;
}
