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
package com.android.adblib.impl

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException

class DevicePropertiesParserTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun parserWorks() {
        // Prepare
        val expected = """# This is some build info
# This is more build info

[ro.product.manufacturer]: [Google]
[ro.product.model]: [Pix3l]
[ro.build.version.release]: [versionX
]
[ro.build.version.release2]: [versionX
test]
[ro.build.version.release3]: [versionX
test1
test2
]
[ro.build.version.sdk]: [29]
    """.trimIndent()

        // Act
        val entries = DevicePropertiesParser().parse(expected.splitToSequence("\n"))

        // Assert
        assertEquals(6, entries.size)
        assertEquals("ro.product.manufacturer", entries[0].name)
        assertEquals("Google", entries[0].value)

        assertEquals("ro.product.model", entries[1].name)
        assertEquals("Pix3l", entries[1].value)

        assertEquals("ro.build.version.release", entries[2].name)
        assertEquals("versionX\n", entries[2].value)

        assertEquals("ro.build.version.release2", entries[3].name)
        assertEquals("versionX\ntest", entries[3].value)

        assertEquals("ro.build.version.release3", entries[4].name)
        assertEquals("versionX\ntest1\ntest2\n", entries[4].value)

        assertEquals("ro.build.version.sdk", entries[5].name)
        assertEquals("29", entries[5].value)
    }
}
