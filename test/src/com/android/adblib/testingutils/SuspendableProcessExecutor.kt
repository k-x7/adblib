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
package com.android.adblib.testingutils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * A coroutine friendly implementation of executing a process and collecting its
 * `stdout`, `stderr` and exit code.
 */
class SuspendableProcessExecutor {

    suspend fun execute(cmd: List<String>): ProcessResult {
        return coroutineScope {
          val process = startProcess(cmd)
          try {
            val stdout = async { collectStream(process.inputStream) }
            val stderr = async { collectStream(process.errorStream) }
            val exitCode = async { waitForProcessEnd(process) }

            // Await all coroutines to ensure any exception is thrown as soon as possible
            awaitAll(stdout, stderr, exitCode)

            ProcessResult(stdout.await(), stderr.await(), exitCode.await())
          } catch (t: Throwable) {
            runCatching {
              if (process.isAlive) {
                process.destroyForcibly()
              }
            }.onFailure {
              t.addSuppressed(it)
            }
            throw t
          }
        }
    }

    private suspend fun startProcess(cmd: List<String>): Process {
        return withContext(Dispatchers.IO) {
          ProcessBuilder().command(cmd).start()
        }
    }

    private suspend fun waitForProcessEnd(process: Process): Int {
        return withContext(Dispatchers.IO) {
          while (process.isAlive) {
            coroutineContext.ensureActive()
            process.waitFor(10, TimeUnit.MILLISECONDS)
          }

          // Return the process exit code
          process.exitValue()
        }
    }

    private suspend fun collectStream(stream: InputStream): List<String> {
        return withContext(Dispatchers.IO) {
          InputStreamReader(stream, StandardCharsets.UTF_8).use {
            BufferedReader(it).use { reader ->
              val lines = mutableListOf<String>()
              reader.lines().forEach { line ->
                coroutineContext.ensureActive()
                lines.add(line)
              }
              lines
            }
          }
        }
    }

    class ProcessResult(
        val stdout: List<String>,
        val stderr: List<String>,
        val exitCode: Int,
    )
}
