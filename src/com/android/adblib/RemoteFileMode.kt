/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.adblib

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * Abstraction over the Linux style file mode (bits) of a file on a remote device.
 *
 * @see RemoteFileMode companion object for various ways of creating instances
 */
class RemoteFileMode private constructor(
    /**
     * The Linux style `mode` bits
     */
    val modeBits: Int
) {

    override fun equals(other: Any?): Boolean {
        return (other is RemoteFileMode) && (modeBits == other.modeBits)
    }

    override fun hashCode(): Int {
        return modeBits.hashCode()
    }

    override fun toString(): String {
        return "${javaClass.simpleName}(${modeBits.toString(8)})"
    }

    /**
     * The string representation (e.g. "rwxr-x---") corresponding to this instance
     */
    val posixString: String
        get() {
            return PosixFilePermissions.toString(posixPermissions)
        }

    /**
     * The [Set] of [PosixFilePermission] corresponding to this instance
     */
    val posixPermissions: Set<PosixFilePermission>
        get() {
            return modeBitsToPosixPermissions(modeBits)
        }

    companion object {

        /**
         * Default permissions, to be used as fallback ("rw-r--r--")
         */
        val DEFAULT: RemoteFileMode
            get() = fromModeBits("644".toInt(8))

        /**
         * Returns a new [RemoteFileMode] instance initialized from Linux style file mode bits
         */
        fun fromModeBits(modeBits: Int): RemoteFileMode {
            return RemoteFileMode(modeBits)
        }

        /**
         * Returns a new [RemoteFileMode] instance initialized from multiple
         * [PosixFilePermission] values
         */
        fun fromPosixPermissions(vararg permissions: PosixFilePermission): RemoteFileMode {
            return RemoteFileMode(modeBitsFromPosixPermissions(permissions))
        }

        /**
         * Returns a new [RemoteFileMode] instance initialized from a set of
         * [PosixFilePermission] values
         */
        fun fromPosixPermissions(permissions: Set<PosixFilePermission>): RemoteFileMode {
            return fromPosixPermissions(*permissions.toTypedArray())
        }

        /**
         * Returns a new [RemoteFileMode] instance initialized from Linux style file mode
         * string, e.g. "rw-r--r"
         *
         * @throws IllegalArgumentException if the string is invalid, see
         * [PosixFilePermissions.fromString]
         */
        fun fromPosixString(value: String): RemoteFileMode {
            val permissions = PosixFilePermissions.fromString(value)
            return fromPosixPermissions(*permissions.toTypedArray())
        }

        /**
         * Returns a new [RemoteFileMode] instance initialized from the file permissions
         * of an actual file [Path].
         *
         * Returns `null` if the [Path] cannot be accessed for some
         * reason, e.g. file does not exist. Please use the [DEFAULT] value as alternative
         * if this is the case.
         *
         * Note: The returned [RemoteFileMode] may be an approximation if the underlying
         * [Path.getFileSystem] does not support [PosixFilePermission], e.g. on the Windows
         * platform.
         */
        fun fromPath(path: Path): RemoteFileMode? {
            return try {
                val permissions = Files.getPosixFilePermissions(path)
                fromPosixPermissions(permissions)
            } catch (e: UnsupportedOperationException) {
                // Some platforms (e.g. Windows) don't support posix file permissions
                fromUnsupportedFileSystem(path)
            } catch (e: Exception) {
                null
            }
        }

        internal fun fromUnsupportedFileSystem(path: Path): RemoteFileMode? {
            try {
                val permissions = HashSet<PosixFilePermission>()
                if (Files.isReadable(path)) {
                    // Default for Linux is READ for all 3 permission groups
                    permissions.add(PosixFilePermission.OWNER_READ)
                    permissions.add(PosixFilePermission.GROUP_READ)
                    permissions.add(PosixFilePermission.OTHERS_READ)
                }
                if (Files.isWritable(path)) {
                    // Default for Linux is WRITE for owner permission group only
                    permissions.add(PosixFilePermission.OWNER_WRITE)
                }
                if (Files.isExecutable(path)) {
                    // Default for Linux is EXECUTE for all 3 permission groups
                    permissions.add(PosixFilePermission.OWNER_EXECUTE)
                    permissions.add(PosixFilePermission.GROUP_EXECUTE)
                    permissions.add(PosixFilePermission.OTHERS_EXECUTE)
                }
                return if (permissions.isEmpty()) {
                    // File probably does not exist on disk or is not accessible
                    null
                } else {
                    fromPosixPermissions(permissions)
                }
            } catch (e: Exception) {
                return null
            }
        }

        private fun modeBitsFromPosixPermissions(posixPermissions: Array<out PosixFilePermission>): Int {
            var modeBits = 0
            posixPermissions.forEach { permission ->
                modeBits = modeBits or modeBitFromPosixFilePermission(permission)
            }
            return modeBits
        }

        private fun modeBitsToPosixPermissions(modeBits: Int): Set<PosixFilePermission> {
            val result = HashSet<PosixFilePermission>()
            PosixFilePermission.values().forEach { permission ->
                if ((modeBits and modeBitFromPosixFilePermission(permission)) != 0) {
                    result.add(permission)
                }
            }
            return result
        }

        private fun modeBitFromPosixFilePermission(permission: PosixFilePermission): Int {
            return when (permission) {
                PosixFilePermission.OWNER_READ -> 256
                PosixFilePermission.OWNER_WRITE -> 128
                PosixFilePermission.OWNER_EXECUTE -> 64
                PosixFilePermission.GROUP_READ -> 32
                PosixFilePermission.GROUP_WRITE -> 16
                PosixFilePermission.GROUP_EXECUTE -> 8
                PosixFilePermission.OTHERS_READ -> 4
                PosixFilePermission.OTHERS_WRITE -> 2
                PosixFilePermission.OTHERS_EXECUTE -> 1
                else -> 0
            }
        }
    }
}
