/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.remote

import java8.nio.file.FileSystem
import java.io.IOException

abstract class RemoteFileSystem(
    private val remoteInterface: RemoteInterface<IRemoteFileSystem>
) : FileSystem() {
    @Throws(IOException::class)
    override fun close() {
        if (!remoteInterface.has()) {
            return
        }
        try {
            remoteInterface.get().call { exception -> close(exception) }
        } catch (e: RemoteFileSystemException) {
            // If the remote process has already died there is nothing left to close; don't relaunch
            // it just to tear the file system down.
            if (!remoteInterface.isCausedByDeadObject(e)) {
                throw e
            }
            remoteInterface.invalidate()
        }
    }
}
