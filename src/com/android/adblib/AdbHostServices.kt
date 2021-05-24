package com.android.adblib

/**
 * Exposes services specific to the ADB Server (or "host") as `suspend` functions
 *
 * The underlying implementation is responsible for creating connections to the ADB server
 * as needed, as well as ensuring resources are released when coroutines complete or
 * are cancelled.
 */
interface AdbHostServices {
    /**
     * Returns the internal version of the ADB server ("host:version" query).
     *
     * The internal version is an integer value that is incremented when newer builds of ADB
     * are incompatible with older ADB clients. This value is somewhat opaque to
     * public consumers, but this API is provided for completeness.
     */
    suspend fun version(): Int

    /**
     * Returns the list of features supported by the ADB server ("host:host-features" query).
     *
     * Note that these features may not be supported by the ADB daemon running on a device.
     * TODO: Add link to method to use to retrieve device features
     */
    suspend fun hostFeatures(): List<String>
}
