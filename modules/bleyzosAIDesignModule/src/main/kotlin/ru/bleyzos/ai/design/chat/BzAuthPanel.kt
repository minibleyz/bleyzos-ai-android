package ru.bleyzos.ai.design.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import ru.bleyzos.ai.api.BleyzosAIClient
import ru.bleyzos.ai.api.Cancelable
import ru.bleyzos.ai.api.auth.DeviceCode
import ru.bleyzos.ai.api.auth.LoginListener
import ru.bleyzos.ai.api.model.AuthUser
import ru.bleyzos.ai.api.BzConfig
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.widgets.BzButton
import ru.bleyzos.ai.design.widgets.BzButtonStyle
import ru.bleyzos.ai.design.widgets.MATCH
import ru.bleyzos.ai.design.widgets.add
import ru.bleyzos.ai.design.widgets.bzText
import ru.bleyzos.ai.design.widgets.hbox
import ru.bleyzos.ai.design.widgets.pad
import ru.bleyzos.ai.design.widgets.size
import ru.bleyzos.ai.design.widgets.vbox

/**
 * Блок входа в сайдбаре — auth-panel.tsx. Вход идёт через Device Flow:
 * код → браузер (OAuth Bleyzos) → опрос сервера.
 */
class BzAuthPanel(context: Context, private val client: BleyzosAIClient) : LinearLayout(context) {

    private enum class Mode { IDLE, WAITING }

    private var mode = Mode.IDLE
    private var code: DeviceCode? = null
    private var hint: String? = null
    private var login: Cancelable? = null
    private val listener = { render() }

    init {
        orientation = VERTICAL
        render()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        client.auth.addListener(listener)
        render()
    }

    override fun onDetachedFromWindow() {
        client.auth.removeListener(listener)
        super.onDetachedFromWindow()
    }

    private fun render() {
        removeAllViews()
        val user = client.auth.user
        if (user != null) renderUser(user) else if (mode == Mode.WAITING) renderWaiting() else renderGuest()
    }

    private fun renderUser(user: AuthUser) {
        val row = context.hbox()
        val avatar = context.hbox().apply {
            gravity = Gravity.CENTER
            background = BzDrawables.circle(BzColors.alpha(BzColors.primary, 0.15f))
        }
        avatar.addView(BzIconView(context, BzIcon.SHIELD_CHECK, 16, BzColors.primary), LayoutParams(size(16), size(16)))
        row.add(avatar, size(32), size(32), mr = 10)
        val col = context.vbox()
        col.add(context.bzText(user.name.ifEmpty { user.email }, 13f, BzColors.foreground, 500, singleLine = true))
        if (user.name.isNotEmpty() && user.email.isNotEmpty()) {
            col.add(context.bzText(user.email, 11f, BzColors.mutedForeground, singleLine = true))
        }
        row.add(col, weight = 1f, w = 0)
        add(row, MATCH)

        val out = BzButton(context, "Выйти", BzButtonStyle.OUTLINE, BzIcon.LOG_OUT, textSp = 12f, radiusDp = 8, hPad = 12, vPad = 6)
        out.setOnClickListener { client.auth.logout() }
        add(out, MATCH, mt = 8)
    }

    private fun renderGuest() {
        val btn = BzButton(context, "Войти", BzButtonStyle.PRIMARY, BzIcon.LOG_IN, textSp = 13f, radiusDp = 8, hPad = 12, vPad = 8)
        btn.setOnClickListener { startLogin() }
        add(btn, MATCH)
        hint?.let { add(context.bzText(it, 11f, BzColors.destructive, lineHeightMult = 1.3f), mt = 10) }
        add(
            context.bzText(
                "Один аккаунт Bleyzos для всей экосистемы. Без входа диалоги хранятся ${BzConfig.GUEST_TTL_DAYS} дней, с входом — постоянно.",
                11f, BzColors.mutedForeground, lineHeightMult = 1.3f,
            ),
            mt = 10,
        )
    }

    private fun renderWaiting() {
        val c = code
        val card = context.vbox().apply {
            background = BzDrawables.rect(context, BzColors.background, 10, BzColors.input, 1)
            pad(12, 12, 12, 12)
        }
        card.add(context.bzText("Код входа", 11f, BzColors.mutedForeground))
        card.add(context.bzText(c?.userCode ?: "…", 20f, BzColors.primary, 600, mono = true, letterSpacingEm = 0.12f), mt = 4)
        card.add(
            context.bzText("Откройте страницу входа и подтвердите вход в аккаунт Bleyzos. Приложение ждёт…", 11f, BzColors.mutedForeground, lineHeightMult = 1.3f),
            mt = 6,
        )
        add(card, MATCH)

        val open = BzButton(context, "Открыть браузер", BzButtonStyle.PRIMARY, BzIcon.EXTERNAL_LINK, textSp = 12.5f, radiusDp = 8, hPad = 12, vPad = 8)
        open.setOnClickListener { c?.let { openBrowser(it.verificationUri) } }
        add(open, MATCH, mt = 8)
        val cancel = BzButton(context, "Отмена", BzButtonStyle.OUTLINE, textSp = 12f, radiusDp = 8, hPad = 12, vPad = 6)
        cancel.setOnClickListener { cancelLogin(null) }
        add(cancel, MATCH, mt = 6)
    }

    private fun startLogin() {
        hint = null
        mode = Mode.WAITING
        code = null
        render()
        login = client.auth.startLogin(object : LoginListener {
            override fun onCode(code: DeviceCode) {
                this@BzAuthPanel.code = code
                render()
                openBrowser(code.verificationUri)
            }

            override fun onSuccess(user: AuthUser) {
                mode = Mode.IDLE; login = null; render()
            }

            override fun onError(message: String) = cancelLogin(message)
            override fun onExpired() = cancelLogin("Код истёк, попробуйте войти снова")
        })
    }

    private fun cancelLogin(message: String?) {
        login?.cancel()
        login = null
        mode = Mode.IDLE
        code = null
        hint = message
        render()
    }

    private fun openBrowser(uri: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            hint = "Не удалось открыть браузер. Откройте ссылку вручную: $uri"
            render()
        }
    }
}
