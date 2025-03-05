package im.angry.openeuicc.util

import android.util.Log
import im.angry.openeuicc.core.ApduInterfaceAtrProvider
import im.angry.openeuicc.core.EuiccChannel
import net.typeblog.lpac_jni.ApduInterface
import net.typeblog.lpac_jni.Version

data class EuiccVendorInfo(
    val skuName: String? = null,
    val serialNumber: String? = null,
    val firmwareVersion: String? = null,
    val region: Int? = null,
)

private val EUICC_VENDORS: Array<EuiccVendor> = arrayOf(ESTKme(), SIMLink())
private val EUICC_COMPAT_VENDORS: Array<EuiccVendor> = arrayOf(ESTKme())


fun tryParseEuiccVendorInfo(iface: ApduInterface, isdrAid: ByteArray?): EuiccVendorInfo? =
    EUICC_VENDORS.firstNotNullOfOrNull { it.tryParseEuiccVendorInfo(iface, isdrAid) }

fun EuiccChannel.tryParseEuiccVendorInfo(): EuiccVendorInfo? =
    EUICC_VENDORS.firstNotNullOfOrNull { it.tryParseEuiccVendorInfo(this) }

interface EuiccVendor {
    fun tryParseEuiccVendorInfo(channel: EuiccChannel): EuiccVendorInfo?
    fun tryParseEuiccVendorInfo(iface: ApduInterface, isdrAid: ByteArray?): EuiccVendorInfo?
}

private class ESTKme : EuiccVendor {
    companion object {
        private val PRODUCT_AID = "A06573746B6D65FFFFFFFFFFFF6D6774".decodeHex()
    }

    private fun checkAtr(channel: EuiccChannel): Boolean =
        (channel.apduInterface as? ApduInterfaceAtrProvider)
            ?.atr?.decodeToString()?.contains("estk.me")
            ?: false

    private fun decodeAsn1String(b: ByteArray): String? {
        if (b.size < 2) return null
        if (b[b.size - 2] != 0x90.toByte() || b[b.size - 1] != 0x00.toByte()) return null
        return b.sliceArray(0 until b.size - 2).decodeToString()
    }

    fun decodeAsn1Int(b: ByteArray): Int? {
        if (b.size < 2) return null
        if (b[b.size - 2] != 0x90.toByte() || b[b.size - 1] != 0x00.toByte()) return null
        val bytes = b.sliceArray(0 until b.size - 2)
        if (bytes.size > 4) return null
        var result = 0
        for (i in bytes.indices) {
            result = result or ((bytes[i].toInt() and 0xFF) shl (8 * i))
        }
        return result
    }

    override fun tryParseEuiccVendorInfo(
        iface: ApduInterface,
        isdrAid: ByteArray?
    ): EuiccVendorInfo? {
        return try {
            iface.withLogicalChannel(PRODUCT_AID) { transmit ->
                fun t(p1: Byte) = transmit(byteArrayOf(0x00, 0x00, p1, 0x00, 0x00))
                fun getString(p1: Byte) = decodeAsn1String(t(p1))
                fun getInt(p1: Byte) = decodeAsn1Int(t(p1))
                EuiccVendorInfo(
                    skuName = getString(0x03),
                    serialNumber = getString(0x00),
                    firmwareVersion = run {
                        val bl = getString(0x01) // bootloader version
                        val fw = getString(0x02) // firmware version
                        if (bl == null || fw == null) null else "$bl-$fw"
                    },
                    region = getInt(0x04),
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get ESTKmeInfo", e)
            null
        }
    }

    override fun tryParseEuiccVendorInfo(channel: EuiccChannel): EuiccVendorInfo? {
        if (!checkAtr(channel)) return null
        return tryParseEuiccVendorInfo(channel.apduInterface, null)
    }
}

private class SIMLink : EuiccVendor {
    companion object {
        private val EID_PATTERN = Regex("^89044045(84|21)67274948")
    }

    override fun tryParseEuiccVendorInfo(
        iface: ApduInterface,
        isdrAid: ByteArray?
    ): EuiccVendorInfo? {
        return null
    }

    override fun tryParseEuiccVendorInfo(channel: EuiccChannel): EuiccVendorInfo? {
        val eid = channel.lpa.eID
        val version = channel.lpa.euiccInfo2?.euiccFirmwareVersion
        if (version == null || EID_PATTERN.find(eid, 0) == null) return null
        val versionName = when {
            // @formatter:off
            version >= Version(37,  4,  3) -> "v3.2 (beta 1)"
            version >= Version(37,  1, 41) -> "v3.1 (beta 1)"
            version >= Version(36, 18,  5) -> "v3 (final)"
            version >= Version(36, 17, 39) -> "v3 (beta)"
            version >= Version(36, 17,  4) -> "v2s"
            version >= Version(36,  9,  3) -> "v2.1"
            version >= Version(36,  7,  2) -> "v2"
            // @formatter:on
            else -> null
        }

        val skuName = if (versionName == null) {
            "9eSIM"
        } else {
            "9eSIM $versionName"
        }

        return EuiccVendorInfo(skuName = skuName)
    }
}