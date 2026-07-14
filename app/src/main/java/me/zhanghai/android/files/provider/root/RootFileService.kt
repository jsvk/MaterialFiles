/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.root

import android.annotation.SuppressLint
import android.content.Context
import android.os.Process
import android.util.Log
import me.zhanghai.android.files.BuildConfig
import me.zhanghai.android.files.provider.FileSystemProviders
import me.zhanghai.android.files.provider.remote.RemoteFileService
import me.zhanghai.android.files.provider.remote.RemoteInterface
import me.zhanghai.android.files.util.lazyReflectedMethod

/**
 * Whether the current process is the privileged remote file service helper process (either the
 * root process spawned by libsu, or the Shizuku user service process).
 *
 * This must NOT be determined solely by [Process.myUid] being 0: the libsu root process runs as
 * uid 0, but the Shizuku user service process runs as the shell uid (2000). In both cases there is
 * no full application setup (e.g. [me.zhanghai.android.files.app.application] is never initialized
 * because [me.zhanghai.android.files.app.AppProvider] does not run), so code paths that require it
 * must be skipped. The flag is set in [RootFileService.main] before any provider is installed.
 */
@Volatile
var isRunningAsRemoteFileService = false
    internal set

val isRunningAsRoot: Boolean
    get() = isRunningAsRemoteFileService || Process.myUid() == 0

@SuppressLint("StaticFieldLeak")
lateinit var rootContext: Context private set

object RootFileService : RemoteFileService(
    RemoteInterface {
        if (ShizukuFileServiceLauncher.isShizukuAvailable()) {
            ShizukuFileServiceLauncher.launchService()
        } else {
            LibSuFileServiceLauncher.launchService()
        }
    }
) {
    const val TIMEOUT_MILLIS = 15 * 1000L

    private val LOG_TAG = RootFileService::class.java.simpleName

    // Not actually restricted because there's no restriction when running as root.
    //@RestrictedHiddenApi
    private val activityThreadCurrentActivityThreadMethod by lazyReflectedMethod(
        "android.app.ActivityThread", "currentActivityThread"
    )
    //@RestrictedHiddenApi
    private val activityThreadGetSystemContextMethod by lazyReflectedMethod(
        "android.app.ActivityThread", "getSystemContext"
    )

    fun main() {
        // Mark this process as the remote file service helper before installing anything, so that
        // code depending on the (uninitialized) application context is skipped. This is required for
        // the Shizuku user service process, which runs as the shell uid (2000) rather than uid 0 and
        // so is not detected by Process.myUid() == 0.
        isRunningAsRemoteFileService = true
        Log.i(LOG_TAG, "Creating package context")
        rootContext = createPackageContext(BuildConfig.APPLICATION_ID)
        Log.i(LOG_TAG, "Installing file system providers")
        FileSystemProviders.install()
        FileSystemProviders.overflowWatchEvents = true
    }

    private fun createPackageContext(packageName: String): Context {
        val activityThread = activityThreadCurrentActivityThreadMethod.invoke(null)
        val systemContext = activityThreadGetSystemContextMethod.invoke(activityThread) as Context
        return systemContext.createPackageContext(
            packageName, Context.CONTEXT_IGNORE_SECURITY or Context.CONTEXT_INCLUDE_CODE
        )
    }
}
