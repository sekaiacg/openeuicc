package im.angry.openeuicc.core

import im.angry.openeuicc.util.decodeHex

interface ApduInterfaceEstkInfoProvider {
    data class EstkInfo(
        var blVer: String = "",
        var fwVer: String = "",
        var sku: String = "",
    ) {
        override fun toString() = buildString {
            if (sku.isNotEmpty()) appendLine(sku)
            if (blVer.isNotEmpty() && fwVer.isNotEmpty()) append(blVer, '-', fwVer)
            else if (blVer.isNotEmpty()) append(blVer)
            else if (fwVer.isNotEmpty()) append(fwVer)
        }
    }

    val estkFwupdSelectAid: ByteArray
        get() = "A06573746B6D65FFFFFFFFFFFF6D6774".decodeHex()
    val estkGetSerialAPDU: ByteArray
        get() = "0000000000".decodeHex()
    val estkGetBlVerAPDU: ByteArray
        get() = "0000010000".decodeHex()
    val estkGetFwVerAPDU: ByteArray
        get() = "0000020000".decodeHex()
    val estkGetSkuAPDU: ByteArray
        get() = "0000030000".decodeHex()
    var estkInfo: EstkInfo?
}