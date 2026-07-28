package com.userhub

import androidx.compose.ui.window.ComposeUIViewController
import com.userhub.di.initKoin
import org.koin.mp.KoinPlatformTools
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
    return ComposeUIViewController { App() }
}
