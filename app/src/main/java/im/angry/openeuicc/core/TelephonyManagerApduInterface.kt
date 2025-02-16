package im.angry.openeuicc.core

import android.telephony.IccOpenLogicalChannelResponse
import android.telephony.TelephonyManager
import android.util.Log
import im.angry.openeuicc.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.typeblog.lpac_jni.ApduInterface

class TelephonyManagerApduInterface(
    private val port: UiccPortInfoCompat,
    private val tm: TelephonyManager,
    private val verboseLoggingFlow: Flow<Boolean>
): ApduInterface, ApduInterfaceEstkInfoProvider {
    companion object {
        const val TAG = "TelephonyManagerApduInterface"
    }

    private var lastChannel: Int = -1

    override var estkInfo: ApduInterfaceEstkInfoProvider.EstkInfo? = null

    override val valid: Boolean
        // TelephonyManager channels will never become truly "invalid",
        // just that transactions might return errors or nonsense
        get() = lastChannel != -1

    override fun connect() {
        // Do nothing
    }

    override fun disconnect() {
        // Do nothing
        lastChannel = -1
        estkInfo = null
    }

    override fun logicalChannelOpen(aid: ByteArray): Int {
        check(lastChannel == -1) { "Already initialized" }
        tryGetEstkInfo()
        val hex = aid.encodeHex()
        val channel = tm.iccOpenLogicalChannelByPortCompat(port.card.physicalSlotIndex, port.portIndex, hex, 0)
        if (channel.status != IccOpenLogicalChannelResponse.STATUS_NO_ERROR || channel.channel == IccOpenLogicalChannelResponse.INVALID_CHANNEL) {
            throw IllegalArgumentException("Cannot open logical channel $hex via TelephonManager on slot ${port.card.physicalSlotIndex} port ${port.portIndex}")
        }
        lastChannel = channel.channel
        return lastChannel
    }

    override fun logicalChannelClose(handle: Int) {
        check(handle == lastChannel) { "Invalid channel handle " }
        tm.iccCloseLogicalChannelByPortCompat(port.card.physicalSlotIndex, port.portIndex, handle)
        lastChannel = -1
    }

    private fun transmit(tx: ByteArray, channel: Int): ByteArray {
        if (runBlocking { verboseLoggingFlow.first() }) {
            Log.d(TAG, "TelephonyManager APDU: ${tx.encodeHex()}")
        }

        val cla = tx[0].toUByte().toInt()
        val instruction = tx[1].toUByte().toInt()
        val p1 = tx[2].toUByte().toInt()
        val p2 = tx[3].toUByte().toInt()
        val p3 = tx[4].toUByte().toInt()
        val p4 = tx.drop(5).toByteArray().encodeHex()

        return tm.iccTransmitApduLogicalChannelByPortCompat(port.card.physicalSlotIndex, port.portIndex, channel,
            cla,
            instruction,
            p1,
            p2,
            p3,
            p4
        ).also {
            if (runBlocking { verboseLoggingFlow.first() }) {
                Log.d(TAG, "TelephonyManager APDU response: $it")
            }
        }?.decodeHex() ?: byteArrayOf()
    }

    override fun transmit(tx: ByteArray): ByteArray {
        check(lastChannel != -1) { "Uninitialized" }
        return transmit(tx, lastChannel)
    }

    private fun tryGetEstkInfo() {
        try {
            val hex = estkFwupdSelectAid.encodeHex()
            val channel = tm.iccOpenLogicalChannelByPortCompat(port.card.physicalSlotIndex, port.portIndex, hex, 0)
            if (channel.status == IccOpenLogicalChannelResponse.STATUS_NO_ERROR || channel.channel != IccOpenLogicalChannelResponse.INVALID_CHANNEL) {
                val channelId = channel.channel
                estkInfo = ApduInterfaceEstkInfoProvider.EstkInfo()
                transmit(estkGetBlVerAPDU, channelId).let {
                    if (it.isNotEmpty()) estkInfo?.blVer = decodeFormApduResp(it)
                }
                transmit(estkGetFwVerAPDU, channelId).let {
                    if (it.isNotEmpty()) estkInfo?.fwVer = decodeFormApduResp(it)
                }
                transmit(estkGetSkuAPDU, channelId).let {
                    if (it.isNotEmpty()) estkInfo?.sku = decodeFormApduResp(it)
                }
            }
        } catch (_: Exception) {
        }
    }

}
