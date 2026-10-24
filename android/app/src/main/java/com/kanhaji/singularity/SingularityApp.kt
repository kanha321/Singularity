package com.kanhaji.singularity

import android.app.Application
import com.kanhaji.core.designsystem.backgrounds.BackgroundType
import com.kanhaji.core.framework.coreModules
import com.kanhaji.core.storage.provider.KSafeProvider
import com.kanhaji.core.storage.provider.initKSafe
import com.kanhaji.core.theme.manager.ThemeConfig
import com.kanhaji.core.theme.manager.ThemeManager
import com.kanhaji.core.theme.manager.ThemeType
import com.kanhaji.feature.settings.di.settingsModule
import com.materialkolor.PaletteStyle
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SingularityApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKSafe(this)
        com.kanhaji.singularity.feature.auth.data.AuthSettingsManager.initialize(this)

        startKoin {
            androidContext(this@SingularityApp)
            modules(coreModules + listOf(settingsModule))
        }

        ThemeManager.initialize(
            kSafeProvider = KSafeProvider,
            initialConfig = ThemeConfig(
                defaultThemeType = ThemeType.DARK,
                defaultSeedColorHex = "#3584E4",
                defaultPaletteStyle = PaletteStyle.TonalSpot,
                defaultPureBlack = true,
                defaultBackgroundType = BackgroundType.CIRCLES,
                defaultEnableParallax = true
            )
        )
    }
}
