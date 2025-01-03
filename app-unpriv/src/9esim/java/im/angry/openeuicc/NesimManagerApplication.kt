package im.angry.openeuicc

import im.angry.openeuicc.di.NesimAppContainer

class NesimManagerApplication : UnprivilegedOpenEuiccApplication() {
    override val appContainer by lazy {
        NesimAppContainer(this)
    }
}