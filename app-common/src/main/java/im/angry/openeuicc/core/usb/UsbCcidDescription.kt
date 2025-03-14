package im.angry.openeuicc.core.usb

import java.nio.ByteBuffer
import java.nio.ByteOrder

@Suppress("unused")
data class UsbCcidDescription(
    private val bMaxSlotIndex: Byte,
    private val bVoltageSupport: Byte,
    private val dwProtocols: Int,
    private val dwFeatures: Int
) {
    companion object {
        private const val DESCRIPTOR_LENGTH: Byte = 0x36
        private const val DESCRIPTOR_TYPE: Byte = 0x21

        // dwFeatures Masks
        private const val FEATURE_AUTOMATIC_VOLTAGE = 0x00008
        private const val FEATURE_AUTOMATIC_PPS = 0x00080

        private const val FEATURE_EXCHANGE_LEVEL_TPDU = 0x10000
        private const val FEATURE_EXCHANGE_LEVEL_SHORT_APDU = 0x20000
        private const val FEATURE_EXCHANGE_LEVEL_EXTENDED_APDU = 0x40000

        // bVoltageSupport Masks
        private const val VOLTAGE_5V0: Byte = 1
        private const val VOLTAGE_3V0: Byte = 2
        private const val VOLTAGE_1V8: Byte = 4

        private const val SLOT_OFFSET = 4
        private const val FEATURES_OFFSET = 40
        private const val MASK_T0_PROTO = 1
        private const val MASK_T1_PROTO = 2

        fun fromRawDescriptors(desc: ByteArray): UsbCcidDescription? {
            var dwProtocols = 0
            var dwFeatures = 0
            var bMaxSlotIndex: Byte = 0
            var bVoltageSupport: Byte = 0

            var hasCcidDescriptor = false

            val byteBuffer = ByteBuffer.wrap(desc).order(ByteOrder.LITTLE_ENDIAN)

            while (byteBuffer.hasRemaining()) {
                byteBuffer.mark()
                val len = byteBuffer.get()
                val type = byteBuffer.get()
                if (type == DESCRIPTOR_TYPE && len == DESCRIPTOR_LENGTH) {
                    byteBuffer.reset()
                    byteBuffer.position(byteBuffer.position() + SLOT_OFFSET)
                    bMaxSlotIndex = byteBuffer.get()
                    bVoltageSupport = byteBuffer.get()
                    dwProtocols = byteBuffer.int
                    byteBuffer.reset()
                    byteBuffer.position(byteBuffer.position() + FEATURES_OFFSET)
                    dwFeatures = byteBuffer.int
                    hasCcidDescriptor = true
                    break
                } else {
                    byteBuffer.position(byteBuffer.position() + len - 2)
                }
            }

            return if (hasCcidDescriptor) {
                UsbCcidDescription(bMaxSlotIndex, bVoltageSupport, dwProtocols, dwFeatures)
            } else {
                null
            }
        }
    }

    enum class Voltage(powerOnValue: Int, mask: Int) {
        // @formatter:off
        AUTO(0, 0),
        V50(1, VOLTAGE_5V0.toInt()),
        V30(2, VOLTAGE_3V0.toInt()),
        V18(3, VOLTAGE_1V8.toInt());
        // @formatter:on

        val mask = powerOnValue.toByte()
        val powerOnValue = mask.toByte()
    }

    private fun hasFeature(feature: Int) = (dwFeatures and feature) != 0

    val voltages: List<Voltage>
        get() {
            if (hasFeature(FEATURE_AUTOMATIC_VOLTAGE)) return listOf(Voltage.AUTO)
            return Voltage.entries.filter { (it.mask.toInt() and bVoltageSupport.toInt()) != 0 }
        }

    val hasAutomaticPps: Boolean
        get() = hasFeature(FEATURE_AUTOMATIC_PPS)

    val hasT0Protocol: Boolean
        get() = (dwProtocols and MASK_T0_PROTO) != 0
}