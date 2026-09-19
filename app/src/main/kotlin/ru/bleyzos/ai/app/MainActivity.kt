package ru.bleyzos.ai.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import ru.bleyzos.ai.design.BzSystemBars
import ru.bleyzos.ai.design.chat.BzChatScreen

/** Вся «оболочка»: экран целиком рисует bleyzosAIDesignModule, данные берёт из bleyzosAIApiModule. */
class MainActivity : Activity() {
    private lateinit var screen: BzChatScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BzSystemBars.apply(this)
        screen = BzChatScreen(this)
        setContentView(screen)
    }

    @Deprecated("Платформенный Activity без AndroidX")
    override fun onBackPressed() {
        if (!screen.handleBack()) super.onBackPressed()
    }

    @Deprecated("Платформенный Activity без AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!screen.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
