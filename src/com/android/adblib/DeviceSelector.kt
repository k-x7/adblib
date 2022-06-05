package com.android.adblib

/**
 * The identifier of a device (technically a "transport" in ADB terminology) when invoking
 * a service on the ADB Server that relates to a specific device.
 */
abstract class DeviceSelector {

    abstract override fun toString(): String

    /**
     * The (optional) transport ID of the device if the [DeviceSelector] used to start the
     * service specified that a transport ID should be returned on the channel.
     * `null` otherwise.
     *
     * Note: This field is only set for [DeviceSelector] instances that come from the
     * [factoryWithTransportIdTracking] factory.
     */
    open var transportId: Long?
        get() = null
        /**
         * Internal function called when the transport ID is read on the ADB protocol wire.
         */
        internal set(@Suppress("UNUSED_PARAMETER") value) {
            // Default implementation does not track transport IDs
        }

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

        override val hostPrefix: String
            get() = "host-serial:$serialNumber"

        override val transportPrefix: String
            get() = "host:transport:$serialNumber"
    }

    private class TransportId(private val value: Long) : DeviceSelector() {

        override var transportId: Long?
            get() = value
            set(@Suppress("UNUSED_PARAMETER") value) {
                // We don't track transport ID values, since we were provided one!
            }

        override fun toString() = "transport-$value"

        override val hostPrefix: String
            get() = "host-transport-id:$value"

        override val transportPrefix: String
            get() = "host:transport-id:$value"
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
         * from the ADB host, but don't track the transport ID value itself.
         */
        val factoryWithTransportId: DeviceSelectorFactory = TransportIdDeviceSelectorFactory

        /**
         * Factory for [DeviceSelector] instances that request a transport ID in the response
         * from the ADB host, and store that transport ID value in [DeviceSelector.transportId]
         * when it is received in the response.
         *
         * Note: This factory is slightly less efficient than the [factoryWithTransportId] factory,
         *       because returned instances are mutable and never shared across invocations.
         *       Returned instances are also not thread-safe and should not be used when
         *       invoking ADB services concurrently.
         */
        @JvmStatic
        val factoryWithTransportIdTracking: DeviceSelectorFactory = TransportIdTrackingDeviceSelectorFactory

        @JvmStatic
        /** [DeviceSelector] for the given device serial number (see [DeviceInfo.serialNumber] */
        fun fromSerialNumber(serialNumber: String): DeviceSelector = SerialNumber(serialNumber)

        /**
         * [DeviceSelector] for the given transport id (see [DeviceInfo.transportId]).
         *
         * Note: This can be useful to address devices that ADB detects correctly but have an
         * unknown serial number, i.e. devices not yet fully booted or in recovery mode.
         */
        @JvmStatic
        fun fromTransportId(transportId: Long): DeviceSelector = TransportId(transportId)

        /** [DeviceSelector] for any single device connected via usb cable */
        @JvmStatic
        fun usb(): DeviceSelector = Usb

        /** [DeviceSelector] for any single device connected locally (e.g.emulator) */
        @JvmStatic
        fun local(): DeviceSelector = Local

        /** [DeviceSelector] for any single connected device */
        @JvmStatic
        fun any(): DeviceSelector = Any

        interface DeviceSelectorFactory {
            /** [DeviceSelector] for the given device serial number (see [DeviceInfo.serialNumber] */
            fun fromSerialNumber(serialNumber: String): DeviceSelector
            /** [DeviceSelector] for any single device connected via usb cable */
            fun usb(): DeviceSelector

            /** [DeviceSelector] for any single device connected locally (e.g.emulator) */
            fun local(): DeviceSelector

            /** [DeviceSelector] for any single connected device */
            fun any(): DeviceSelector
        }

        internal object TransportIdDeviceSelectorFactory : DeviceSelectorFactory {

            /** [DeviceSelector] for the given device serial number (see [DeviceInfo.serialNumber] */
            override fun fromSerialNumber(serialNumber: String): DeviceSelector =
                SerialNumberWithTransportId(serialNumber)

            /** [DeviceSelector] for any single device connected via usb cable */
            override fun usb(): DeviceSelector = UsbWithTransportId

            /** [DeviceSelector] for any single device connected locally (e.g.emulator) */
            override fun local(): DeviceSelector = LocalWithTransportId

            /** [DeviceSelector] for any single connected device */
            override fun any(): DeviceSelector = AnyWithTransportId

            private class SerialNumberWithTransportId(private val serialNumber: String) :
                DeviceSelector() {

                override fun toString() = "serial-$serialNumber"

                override val hostPrefix: String
                    get() = "host-serial:$serialNumber"

                override val transportPrefix: String
                    get() = "host:tport:serial:$serialNumber"

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

        internal object TransportIdTrackingDeviceSelectorFactory : DeviceSelectorFactory {

            /** [DeviceSelector] for the given device serial number (see [DeviceInfo.serialNumber] */
            override fun fromSerialNumber(serialNumber: String): DeviceSelector =
                TrackingDeviceSelector(factoryWithTransportId.fromSerialNumber(serialNumber))

            /** [DeviceSelector] for any single device connected via usb cable */
            override fun usb(): DeviceSelector = TrackingDeviceSelector(factoryWithTransportId.usb())

            /** [DeviceSelector] for any single device connected locally (e.g.emulator) */
            override fun local(): DeviceSelector = TrackingDeviceSelector(factoryWithTransportId.local())

            /** [DeviceSelector] for any single connected device */
            override fun any(): DeviceSelector = TrackingDeviceSelector(factoryWithTransportId.any())

            private class TrackingDeviceSelector(private val delegate: DeviceSelector) : DeviceSelector() {

                override var transportId: Long? = null

                override fun toString() = delegate.toString()

                override val hostPrefix: String
                    get() = delegate.hostPrefix

                override val transportPrefix: String
                    get() = delegate.transportPrefix

                override val responseContainsTransportId: Boolean
                    get() = delegate.responseContainsTransportId
            }
        }
    }
}
