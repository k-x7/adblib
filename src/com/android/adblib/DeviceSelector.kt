package com.android.adblib

/**
 * The identifier of a device (technically a "transport" in ADB terminology) when invoking
 * a service on the ADB Server that relates to a specific device.
 */
abstract class DeviceSelector {

    abstract override fun toString(): String

    /**
     * The prefix used to invoke services on the ADB Server that relate to a specific device,
     * but does not actually connect to the device ADB daemon.
     *
     * As documented in [SERVICES.TXT](https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/SERVICES.TXT;l=5),
     * the value is one of
     *
     * + __`host-transport-id:<transport-id>`__ -
     *   This is a special form of query, where the 'host-transport-id:<transport-id>:'
     *   prefix can be used to indicate that the client is asking the ADB server
     *   for information related to a specific transport id of a device.
     *
     * + __`host-serial:<serial-number>`__ -
     *   This is a special form of query, where the 'host-serial:<serial-number>:'
     *   prefix can be used to indicate that the client is asking the ADB server
     *   for information related to a specific device.
     *
     * + __`host-usb`__ -
     *   A variant of host-serial used to target the single USB device connected
     *   to the host. This will fail if there is none or more than one.
     *
     * + __`host-local`__ -
     *   A variant of host-serial used to target the single emulator instance
     *   running on the host. This will fail if there is none or more than one.
     *
     * + __`host`__ -
     *   When asking for information related to a device, 'host:' can also be
     *   interpreted as 'any single device or emulator connected to/running on
     *   the host'.
     */
    internal abstract val hostPrefix: String

    /**
     * A prefix used to switch the socket to a direct connection to the adbd daemon running
     * on the corresponding device.
     *
     * As documented in [SERVICES.TXT](https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/SERVICES.TXT;l=5),
     * the value is one of
     *
     * * __host:transport-id:<transport-id>__ -
     *   Asks to switch the connection to the device/emulator identified by
     *   <transport-id>. After the OKAY response, every client request will
     *   be sent directly to the adbd daemon running on the device.
     *
     * * __host:transport:<serial-number>__ or __host:tport:serial:<serial-number>__ -
     *   Asks to switch the connection to the device/emulator identified by
     *   <serial-number>. After the OKAY response, every client request will
     *   be sent directly to the adbd daemon running on the device.
     *
     * * __host:transport-usb__ or __host:tport:usb__ -
     *   Asks to switch the connection to one device connected through USB
     *   to the host machine. This will fail if there are more than one such
     *   devices.
     *
     * * __host:transport-local__ or __host:tport:local__ -
     *   Asks to switch the connection to one emulator connected through TCP.
     *   This will fail if there is more than one such emulator instance
     *   running.
     *
     * * __host:transport-any__ or __host:tport:any__ -
     *   Asks to switch the connection to
     *   either the device or emulator connect to/running on the host.
     *   Will fail if there is more than one such device/emulator available.
     *
     * The __host:tport__ versions always return an 8 byte transport id in the response
     * stream.
     */
    internal abstract val transportPrefix: String

    /**
     * See [transportPrefix]: all `host:tport` prefixes result in ADB sending an 8 byte
     * transport ID in the response.
     */
    internal open val responseContainsTransportId = false

    private class SerialNumber(private val serialNumber: String) : DeviceSelector() {

        override fun toString() = "serial-$serialNumber"

        override val hostPrefix: String by lazy {
            "host-serial:$serialNumber"
        }

        override val transportPrefix: String by lazy {
            "host:transport:$serialNumber"
        }
    }

    private class TransportId(private val transportId: String) : DeviceSelector() {

        override fun toString() = "transport-$transportId"

        override val hostPrefix: String by lazy {
            "host-transport-id:$transportId"
        }

        override val transportPrefix: String by lazy {
            "host:transport-id:$transportId"
        }
    }

    private object Usb : DeviceSelector() {

        override fun toString() = "usb"

        override val hostPrefix: String
            get() = "host-usb"

        override val transportPrefix: String
            get() = "host:transport-usb"
    }

    private object Local : DeviceSelector() {

        override fun toString() = "local"

        override val hostPrefix: String
            get() = "host-local"

        override val transportPrefix: String
            get() = "host:transport-local"
    }

    private object Any : DeviceSelector() {

        override fun toString() = "any"

        override val hostPrefix: String
            get() = "host"

        override val transportPrefix: String
            get() = "host:transport-any"
    }

    /**
     * Factory for [DeviceSelector] instances.
     */
    companion object {

        /**
         * Factory for [DeviceSelector] instances that request a transport ID in the response
         * from the ADB host
         */
        val withTransportId = TransportIdDeviceSelector

        /** [DeviceSelector] for the given device serial number (see [DeviceInfo.serialNumber] */
        fun fromSerialNumber(serialNumber: String): DeviceSelector = SerialNumber(serialNumber)

        /** [DeviceSelector] for the given transport id (see [DeviceInfo.transportId] */
        fun fromTransportId(transportId: String): DeviceSelector = TransportId(transportId)

        /** [DeviceSelector] for any single device connected via usb cable */
        fun usb(): DeviceSelector = Usb

        /** [DeviceSelector] for any single device connected locally (e.g.emulator) */
        fun local(): DeviceSelector = Local

        /** [DeviceSelector] for any single connected device */
        fun any(): DeviceSelector = Any

        object TransportIdDeviceSelector {

            /** [DeviceSelector] for the given device serial number (see [DeviceInfo.serialNumber] */
            fun fromSerialNumber(serialNumber: String): DeviceSelector =
                SerialNumberWithTransportId(serialNumber)

            /** [DeviceSelector] for any single device connected via usb cable */
            fun usb(): DeviceSelector = UsbWithTransportId

            /** [DeviceSelector] for any single device connected locally (e.g.emulator) */
            fun local(): DeviceSelector = LocalWithTransportId

            /** [DeviceSelector] for any single connected device */
            fun any(): DeviceSelector = AnyWithTransportId

            private class SerialNumberWithTransportId(private val serialNumber: String) :
                DeviceSelector() {

                override fun toString() = "serial-$serialNumber"

                override val hostPrefix: String by lazy {
                    "host-serial:$serialNumber"
                }

                override val transportPrefix: String by lazy {
                    "host:tport:serial:$serialNumber"
                }

                override val responseContainsTransportId: Boolean
                    get() = true
            }

            private object UsbWithTransportId : DeviceSelector() {

                override fun toString() = "usb"

                override val hostPrefix: String
                    get() = "host-usb"

                override val transportPrefix: String
                    get() = "host:tport:usb"

                override val responseContainsTransportId: Boolean
                    get() = true
            }

            private object LocalWithTransportId : DeviceSelector() {

                override fun toString() = "local"

                override val hostPrefix: String
                    get() = "host-local"

                override val transportPrefix: String
                    get() = "host:tport:local"

                override val responseContainsTransportId: Boolean
                    get() = true
            }

            private object AnyWithTransportId : DeviceSelector() {

                override fun toString() = "any"

                override val hostPrefix: String
                    get() = "host"

                override val transportPrefix: String
                    get() = "host:tport:any"

                override val responseContainsTransportId: Boolean
                    get() = true
            }
        }
    }
}
