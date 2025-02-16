package im.angry.openeuicc.core

import im.angry.openeuicc.util.*
import net.typeblog.lpac_jni.LocalProfileAssistant

interface EuiccChannel {
    val type: String

    val port: UiccPortInfoCompat

    val slotId: Int // PHYSICAL slot
    val logicalSlotId: Int
    val portId: Int

    val lpa: LocalProfileAssistant

    val valid: Boolean

    /**
     * Answer to Reset (ATR) value of the underlying interface, if any
     */
    val atr: ByteArray?

    /**
     * Try to get estk information, if any
     * www.estk.me
     */
    val estkInfo: String?

    /**
     * Intrinsic name of this channel. For device-internal SIM slots,
     * this should be null; for USB readers, this should be the name of
     * the reader device.
     */
    val intrinsicChannelName: String?

    fun close()
}