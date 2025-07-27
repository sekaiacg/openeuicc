package im.angry.openeuicc.ui

import android.annotation.SuppressLint
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.core.view.isVisible
import im.angry.easyeuicc.R

class NesimQuickCompatibilityFragment : QuickCompatibilityFragment() {

    private val hpUrl: TextView by lazy {
        requireView().requireViewById(R.id.quick_compatibility_result_9esim_hp)
    }

    @SuppressLint("SetTextI18n")
    override fun onCompatibilityUpdate(result: Companion.CompatibilityResult) {
        super.onCompatibilityUpdate(result)
        hpUrl.isVisible = true
        hpUrl.movementMethod = LinkMovementMethod.getInstance()
        val title = getString(R.string.pref_info_9esim_official_website)
        val value = getString(R.string.pref_info_source_code_url)
        hpUrl.text = "$title: $value"
    }
}
