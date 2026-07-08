/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.remote

import android.os.DeadObjectException
import android.os.IBinder.DeathRecipient
import android.os.IInterface
import android.os.RemoteException
import java.lang.ref.WeakReference

class RemoteInterface<T : IInterface>(
    // @Throws(RemoteFileSystemException::class)
    private val creator: () -> T
) {
    private var value: T? = null

    private val lock = Any()

    private val deathRecipient = WeakDeathRecipient(this)

    fun has(): Boolean = synchronized(lock) { value != null }

    @Throws(RemoteFileSystemException::class)
    fun get(): T {
        synchronized(lock) {
            var value = value
            if (value == null) {
                value = creator()
                this.value = value
                try {
                    value.asBinder().linkToDeath(deathRecipient, 0)
                } catch (e: RemoteException) {
                    // RemoteException is thrown if remote has already died.
                    this.value = null
                    throw RemoteFileSystemException(e)
                }
            }
            return value
        }
    }

    /**
     * Invalidate the cached interface so that the next [get] re-creates it. This is used to recover
     * from the remote process dying (e.g. the Shizuku user service being torn down), after which the
     * cached binder becomes stale and any transaction on it throws [DeadObjectException].
     */
    fun invalidate() {
        synchronized(lock) {
            val value = value ?: return
            try {
                value.asBinder().unlinkToDeath(deathRecipient, 0)
            } catch (e: NoSuchElementException) {
                // The death recipient may have already been unregistered when the binder died.
            }
            this.value = null
        }
    }

    /**
     * Run [block] against the (re-)created remote interface, retrying once if the remote process has
     * died in the meantime. Binder death notifications are delivered asynchronously, so the first
     * call after the remote process dies would otherwise fail with a [DeadObjectException] before
     * [binderDied] has a chance to clear the cache. Invalidating and retrying transparently
     * re-establishes the connection.
     */
    @Throws(RemoteFileSystemException::class)
    fun <R> call(block: (T) -> R): R {
        return try {
            block(get())
        } catch (e: RemoteFileSystemException) {
            if (!isCausedByDeadObject(e)) {
                throw e
            }
            // The remote process has died; drop the stale binder and try once more with a fresh one.
            invalidate()
            block(get())
        }
    }

    /**
     * Convenience overload that runs an IPC [block] through [T.call] (with its [ParcelableException]
     * marshalling) while automatically recovering from the remote process dying. Replaces the
     * previous `get().call { ... }` idiom so that a stale binder no longer surfaces a
     * [DeadObjectException] to the caller.
     */
    @Throws(RemoteFileSystemException::class)
    fun <R> callWith(block: T.(ParcelableException) -> R): R = call { it.call(block) }

    fun isCausedByDeadObject(exception: Throwable): Boolean {
        var cause: Throwable? = exception
        while (cause != null) {
            if (cause is DeadObjectException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun binderDied() {
        synchronized(lock) {
            val value = value ?: return
            try {
                value.asBinder().unlinkToDeath(deathRecipient, 0)
            } catch (e: NoSuchElementException) {
                // The death recipient may have already been unregistered.
            }
            this.value = null
        }
    }

    protected fun finalize() {
        if (value != null) {
            // We have to do this or JavaBinder will complain about BinderProxy being destroyed
            // without unlinkToDeath() being called first.
            value!!.asBinder().unlinkToDeath(deathRecipient, 0)
            value = null
        }
    }

    // Avoid a strong reference to the BinderProxy so that it can be garbage collected.
    private class WeakDeathRecipient<T : IInterface>(
        remoteInterface: RemoteInterface<T>
    ) : DeathRecipient {
        private val weakRemoteInterface = WeakReference(remoteInterface)

        override fun binderDied() {
            weakRemoteInterface.get()?.binderDied()
        }
    }
}
