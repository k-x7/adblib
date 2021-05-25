package com.android.adblib

/**
 * The state of a device.
 *
 * See [states](https://cs.android.com/android/platform/superproject/+/790d619575aea7032a4fe5f097d412adedf6623b:packages/modules/adb/transport.cpp;l=1132)
 */
@Suppress("unused", "SpellCheckingInspection")
enum class DeviceState(val state: String) {

    /**
     * Device is fully online and available to perform most services, such as installing
     * an application, debugging, etc.
     */
    ONLINE("device"),

    /**
     * Device is connected but not generally available yet
     */
    OFFLINE("offline"),
    BOOTLOADER("bootloader"),
    HOST("host"),
    RECOVERY("recovery"),
    RESCUE("rescue"),

    /**
     * TODO: This state is actually a short sentence
     *
     * See [no permissions](https://cs.android.com/android/platform/superproject/+/790d619575aea7032a4fe5f097d412adedf6623b:packages/modules/adb/transport.cpp;l=1148)
     */
    NO_PERMISSIONS("no permissions"),

    /**
     * Device is in "sideload" state either through `adb sideload` or recovery menu
     */
    SIDELOAD("sideload"),
    UNAUTHORIZED("unauthorized"),
    AUTHORIZING("authorizing"),
    CONNECTING("connecting"),

    /**
     * Unknown state, i.e. a state that we somehow did not recognize
     */
    UNKNOWN("unkonwn"),

    /**
     * bootloader mode with is-userspace = true though `adb reboot fastboot`
     */
    FASTBOOTD("fastbootd"),
    DISCONNECTED("disconnected");

    companion object {
        /**
         * Returns a [DeviceState] from the string returned by `adb devices`.
         *
         * @param state the device state.
         * @return a [DeviceState] object or `null` if the state is unknown.
         */
        fun parseStateOrNull(state: String): DeviceState? {
            for (deviceState in values()) {
                if (deviceState.state == state) {
                    return deviceState
                }
            }
            return null
        }

        /**
         * Returns a [DeviceState] from the string returned by `adb devices`.
         *
         * @param state the device state.
         * @return a [DeviceState] object or [UNKNOWN] if the state is unknown.
         */
        fun parseState(state: String): DeviceState {
            return parseStateOrNull(state) ?: UNKNOWN
        }
    }
}
