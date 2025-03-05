package im.angry.openeuicc.core

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.se.omapi.SEService
import android.util.Log
import im.angry.openeuicc.common.R
import im.angry.openeuicc.core.usb.UsbApduInterface
import im.angry.openeuicc.core.usb.bulkPair
import im.angry.openeuicc.core.usb.endpoints
import im.angry.openeuicc.util.*
import im.angry.openeuicc.vendored.getESTKmeInfo
import java.lang.IllegalArgumentException

open class DefaultEuiccChannelFactory(protected val context: Context) : EuiccChannelFactory {
    private var seService: SEService? = null

    private val usbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private suspend fun ensureSEService() {
        if (seService == null || !seService!!.isConnected) {
            seService = connectSEService(context)
        }
    }

    override suspend fun tryOpenEuiccChannel(port: UiccPortInfoCompat): EuiccChannel? {
        if (port.portIndex != 0) {
            Log.w(DefaultEuiccChannelManager.TAG, "OMAPI channel attempted on non-zero portId, this may or may not work.")
        }

        ensureSEService()

        Log.i(DefaultEuiccChannelManager.TAG, "Trying OMAPI for physical slot ${port.card.physicalSlotIndex}")
        try {
            val mss: UByte = 0xFFu
            val omapiApduInterface = OmapiApduInterface(
                seService!!,
                port,
                context.preferenceRepository.verboseLoggingFlow
            )
            try {
                omapiApduInterface.connect()
                omapiApduInterface.estkmeInfo = getESTKmeInfo(omapiApduInterface)
                omapiApduInterface.disconnect()
            }catch (_: Exception) {
            }
            return EuiccChannelImpl(
                context.getString(R.string.omapi),
                port,
                intrinsicChannelName = null,
                omapiApduInterface,
                context.preferenceRepository.verboseLoggingFlow,
                context.preferenceRepository.ignoreTLSCertificateFlow,
            ).also {
                Log.i(DefaultEuiccChannelManager.TAG, "Is OMAPI channel, setting MSS to $mss")
                it.lpa.setEs10xMss(mss)
            }
        } catch (e: IllegalArgumentException) {
            // Failed
            Log.w(
                DefaultEuiccChannelManager.TAG,
                "OMAPI APDU interface unavailable for physical slot ${port.card.physicalSlotIndex}."
            )
        }

        return null
    }

    override fun tryOpenUsbEuiccChannel(usbDevice: UsbDevice, usbInterface: UsbInterface): EuiccChannel? {
        val (bulkIn, bulkOut) = usbInterface.endpoints.bulkPair
        if (bulkIn == null || bulkOut == null) return null
        val conn = usbManager.openDevice(usbDevice) ?: return null
        if (!conn.claimInterface(usbInterface, true)) return null
        return EuiccChannelImpl(
            context.getString(R.string.usb),
            FakeUiccPortInfoCompat(FakeUiccCardInfoCompat(EuiccChannelManager.USB_CHANNEL_ID)),
            intrinsicChannelName = usbDevice.productName,
            UsbApduInterface(
                conn,
                bulkIn,
                bulkOut,
                context.preferenceRepository.verboseLoggingFlow
            ),
            context.preferenceRepository.verboseLoggingFlow,
            context.preferenceRepository.ignoreTLSCertificateFlow,
        )
    }

    override fun cleanup() {
        seService?.shutdown()
        seService = null
    }
}