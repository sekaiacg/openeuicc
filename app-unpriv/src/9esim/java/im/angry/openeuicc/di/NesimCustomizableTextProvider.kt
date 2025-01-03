package im.angry.openeuicc.di

import android.content.Context
import im.angry.easyeuicc.R

class NesimCustomizableTextProvider(private val context: Context) :
    UnprivilegedCustomizableTextProvider(context) {
    override val noEuiccExplanation: String
        get() = context.getString(R.string.no_euicc_9esim)
    override val profileSwitchingTimeoutMessage: String
        get() = context.getString(R.string.enable_disable_timeout_9esim)
}