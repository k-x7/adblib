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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketSpecTest {

    @Test
    fun toQueryStringWorks() {
        // Act
        val tcp = SocketSpec.Tcp(5)
        val vsock = SocketSpec.VSock(10, 1)
        val acceptfd = SocketSpec.AcceptFD(5)
        val local = SocketSpec.Local("foo/bar")
        val localReserved = SocketSpec.LocalReserved("foo/bar/res")
        val localFileSystem = SocketSpec.LocalFileSystem("foo/bar/fs")
        val localAbstract = SocketSpec.LocalAbstract("foo/bar/abs")
        val passThrough = SocketSpec.PassThrough("this-is-some-text")

        // Assert
        assertEquals("tcp:5", tcp.toQueryString())
        assertEquals("vsock:10:1", vsock.toQueryString())
        assertEquals("acceptfd:5", acceptfd.toQueryString())
        assertEquals("local:foo/bar", local.toQueryString())
        assertEquals("localreserved:foo/bar/res", localReserved.toQueryString())
        assertEquals("localfilesystem:foo/bar/fs", localFileSystem.toQueryString())
        assertEquals("localabstract:foo/bar/abs", localAbstract.toQueryString())
        assertEquals("this-is-some-text", passThrough.toQueryString())
    }

    @Test
    fun fromQueryStringWorks() {
        // Act
        val tcp = SocketSpec.fromQueryString("tcp:5")
        val vsock = SocketSpec.fromQueryString("vsock:10:5")
        val acceptfd = SocketSpec.fromQueryString("acceptfd:10")
        val local = SocketSpec.fromQueryString("local:foo/bar")
        val localReserved = SocketSpec.fromQueryString("localreserved:foo/bar/res")
        val localFileSystem = SocketSpec.fromQueryString("localfilesystem:foo/bar/fs")
        val localAbstract = SocketSpec.fromQueryString("localabstract:foo/bar/abs")
        val passThrough = SocketSpec.fromQueryString("this-is-some-text")

        // Assert
        assertTrue((tcp is SocketSpec.Tcp) && (tcp.port == 5))
        assertTrue((vsock is SocketSpec.VSock) && (vsock.cid == 10) && (vsock.port == 5))
        assertTrue((acceptfd is SocketSpec.AcceptFD) && (acceptfd.fd == 10))
        assertTrue((local is SocketSpec.Local) && (local.path == "foo/bar"))
        assertTrue((localReserved is SocketSpec.LocalReserved) && (localReserved.name == "foo/bar/res"))
        assertTrue((localFileSystem is SocketSpec.LocalFileSystem) && (localFileSystem.name == "foo/bar/fs"))
        assertTrue((localAbstract is SocketSpec.LocalAbstract) && (localAbstract.name == "foo/bar/abs"))
        assertTrue((passThrough is SocketSpec.PassThrough) && (passThrough.value == "this-is-some-text"))
    }
}
