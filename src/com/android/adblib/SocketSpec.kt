/*
 * Copyright (C) 2022 The Android Open Source Project
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

/**
 * The format specification of a socket that ADB listens from when forwarding socket connections
 * (see [AdbHostServices.forward].
 *
 * See [ADB source code](https://cs.android.com/android/platform/superproject/+/c9877a99c1ade78c9aa6ac67b8059c20a7bf5ed2:packages/modules/adb/socket_spec.cpp;l=330)
 * for reference.
 *
 * * `tcp:<port>` (may be `tcp:0` to pick any open port)
 * * `localabstract:<unix domain socket name>`
 * * `localreserved:<unix domain socket name>`
 * * `localfilesystem:<unix domain socket name>`
 * * `dev:<character device name>
 * * `jdwp:<process pid>` (remote only)
 * * `vsock:<CID>:<port>` (remote only)
 * * `local:path:` Unix local domain socket on `path`
 * * `acceptfd:<fd>` (listen only)
 */
abstract class SocketSpec {

    /**
     * Returns the string to use in the underlying ADB protocol command/query
     */
    abstract fun toQueryString(): String

    override fun toString(): String {
        return "socket-spec: " + toQueryString()
    }

    /**
     * A TCP connection, either local or remote. If this is used for a "server" socket, [port] can
     * be zero, in which case ADB server returns the actual port the server socket has been
     * bound to.
     */
    class Tcp(public val port: Int = 0) : SocketSpec() {

        override fun toQueryString(): String {
            return "tcp:$port"
        }
    }

    /**
     * A VSock connection, either local or remote.
     *
     * Note: This should only be used as the "remote" parameter of [AdbHostServices.forward].
     */
    class VSock(public val cid: Int, public val port: Int) : SocketSpec() {

        override fun toQueryString(): String {
            return "vsock:$cid:$port"
        }
    }

    /**
     * A server socket, already opened as a file descriptor [fd].
     *
     * Note: This should only be used as the "local" parameter of [AdbHostServices.forward].
     */
    class AcceptFD(public val fd: Int) : SocketSpec() {

        override fun toQueryString(): String {
            return "acceptfd:$fd"
        }
    }

    class Local(public val path: String) : SocketSpec() {

        override fun toQueryString(): String {
            return "local:$path"
        }
    }

    class LocalAbstract(public val name: String) : SocketSpec() {

        override fun toQueryString(): String {
            return "localabstract:$name"
        }
    }

    class LocalReserved(public val name: String) : SocketSpec() {

        override fun toQueryString(): String {
            return "localreserved:$name"
        }
    }

    class LocalFileSystem(public val name: String) : SocketSpec() {

        override fun toQueryString(): String {
            return "localfilesystem:$name"
        }
    }

    /**
     * A [SocketSpec] passed unchanged to/from the ADB server. This is useful
     * to represent [SocketSpec] not currently supported by other [SocketSpec]
     * classes.
     */
    class PassThrough(val value: String) : SocketSpec() {

        override fun toQueryString(): String {
            return value
        }
    }

    /**
     * Attaches to the JDWP debugging session of the process [pid].
     *
     * Note: This should only be used as the "remote" parameter of [AdbHostServices.forward].
     */
    class Jdwp(public val pid: Int) : SocketSpec() {

        override fun toQueryString(): String {
            return "jdwp:$pid"
        }
    }

    /**
     * Invokes any [service] on the remote device, for example `track-jdwp`.
     *
     * Note: This should only be used as the "remote" parameter of [AdbHostServices.forward].
     */
    class Service(private val service: String): SocketSpec() {

        override fun toQueryString(): String {
            return service
        }
    }

    companion object {

        /**
         * Returns a [SocketSpec] instance given a string representation of the format
         * (see [toQueryString]).
         */
        fun fromQueryString(value: String): SocketSpec {
            val colonIndex = value.indexOf(":")
            if (colonIndex < 0) {
                return PassThrough(value)
            }
            val prefix = value.substring(0, colonIndex)
            val suffix = value.substring(colonIndex + 1)
            return when (prefix) {
                "tcp" -> {
                    val port = suffix.toIntOrNull()
                    return if (port == null) PassThrough(value) else Tcp(port)
                }
                "vsock" -> {
                    val suffixColonIndex = suffix.indexOf(":")
                    if (suffixColonIndex < 0) {
                        return PassThrough(value)
                    }
                    val cid = suffix.substring(0, suffixColonIndex).toIntOrNull()
                    val port = suffix.substring(suffixColonIndex + 1).toIntOrNull()
                    return if (cid == null || port == null) {
                        PassThrough(value)
                    } else {
                        VSock(cid, port)
                    }
                }
                "acceptfd" -> {
                    val fd = suffix.toIntOrNull()
                    return if (fd == null) PassThrough(value) else AcceptFD(fd)
                }
                "local" -> {
                    return Local(suffix)
                }
                "localabstract" -> {
                    return LocalAbstract(suffix)
                }
                "localreserved" -> {
                    return LocalReserved(suffix)
                }
                "localfilesystem" -> {
                    return LocalFileSystem(suffix)
                }
                "jdwp" -> {
                    val pid = suffix.toIntOrNull()
                    return if (pid == null) PassThrough(value) else Jdwp(pid)
                }
                else -> {
                    PassThrough(value)
                }
            }
        }
    }
}
