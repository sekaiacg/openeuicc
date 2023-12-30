package net.typeblog.lpac_jni

/* Corresponds to EuiccInfo2 in SGP.22 */
data class EuiccInfo2(
    val profileVersion: String,
    val sgp22Version: String,
    val euiccFirmwareVersion: String,
    val uiccFirmwareVersion: String,
    val globalPlatformVersion: String,
    val sasAccreditationNumber: String,
    val ppVersion: String,
    val freeNvram: Int,
    val freeRam: Int,
)