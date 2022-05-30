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
 * A [List] of [E] elements in addition to an arbitrary list of [ErrorLine] entries.
 */
class ListWithErrors<E>(
    /**
     * The list of entries that were successfully parsed.
     */
    val entries: List<E>,
    /**
     * List of [ErrorLine] corresponding to entries that were not recognized.
     */
    val errors: List<ErrorLine>
) : List<E> by entries {

    override fun toString(): String {
        return entries.joinToString(", ", "[", "]") +
                " - " +
                errors.joinToString(", ", "[", "]")
    }

    class Builder<E> {

        private val entries: MutableList<E> = ArrayList()
        private val errors: MutableList<ErrorLine> = ArrayList()

        fun addEntry(entry: E) {
            entries.add(entry)
        }

        fun addError(message: String, lineIndex: Int, rawLineText: CharSequence) {
            errors.add(ErrorLine(message, lineIndex, rawLineText.toString()))
        }

        fun addError(error: ErrorLine) {
            errors.add(error)
        }

        fun build(): ListWithErrors<E> {
            return ListWithErrors(entries, errors)
        }
    }
}

fun <T> emptyListWithErrors(): ListWithErrors<T> = ListWithErrors.Builder<T>().build()

/**
 * An error collected from a parser producing a [ListWithErrors]
 */
class ErrorLine(
    /**
     * An arbitrary error message describing this error.
     */
    val message: String,
    /**
     * The zero-based line number where the error occurred.
     */
    val lineIndex: Int,
    /**
     * The raw text that was the source of this error.
     */
    val rawLineText: String
)
