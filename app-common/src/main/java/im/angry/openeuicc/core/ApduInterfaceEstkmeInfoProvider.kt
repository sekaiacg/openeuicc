package im.angry.openeuicc.core

import im.angry.openeuicc.util.EuiccVendorInfo

interface ApduInterfaceEstkmeInfoProvider {
    var estkmeInfo: EuiccVendorInfo?
}