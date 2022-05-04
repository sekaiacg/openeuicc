package im.angry.openeuicc.service

import android.service.euicc.*
import android.telephony.euicc.DownloadableSubscription
import android.telephony.euicc.EuiccInfo
import com.truphone.lpa.LocalProfileInfo
import com.truphone.util.TextUtil
import im.angry.openeuicc.OpenEuiccApplication
import im.angry.openeuicc.core.EuiccChannel
import im.angry.openeuicc.util.*

class OpenEuiccService : EuiccService() {
    private val openEuiccApplication
        get() = application as OpenEuiccApplication

    private fun findChannel(slotId: Int): EuiccChannel? =
        openEuiccApplication.euiccChannelManager
            .findEuiccChannelBySlotBlocking(slotId)

    override fun onGetEid(slotId: Int): String? =
        findChannel(slotId)?.lpa?.eid

    override fun onGetOtaStatus(slotId: Int): Int {
        // Not implemented
        return 5 // EUICC_OTA_STATUS_UNAVAILABLE
    }

    override fun onStartOtaIfNecessary(
        slotId: Int,
        statusChangedCallback: OtaStatusChangedCallback?
    ) {
        // Not implemented
    }

    override fun onGetDownloadableSubscriptionMetadata(
        slotId: Int,
        subscription: DownloadableSubscription?,
        forceDeactivateSim: Boolean
    ): GetDownloadableSubscriptionMetadataResult {
        // Stub: return as-is and do not fetch anything
        // This is incompatible with carrier eSIM apps; should we make it compatible?
        return GetDownloadableSubscriptionMetadataResult(RESULT_OK, subscription)
    }

    override fun onGetDefaultDownloadableSubscriptionList(
        slotId: Int,
        forceDeactivateSim: Boolean
    ): GetDefaultDownloadableSubscriptionListResult {
        // Stub: we do not implement this (as this would require phoning in a central GSMA server)
        return GetDefaultDownloadableSubscriptionListResult(RESULT_OK, arrayOf())
    }

    override fun onGetEuiccProfileInfoList(slotId: Int): GetEuiccProfileInfoListResult? {
        val profiles = (findChannel(slotId) ?: return null).lpa.profiles.filter {
            it.profileClass != LocalProfileInfo.Clazz.Testing
        }.map {
            EuiccProfileInfo.Builder(it.iccidLittleEndian).apply {
                setProfileName(it.name)
                setNickname(it.nickName)
                setServiceProviderName(it.providerName)
                setState(
                    when (it.state) {
                        LocalProfileInfo.State.Enabled -> EuiccProfileInfo.PROFILE_STATE_ENABLED
                        LocalProfileInfo.State.Disabled -> EuiccProfileInfo.PROFILE_STATE_DISABLED
                    }
                )
                setProfileClass(
                    when (it.profileClass) {
                        LocalProfileInfo.Clazz.Testing -> EuiccProfileInfo.PROFILE_CLASS_TESTING
                        LocalProfileInfo.Clazz.Provisioning -> EuiccProfileInfo.PROFILE_CLASS_PROVISIONING
                        LocalProfileInfo.Clazz.Operational -> EuiccProfileInfo.PROFILE_CLASS_OPERATIONAL
                    }
                )
            }.build()
        }

        return GetEuiccProfileInfoListResult(RESULT_OK, profiles.toTypedArray(), false)
    }

    override fun onGetEuiccInfo(slotId: Int): EuiccInfo {
        return EuiccInfo("Unknown") // TODO: Can we actually implement this?
    }

    override fun onDeleteSubscription(slotId: Int, iccid: String?): Int {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun onSwitchToSubscription(
        slotId: Int,
        iccid: String?,
        forceDeactivateSim: Boolean
    ): Int {
        TODO("Not yet implemented")
    }

    override fun onUpdateSubscriptionNickname(slotId: Int, iccid: String, nickname: String?): Int {
        val channel = findChannel(slotId) ?: return RESULT_FIRST_USER
        val success = channel.lpa
            .setNickname(TextUtil.iccidLittleToBig(iccid), nickname)
        openEuiccApplication.subscriptionManager.tryRefreshCachedEuiccInfo(channel.cardId)
        return if (success) {
            RESULT_OK
        } else {
            RESULT_FIRST_USER
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onEraseSubscriptions(slotId: Int): Int {
        // No-op
        return RESULT_FIRST_USER
    }

    override fun onRetainSubscriptionsForFactoryReset(slotId: Int): Int {
        // No-op -- we do not care
        return RESULT_FIRST_USER
    }
}