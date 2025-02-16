package im.angry.openeuicc.core

import android.content.Context
import android.util.Log
import im.angry.openeuicc.OpenEuiccApplication
import im.angry.openeuicc.R
import im.angry.openeuicc.util.*
import kotlinx.coroutines.flow.first
import java.lang.IllegalArgumentException

class PrivilegedEuiccChannelFactory(context: Context) : DefaultEuiccChannelFactory(context) {
    private val tm by lazy {
        (context.applicationContext as OpenEuiccApplication).appContainer.telephonyManager
    }

    @Suppress("NAME_SHADOWING")
    override suspend fun tryOpenEuiccChannel(port: UiccPortInfoCompat): EuiccChannel? {
        val port = port as RealUiccPortInfoCompat
        if (port.card.isRemovable) {
            // Attempt unprivileged (OMAPI) before TelephonyManager
            // but still try TelephonyManager in case OMAPI is broken
            super.tryOpenEuiccChannel(port)?.let { return it }
        }

        if (port.card.isEuicc || context.preferenceRepository.forceUseTMAPIFlow.first()) {
            Log.i(
                DefaultEuiccChannelManager.TAG,
                "Trying TelephonyManager for slot ${port.card.physicalSlotIndex} port ${port.portIndex}"
            )
            try {
                val mss: UByte = 0xFFu
                return EuiccChannelImpl(
                    context.getString(R.string.telephony_manager),
                    port,
                    intrinsicChannelName = null,
                    TelephonyManagerApduInterface(
                        port,
                        tm,
                        context.preferenceRepository.verboseLoggingFlow
                    ),
                    context.preferenceRepository.verboseLoggingFlow,
                    context.preferenceRepository.ignoreTLSCertificateFlow,
                ).also {
                    Log.i(DefaultEuiccChannelManager.TAG, "Is TMAPI channel, setting MSS to $mss")
                    it.lpa.setEs10xMss(mss)
                }
            } catch (e: IllegalArgumentException) {
                // Failed
                Log.w(
                    DefaultEuiccChannelManager.TAG,
                    "TelephonyManager APDU interface unavailable for slot ${port.card.physicalSlotIndex} port ${port.portIndex}, falling back"
                )
            }
        }

        return super.tryOpenEuiccChannel(port)
    }
}