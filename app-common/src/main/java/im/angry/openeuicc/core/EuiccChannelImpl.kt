package im.angry.openeuicc.core

import android.util.Log
import im.angry.openeuicc.core.usb.UsbApduInterface
import im.angry.openeuicc.util.UiccPortInfoCompat
import im.angry.openeuicc.util.decodeHex
import im.angry.openeuicc.util.encodeHex
import im.angry.openeuicc.vendored.ESTKmeInfo
import kotlinx.coroutines.flow.Flow
import net.typeblog.lpac_jni.ApduInterface
import net.typeblog.lpac_jni.LocalProfileAssistant
import net.typeblog.lpac_jni.impl.HttpInterfaceImpl
import net.typeblog.lpac_jni.impl.LocalProfileAssistantImpl

class EuiccChannelImpl(
    override val type: String,
    override val port: UiccPortInfoCompat,
    override val intrinsicChannelName: String?,
    override val apduInterface: ApduInterface,
    verboseLoggingFlow: Flow<Boolean>,
    ignoreTLSCertificateFlow: Flow<Boolean>
) : EuiccChannel {
    companion object {
        private const val TAG = "EuiccChannelImpl"

        // Specs: SGP.02 v4.3 (Section 2.2.3)
        // https://www.gsma.com/esim/wp-content/uploads/2023/02/SGP.02-v4.3.pdf#page=27
        val STANDARD_ISDR_AID = "A0000005591010FFFFFFFF8900000100".decodeHex()
    }

    private val isdRAid = findISDRAID()

    override val slotId = port.card.physicalSlotIndex
    override val logicalSlotId = port.logicalSlotIndex
    override val portId = port.portIndex

    override val lpa: LocalProfileAssistant = LocalProfileAssistantImpl(
        isdRAid,
        apduInterface,
        HttpInterfaceImpl(verboseLoggingFlow, ignoreTLSCertificateFlow),
    )

    override val atr: ByteArray?
        get() = (apduInterface as? ApduInterfaceAtrProvider)?.atr

    override val estkmeInfo: ESTKmeInfo?
        get() = (apduInterface as? ApduInterfaceEstkmeInfoProvider)?.estkmeInfo

    override val valid: Boolean
        get() = lpa.valid

    override fun close() = lpa.close()

    private fun findISDRAID(): ByteArray {
        val aids = buildList {
            STANDARD_ISDR_AID.encodeHex()
            add("A0000005591010000000008900000300") // eSIM.me
            add("A0000005591010FFFFFFFF8900050500") // 5ber.eSIM
            add("A06573746B6D65FFFFFFFF4953442D52") // ESTKme
        }
        try {
            apduInterface.connect()
            for (aid in aids) {
                val channel = aid.decodeHex()
                try {
                    Log.d(TAG, "Trying ISD-R AID: $aid")
                    apduInterface.withLogicalChannel(channel) { true }
                    Log.d(TAG, "Selected ISD-R AID: $aid")
                    return channel
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to select ISD-R AID: $aid")
                    continue
                }
            }
        } catch (_: Exception) {
        } finally {
            if (apduInterface !is UsbApduInterface)
                apduInterface.disconnect()
        }
        return STANDARD_ISDR_AID
    }
}
