package im.angry.openeuicc.di

import android.content.Context

class NesimAppContainer(context: Context) : UnprivilegedAppContainer(context) {
    override val uiComponentFactory by lazy {
        NesimUiComponentFactory()
    }

    override val customizableTextProvider by lazy {
        NesimCustomizableTextProvider(context)
    }
}