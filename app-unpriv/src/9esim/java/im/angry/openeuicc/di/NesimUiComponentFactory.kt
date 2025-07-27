package im.angry.openeuicc.di

import androidx.fragment.app.Fragment
import im.angry.openeuicc.ui.NesimNoEuiccPlaceholderFragment
import im.angry.openeuicc.ui.NesimQuickCompatibilityFragment
import im.angry.openeuicc.ui.NesimSettingsFragment

class NesimUiComponentFactory : UnprivilegedUiComponentFactory() {
    override fun createNoEuiccPlaceholderFragment(): Fragment =
        NesimNoEuiccPlaceholderFragment()

    override fun createSettingsFragment(): Fragment =
        NesimSettingsFragment()

    override fun createQuickCompatibilityFragment(): Fragment =
        NesimQuickCompatibilityFragment()
}