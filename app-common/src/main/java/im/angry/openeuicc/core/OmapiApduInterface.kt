package im.angry.openeuicc.core

import android.se.omapi.Channel
import android.se.omapi.SEService
import android.se.omapi.Session
import android.util.Log
import im.angry.openeuicc.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import net.typeblog.lpac_jni.ApduInterface

class OmapiApduInterface(
    private val service: SEService,
    private val port: UiccPortInfoCompat,
    private val verboseLoggingFlow: Flow<Boolean>
): ApduInterface, ApduInterfaceAtrProvider, ApduInterfaceEstkInfoProvider {
    companion object {
        const val TAG = "OmapiApduInterface"
    }

    private lateinit var session: Session
    private lateinit var lastChannel: Channel

    override val valid: Boolean
        get() = service.isConnected && (this::session.isInitialized && !session.isClosed)

    override val atr: ByteArray?
        get() = session.atr

    override var estkInfo: ApduInterfaceEstkInfoProvider.EstkInfo? = null

    override fun connect() {
        session = service.getUiccReaderCompat(port.logicalSlotIndex + 1).openSession()
    }

    override fun disconnect() {
        session.close()
    }

    override fun logicalChannelOpen(aid: ByteArray): Int {
        check(!this::lastChannel.isInitialized) {
            "Can only open one channel"
        }
        tryGetEstkInfo()
        lastChannel = session.openLogicalChannel(aid)!!
        return 1
    }

    override fun logicalChannelClose(handle: Int) {
        check(handle == 1 && !this::lastChannel.isInitialized) {
            "Unknown channel"
        }
        lastChannel.close()
        estkInfo = null
    }

    private fun transmit(tx: ByteArray, channel: Channel): ByteArray {
        if (runBlocking { verboseLoggingFlow.first() }) {
            Log.d(TAG, "OMAPI APDU: ${tx.encodeHex()}")
        }

        try {
            for (i in 0..10) {
                val res = channel.transmit(tx)
                if (runBlocking { verboseLoggingFlow.first() }) {
                    Log.d(TAG, "OMAPI APDU response: ${res.encodeHex()}")
                }

                if (res.size == 2 && res[0] == 0x66.toByte() && res[1] == 0x01.toByte()) {
                    Log.d(TAG, "Received checksum error 0x6601, retrying (count = $i)")
                    continue
                }

                return res
            }

            throw RuntimeException("Retransmit attempts exhausted; this was likely caused by checksum errors")
        } catch (e: Exception) {
            Log.e(TAG, "OMAPI APDU exception")
            e.printStackTrace()
            throw e
        }
    }

    override fun transmit(tx: ByteArray): ByteArray {
        check(this::lastChannel.isInitialized) {
            "Unknown channel"
        }
        return transmit(tx, lastChannel)
    }

    private fun tryGetEstkInfo() {
        var channel: Channel? = null
        try {
            channel = session.openLogicalChannel(estkFwupdSelectAid)!!
            estkInfo = ApduInterfaceEstkInfoProvider.EstkInfo()
            transmit(estkGetBlVerAPDU, channel).let { estkInfo?.blVer = decodeFormApduResp(it) }
            transmit(estkGetFwVerAPDU, channel).let { estkInfo?.fwVer = decodeFormApduResp(it) }
            transmit(estkGetSkuAPDU, channel).let { estkInfo?.sku = decodeFormApduResp(it) }
        } catch (_: Exception) {
        } finally {
            channel?.close()
        }
    }
}
