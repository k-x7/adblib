# Overview
`adblib` is part of the platform tools source code used to build Android Studio or the Intellij IDE, it can be found at the [AOSP](https://android.googlesource.com/platform/tools/base/) repository.


Read [README.origin.md](https://github.com/k-x7/adblib/blob/master/README.origin.md) for more information about `adblib`.

# Goal
The goal of this repository is to be able to build `adblib` without having to full clone the platform repository, it only replaces `bazel` files to use non-local dependencies that are usually shipped with the platform tools.


# Build

```shell

bazel build //:adblib

```

# Example of use

This example will use `adblib` to track all new devices connected through ADB, the same as `adb track-devices`:

```kotlin
import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.adblib.trackDevices
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val host = AdbSessionHost()
    val session = AdbSession.create(host)
    val devicesFlow = session.trackDevices()
    runBlocking {
        devicesFlow.collect{ deviceList ->
            println("### Change in state ###")
            deviceList.devices.forEach{
                    device -> println("${device.model}:  ${device.serialNumber}: ${device.deviceState}")
            }
        }
    }
}

```


the output, while connecting and disconnecting a Pixel 5 phone:

```shell
### Change in state ###
### Change in state ###
Pixel_5:  XXXXXXXXXXXXXX: ONLINE
### Change in state ###
Pixel_5:  XXXXXXXXXXXXXX: OFFLINE
### Change in state ###

```

# Disclaimer

I am not the owner nor the maintainer of the [source code](https://android.googlesource.com/platform/tools/base/+/refs/heads/mirror-goog-studio-main/adblib/) of `adblib`.
I just want to use the library outside of the platform tools, and currently there is no release of it as a standalone library.

If you are having trouble building the library, please open an issue in this repository. Otherwise, contact the OWNERS or report a bug in the [official bug tracker](https://issuetracker.google.com/issues?q=componentid:192708%20status:open)
