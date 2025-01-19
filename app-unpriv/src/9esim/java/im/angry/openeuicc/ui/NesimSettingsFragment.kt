package im.angry.openeuicc.ui

import android.os.Bundle
import im.angry.easyeuicc.R

class NesimSettingsFragment : UnprivilegedSettingsFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        addPreferencesFromResource(R.xml.pref_9esim_settings)
    }
}