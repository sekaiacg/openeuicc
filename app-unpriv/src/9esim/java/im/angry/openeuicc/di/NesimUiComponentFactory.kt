package im.angry.openeuicc.di

import androidx.fragment.app.Fragment
import im.angry.openeuicc.ui.NesimNoEuiccPlaceholderFragment

class NesimUiComponentFactory : UnprivilegedUiComponentFactory() {
    override fun createNoEuiccPlaceholderFragment(): Fragment =
        NesimNoEuiccPlaceholderFragment()
}