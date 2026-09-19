package ru.bleyzos.ai.design

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import ru.bleyzos.ai.design.theme.BzColors

/** Edge-to-edge с прозрачными системными панелями и тёмными иконками на светлом фоне. */
object BzSystemBars {
    @Suppress("DEPRECATION")
    fun apply(activity: Activity) {
        val w = activity.window
        // Обращение к decorView создаёт DecorView (installDecor). Без этого на API 30+
        // window.insetsController падает с NPE, если вызвано до setContentView().
        val decor = w.decorView
        w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(BzColors.background))
        w.statusBarColor = Color.TRANSPARENT
        w.navigationBarColor = Color.TRANSPARENT
        w.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
            decor.windowInsetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            decor.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }
}
