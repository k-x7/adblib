package com.android.adblib

/**
 * The identifier of a device (technically a "transport" in ADB terminology) when invoking
 * a service on the ADB Server that relates to a specific device.
 */
abstract class DeviceSelector {
    /**
     * The prefix used to invoke services on the ADB Server that relate to a specific device,
     * but does not actually connect to the device ADB daemon.
     *
     * The value is one of
     *
     * ```
     * host-transport-id:<transport-id>
     *   This is a special form of query, where the 'host-transport-id:<transport-id>:'
     *   prefix can be used to indicate that the client is asking the ADB server
     *   for information related to a specific transport id of a device.
     *
     * host-serial:<serial-number>
     *   This is a special form of query, where the 'host-serial:<serial-number>:'
     *   prefix can be used to indicate that the client is asking the ADB server
     *   for information related to a specific device.
     *
     * host-usb
     *   A variant of host-serial used to target the single USB device connected
     *   to the host. This will fail if there is none or more than one.
     *
     * host-local
     *   A variant of host-serial used to target the single emulator instance
     *   running on the host. This will fail if there is none or more than one.
     *
     * host
     *   When asking for information related to a device, 'host:' can also be
     *   interpreted as 'any single device or emulator connected to/running on
     *   the host'.
     * ```
     */
    internal abstract val hostPrefix: String

    private class SerialNumber(private val serialNumber: String) : DeviceSelector() {
        override val hostPrefix: String by lazy {
            "host-serial:$serialNumber"
        }
    }

    private class TransportId(private val transportId: String) : DeviceSelector() {
        override val hostPrefix: String by lazy {
            "host-transport-id:$transportId"
        }
    }

    private object Usb : DeviceSelector() {
        override val hostPrefix: String
            get() = "host-usb"
    }

    private object Local : DeviceSelector() {
        override val hostPrefix: String
            get() = "host-local"
    }

    private object Any : DeviceSelector() {
        override val hostPrefix: String
            get() = "host"
    }

    /**
     * Factory for [DeviceSelector] instances.
     */
    companion object {
        /** [DeviceSelector] for the given device serial number (see [DeviceInfo.serialNumber] */
        fun fromSerialNumber(serialNumber: String): DeviceSelector {
            return SerialNumber(serialNumber)
        }
        /** [DeviceSelector] for the given transport id (see [DeviceInfo.transportId] */
        fun fromTransportId(transportId: String): DeviceSelector {
            return TransportId(transportId)
        }
        /** [DeviceSelector] for any single device connected via usb cable */
        fun usb(): DeviceSelector {
            return Usb
        }
        /** [DeviceSelector] for any single device connected locally (e.g.emulator) */
        fun local(): DeviceSelector {
            return Local
        }
        /** [DeviceSelector] for any single connected device */
        fun any(): DeviceSelector {
            return Any
        }
    }
}
