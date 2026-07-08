/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.remote

import java8.nio.file.FileSystem
import me.zhanghai.android.files.provider.common.PosixFileAttributeView
import me.zhanghai.android.files.provider.common.PosixFileStore

abstract class RemoteFileService(private val remoteInterface: RemoteInterface<IRemoteFileService>) {
    @Throws(RemoteFileSystemException::class)
    fun getRemoteFileSystemProviderInterface(scheme: String): IRemoteFileSystemProvider =
        remoteInterface.callWith { getRemoteFileSystemProviderInterface(scheme) }

    @Throws(RemoteFileSystemException::class)
    fun getRemoteFileSystemInterface(fileSystem: FileSystem): IRemoteFileSystem =
        remoteInterface.callWith { getRemoteFileSystemInterface(fileSystem.toParcelable()) }

    @Throws(RemoteFileSystemException::class)
    fun getRemotePosixFileStoreInterface(fileStore: PosixFileStore): IRemotePosixFileStore =
        remoteInterface.callWith { getRemotePosixFileStoreInterface(fileStore.toParcelable()) }

    @Throws(RemoteFileSystemException::class)
    fun getRemotePosixFileAttributeViewInterface(
        attributeView: PosixFileAttributeView
    ): IRemotePosixFileAttributeView =
        remoteInterface.callWith {
            getRemotePosixFileAttributeViewInterface(attributeView.toParcelable())
        }

    /**
     * Invalidate the cached top-level remote file service so that it is relaunched on next use.
     * Used to recover after the remote (root / Shizuku) process has died.
     */
    fun invalidateRemoteInterface() {
        remoteInterface.invalidate()
    }
}
