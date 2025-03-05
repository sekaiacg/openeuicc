package im.angry.openeuicc.core

import im.angry.openeuicc.util.EuiccVendorInfo

interface ApduInterfaceEuiccInfoProvider {
    var euiccVendorInfo: EuiccVendorInfo?
}