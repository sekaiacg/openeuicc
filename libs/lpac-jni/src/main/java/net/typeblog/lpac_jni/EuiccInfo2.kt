package net.typeblog.lpac_jni

/* Corresponds to EuiccInfo2 in SGP.22 */
data class EuiccInfo2(
    val sgp22Version: String,
    val profileVersion: String,
    val euiccFirmwareVersion: String,
    val ts102241Version:String,
    val globalPlatformVersion: String,
    val euiccCategory:String,
    val sasAccreditationNumber: String,
    val ppVersion: String,
    val platformLabel: String,
    val discoveryBaseURL: String,
    val installedApplication:Int,
    val freeNvram: Int,
    val freeRam: Int,
    val euiccCiPKIdListForSigning: Array<String>,
    val euiccCiPKIdListForVerification: Array<String>,
    val uiccCapability: Array<String>,
    val rspCapability: Array<String>,
)