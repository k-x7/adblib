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

import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE

class RemoteFileModeTest {

    @JvmField
    @Rule
    val folder = TemporaryFolder()

    @Test
    fun defaultValueIs0644() {
        Assert.assertEquals("644".toInt(8), RemoteFileMode.DEFAULT.modeBits)
    }

    @Test
    fun fromPosixStringWorks() {
        // Act
        val fileMode = RemoteFileMode.fromPosixString("rwx------")

        // Assert
        Assert.assertEquals("700".toInt(8), fileMode.modeBits)
        Assert.assertEquals("rwx------", fileMode.posixString)
        Assert.assertEquals(
            setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE),
            fileMode.posixPermissions
        )
    }

    @Test
    fun fromModeBitsWorks() {
        // Act
        val fileMode = RemoteFileMode.fromModeBits("700".toInt(8))

        // Assert
        Assert.assertEquals("700".toInt(8), fileMode.modeBits)
        Assert.assertEquals("rwx------", fileMode.posixString)
        Assert.assertEquals(
            setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE),
            fileMode.posixPermissions
        )
    }

    @Test
    fun fromPosixPermissionsWorks() {
        // Act
        val fileMode = RemoteFileMode.fromPosixPermissions(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE)

        // Assert
        Assert.assertEquals("700".toInt(8), fileMode.modeBits)
        Assert.assertEquals("rwx------", fileMode.posixString)
        Assert.assertEquals(
            setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE),
            fileMode.posixPermissions
        )
    }

    @Test
    fun fromPosixPermissionSetWorks() {
        // Act
        val fileMode =
            RemoteFileMode.fromPosixPermissions(setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE))

        // Assert
        Assert.assertEquals("700".toInt(8), fileMode.modeBits)
        Assert.assertEquals("rwx------", fileMode.posixString)
        Assert.assertEquals(
            setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE),
            fileMode.posixPermissions
        )
    }

    @Test
    fun fromPathWorks() {
        // Prepare
        val path = folder.newFile("foo-bar.txt").toPath()

        // Act
        val fileMode = RemoteFileMode.fromPath(path)

        // Assert
        Assert.assertNotNull(fileMode)
        if (posixPermissionsSupported()) {
            Assert.assertEquals(Files.getPosixFilePermissions(path), fileMode!!.posixPermissions)
        } else {
            Assert.assertEquals(
                RemoteFileMode.fromUnsupportedFileSystem(path)!!.posixPermissions,
                fileMode!!.posixPermissions
            )
        }
    }

    @Test
    fun fromPathShouldReturnNullIfFileDoesNotExist() {
        // Prepare
        val path = folder.root.toPath().resolve("foo-bar.txt")

        // Act (Should be `null` since file does not exist)
        val fileMode = RemoteFileMode.fromPath(path)

        // Assert
        Assert.assertFalse(Files.exists(path))
        Assert.assertNull(fileMode)
    }

    private fun posixPermissionsSupported(): Boolean {
        val tempFile = folder.newFile().toPath()
        return try {
            Files.getPosixFilePermissions(tempFile)
            true
        } catch (e: UnsupportedOperationException) {
            false
        }
    }
}
