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
package com.android.adblib.utils

/**
 * Helper class to allow collecting a list of [Throwable] for the purpose of recording
 * [Throwable.suppressedExceptions]
 */
object SuppressedExceptions {

    fun init(): List<Throwable> {
        return emptyList()
    }

    fun add(existing: List<Throwable>, e: Throwable): List<Throwable> {
        return if (existing.isEmpty()) {
            ArrayList<Throwable>().apply { add(e) }
        } else {
            (existing as ArrayList<Throwable>).apply { add(e) }
        }
    }
}

/**
 * Record a list of [Throwable] as [Throwable.suppressedExceptions]
 */
fun <T : Exception> T.withSuppressed(suppressedException: List<Throwable>?): T {
    suppressedException?.forEach { addSuppressed(it) }
    return this
}
