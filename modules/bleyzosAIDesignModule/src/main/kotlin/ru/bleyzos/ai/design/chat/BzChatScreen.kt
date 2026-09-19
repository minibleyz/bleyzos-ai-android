package ru.bleyzos.ai.design.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.OpenableColumns
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ru.bleyzos.ai.api.BleyzosAI
import ru.bleyzos.ai.api.BleyzosAIClient
import ru.bleyzos.ai.api.BzConfig
import ru.bleyzos.ai.api.chat.ChatEngine
import ru.bleyzos.ai.api.chat.ChatState
import ru.bleyzos.ai.api.chat.UploadFile
import ru.bleyzos.ai.api.model.Message
import ru.bleyzos.ai.api.model.Role
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDims
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.dpi
import ru.bleyzos.ai.design.widgets.BzButton
import ru.bleyzos.ai.design.widgets.BzButtonStyle
import ru.bleyzos.ai.design.widgets.BzConfirmDialog
import ru.bleyzos.ai.design.widgets.BzDrawer
import ru.bleyzos.ai.design.widgets.BzIconButton
import ru.bleyzos.ai.design.widgets.BzMaxWidthLayout
import ru.bleyzos.ai.design.widgets.BzPopupItem
import ru.bleyzos.ai.design.widgets.BzPopupMenu
import ru.bleyzos.ai.design.widgets.BzToastHost
import ru.bleyzos.ai.design.widgets.MATCH
import ru.bleyzos.ai.design.widgets.WRAP
import ru.bleyzos.ai.design.widgets.add
import ru.bleyzos.ai.design.widgets.bzText
import ru.bleyzos.ai.design.widgets.hbox
import ru.bleyzos.ai.design.widgets.linearLp
import ru.bleyzos.ai.design.widgets.pad
import ru.bleyzos.ai.design.widgets.padSym
import ru.bleyzos.ai.design.widgets.size
import ru.bleyzos.ai.design.widgets.vbox

/**
 * Готовый экран Bleyzos AI на чистых View: сайдбар/drawer, шапка с выбором модели,
 * лента сообщений, ввод, панель артефактов. Состояние берёт из [ChatEngine].
 *
 * Использование в Activity:
 * ```
 * BzSystemBars.apply(this)
 * screen = BzChatScreen(this); setContentView(screen)
 * // onBackPressed → screen.handleBack(); onActivityResult → screen.handleActivityResult(...)
 * ```
 */
class BzChatScreen(
    context: Context,
    private val client: BleyzosAIClient = BleyzosAI.client(context),
    private val engine: ChatEngine = BleyzosAI.engine(context),
) : FrameLayout(context), ChatEngine.Listener {

    companion object {
        const val REQUEST_PICK_FILES = 0xB1E1
    }

    private class Model(val id: String, val name: String, val hint: String, val soon: Boolean = false)

    private val models = listOf(
        Model("gorn", "Bleyzos 2.7 Gorn", "Флагман · сложные задачи"),
        Model("mini", "Bleyzos 2.7 Mini", "Быстрый · повседневные задачи"),
        Model("kin", "Bleyzos 2.7 Kin", "Скоро · мультимодальный", soon = true),
    )
    private var model = models[0]

    // каркас
    private val root = context.hbox(centerVertical = false)
    private val sidebarSlot = FrameLayout(context)
    private val sidebarShell = context.hbox(centerVertical = false)
    private val sidebar: BzSidebarView
    private val main = context.vbox()
    private val header = context.hbox()
    private val menuBtn: BzIconButton
    private val modelLabel: TextView
    private val scroll = ScrollView(context)
    private val body = FrameLayout(context)
    private val welcome: BzWelcomeView
    private val messagesWrap: BzMaxWidthLayout
    private val messagesBox = context.vbox()
    private val input = BzChatInputView(context)
    private val drawer: BzDrawer
    private val drawerHolder = FrameLayout(context)
    private val artifactPanel: BzArtifactPanel
    private val toastHost = BzToastHost(context)
    private val modelMenu: BzPopupMenu

    // состояние отображения
    private var wide = false
    private var lastWidthDp = -1
    private var insetTop = 0
    private var insetBottom = 0
    private var renderScheduled = false
    private var renderedSessionId: String? = null
    private var renderedCount = 0
    private var openArtifact: Pair<String, Int>? = null

    private val messageViews = HashMap<String, View>()
    private val thinking = BzThinkingBubble(context)
    private val continueRow = FrameLayout(context)

    private val messageCallbacks = object : BzMessageCallbacks {
        override fun onEditMessage(messageId: String, newText: String) = engine.send(newText, emptyList(), messageId)
        override fun onSwitchVariant(messageId: String, dir: Int) {
            openArtifact = null
            engine.switchVariant(messageId, dir)
        }
        override fun onOpenArtifact(messageId: String, index: Int) {
            openArtifact = messageId to index
            scheduleRender()
        }
    }

    init {
        setBackgroundColor(BzColors.background)

        sidebar = BzSidebarView(context, client, object : BzSidebarCallbacks {
            override fun onSelect(id: String) {
                openArtifact = null
                engine.selectSession(id)
                drawer.close()
            }

            override fun onNewChat() {
                openArtifact = null
                engine.newChat()
                input.setText("")
                drawer.close()
            }

            override fun onDelete(id: String, title: String) {
                BzConfirmDialog.show(
                    context, "Удалить чат «$title»?",
                    "Диалог будет удалён с этого устройства без возможности восстановления.",
                    "Удалить",
                ) {
                    openArtifact = null
                    engine.deleteSession(id)
                }
            }
        })
        sidebarShell.add(sidebar, 0, MATCH, weight = 1f)
        sidebarShell.add(View(context).apply { setBackgroundColor(BzColors.border) }, 1, MATCH)

        // шапка
        header.setBackgroundColor(BzColors.alpha(BzColors.background, 0.8f))
        header.pad(12, 0, 12, 0)
        menuBtn = BzIconButton(context, BzIcon.MENU, 36, 20)
        menuBtn.contentDescription = "Открыть меню"
        menuBtn.setOnClickListener { drawer.open() }
        header.add(menuBtn, size(36), size(36), mr = 4)

        val trigger = context.hbox().apply {
            padSym(12, 6)
            background = BzDrawables.ripple(context, null, 10)
            isClickable = true
            contentDescription = "Выбрать модель"
        }
        modelLabel = context.bzText(model.name, 14f, BzColors.primary, 500)
        trigger.add(modelLabel)
        trigger.add(BzIconView(context, BzIcon.CHEVRON_DOWN, 16, BzColors.mutedForeground), size(16), size(16), ml = 6)
        header.add(trigger)
        modelMenu = BzPopupMenu(
            context, "Модель",
            models.map { BzPopupItem(it.id, it.name, it.hint, if (it.soon) "Скоро" else null, it.soon) },
        )
        trigger.setOnClickListener {
            modelMenu.show(trigger, model.id) { item ->
                models.firstOrNull { it.id == item.id }?.let { model = it; modelLabel.text = it.name }
            }
        }

        // лента
        welcome = BzWelcomeView(context) { engine.send(it) }
        body.addView(welcome, LayoutParams(MATCH, MATCH, Gravity.CENTER))
        messagesBox.pad(0, 0, 0, 0)
        messagesWrap = BzMaxWidthLayout(context, maxDp = BzDims.CONTENT_MAX_WIDTH, fill = true)
        val side = if (context.resources.configuration.screenWidthDp >= 640) 24 else 16
        messagesBox.pad(side, 24, side, 24)
        messagesWrap.addView(messagesBox, LayoutParams(MATCH, WRAP))
        body.addView(messagesWrap, LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL))
        scroll.isFillViewport = true
        scroll.addView(body, LayoutParams(MATCH, WRAP))

        val cont = BzButton(context, "Continue", BzButtonStyle.SOFT_BRAND, textSp = 14f, radiusDp = 100, hPad = 20, vPad = 8)
        cont.setOnClickListener { engine.send("Continue") }
        continueRow.addView(cont, LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL))

        main.add(header, MATCH, BzDims.HEADER_HEIGHT)
        main.add(View(context).apply { setBackgroundColor(BzColors.border) }, MATCH, 1)
        main.add(scroll, MATCH, 0, weight = 1f)
        main.add(input, MATCH)
        root.add(sidebarSlot, size(BzDims.SIDEBAR_WIDTH + 1), MATCH)
        root.add(main, 0, MATCH, weight = 1f)
        addView(root, LayoutParams(MATCH, MATCH))

        // оверлеи
        drawer = BzDrawer(context, drawerHolder, BzDims.SIDEBAR_WIDTH + 1)
        addView(drawer, LayoutParams(MATCH, MATCH))

        artifactPanel = BzArtifactPanel(context, client, onClose = {
            openArtifact = null
            artifactPanel.hide()
        }, toast = { toastHost.show(it) })
        addView(artifactPanel, LayoutParams(MATCH, MATCH))
        addView(toastHost, LayoutParams(MATCH, MATCH))

        input.onSend = { text, files -> engine.send(text, files) }
        input.onStop = { engine.stop() }
        input.onPickFiles = { launchPicker() }

        applyMode(false)
    }

    /* ─────────────────────────── жизненный цикл ─────────────────────────── */

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        engine.addListener(this)
        requestApplyInsets()
        scheduleRender()
    }

    override fun onDetachedFromWindow() {
        engine.removeListener(this)
        super.onDetachedFromWindow()
    }

    override fun onChanged(state: ChatState) = scheduleRender()

    /** Не чаще одного раза за кадр — стрим шлёт события на каждый токен. */
    private fun scheduleRender() {
        if (renderScheduled) return
        renderScheduled = true
        Choreographer.getInstance().postFrameCallback {
            renderScheduled = false
            if (isAttachedToWindow) render()
        }
    }

    /* ─────────────────────────── адаптивность и insets ─────────────────────────── */

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val wdp = (w / resources.displayMetrics.density).toInt()
        if (wdp == lastWidthDp) return
        lastWidthDp = wdp
        applyMode(wdp >= BzDims.WIDE_BREAKPOINT)
        welcome.setColumns(if (wdp >= BzDims.SUGGESTION_2COL) 2 else 1)
    }

    /** ≥768dp: постоянный сайдбар слева (md:block); иначе — выезжающий drawer. */
    private fun applyMode(wide: Boolean) {
        this.wide = wide
        (sidebarShell.parent as? ViewGroup)?.removeView(sidebarShell)
        if (wide) {
            drawer.closeImmediately()
            sidebarSlot.visibility = VISIBLE
            sidebarSlot.addView(sidebarShell, LayoutParams(MATCH, MATCH))
            menuBtn.visibility = GONE
        } else {
            sidebarSlot.visibility = GONE
            drawerHolder.addView(sidebarShell, LayoutParams(MATCH, MATCH))
            menuBtn.visibility = VISIBLE
        }
    }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            insetTop = bars.top
            insetBottom = maxOf(bars.bottom, ime.bottom)
        } else {
            insetTop = insets.systemWindowInsetTop
            insetBottom = insets.systemWindowInsetBottom
        }
        header.setPadding(dpi(12), insetTop, dpi(12), 0)
        header.layoutParams = header.layoutParams.apply { height = dpi(BzDims.HEADER_HEIGHT) + insetTop }
        sidebar.setInsets(insetTop, insetBottom)
        input.setBottomInset(insetBottom)
        artifactPanel.setInsets(insetTop, insetBottom)
        return insets
    }

    /* ─────────────────────────── отрисовка ─────────────────────────── */

    private fun render() {
        val st = engine.state
        val session = st.active
        val messages = session?.messages ?: emptyList()

        sidebar.bind(st.sessions, st.activeId)
        input.setStreaming(st.streaming)

        val sessionChanged = session?.id != renderedSessionId
        val nearBottom = run {
            val child = scroll.getChildAt(0)
            child == null || child.bottom - (scroll.scrollY + scroll.height) < dpi(96)
        }
        val grew = messages.size > renderedCount
        val animateNew = !sessionChanged

        welcome.visibility = if (messages.isEmpty()) VISIBLE else GONE
        messagesWrap.visibility = if (messages.isEmpty()) GONE else VISIBLE
        if (messages.isEmpty()) messageViews.clear()

        if (sessionChanged) messageViews.clear()

        val lastAssistantId = messages.lastOrNull { it.role == Role.ASSISTANT }?.id
        val desired = ArrayList<View>()
        val live = HashSet<String>()
        for (m in messages) {
            // пустой ответ ассистента не показываем (вместо него — «думает…»)
            if (m.role == Role.ASSISTANT && m.content.isEmpty() && m.parts.isEmpty()) continue
            live.add(m.id)
            var v = messageViews[m.id]
            if (v == null) {
                v = if (m.role == Role.USER) BzUserMessageView(context, messageCallbacks) else BzAssistantMessageView(context, messageCallbacks)
                messageViews[m.id] = v
                if (animateNew) enter(v)
            }
            if (v is BzUserMessageView) v.bind(m, !st.streaming)
            else if (v is BzAssistantMessageView) v.bind(m, st.streaming && m.id == lastAssistantId)
            desired.add(v)
        }
        messageViews.keys.retainAll(live)

        val last = messages.lastOrNull()
        val waiting = st.streaming && last != null && last.role == Role.ASSISTANT && last.parts.isEmpty() && last.content.isEmpty()
        if (waiting) desired.add(thinking)
        val lastAssistant = messages.lastOrNull { it.id == lastAssistantId }
        if (!st.streaming && lastAssistant?.content?.contains(BzConfig.TOOL_LIMIT_MARK) == true) desired.add(continueRow)

        sync(desired)

        // открытый артефакт
        val art = openArtifact?.let { (mid, idx) -> messages.firstOrNull { it.id == mid }?.artifacts?.getOrNull(idx) }
        if (art != null) artifactPanel.show(art) else {
            openArtifact = null
            artifactPanel.hide()
        }

        renderedSessionId = session?.id
        renderedCount = messages.size
        if (sessionChanged || grew || nearBottom) scrollToBottom()
    }

    /** Приводит дочерние элементы ленты к [desired] с минимумом перестановок. */
    private fun sync(desired: List<View>) {
        for ((i, v) in desired.withIndex()) {
            if (messagesBox.getChildAt(i) === v) continue
            (v.parent as? ViewGroup)?.removeView(v)
            messagesBox.addView(v, i, v.linearLp(MATCH, WRAP, mt = if (i > 0) 24 else 0))
        }
        while (messagesBox.childCount > desired.size) messagesBox.removeViewAt(messagesBox.childCount - 1)
        // отступы зависят от позиции
        for (i in 0 until messagesBox.childCount) {
            val c = messagesBox.getChildAt(i)
            val lp = c.layoutParams as LinearLayout.LayoutParams
            val mt = if (i > 0) dpi(24) else 0
            if (lp.topMargin != mt) { lp.topMargin = mt; c.layoutParams = lp }
        }
    }

    private fun scrollToBottom() {
        scroll.post {
            val child = scroll.getChildAt(0) ?: return@post
            scroll.scrollTo(0, maxOf(0, child.height - scroll.height))
        }
    }

    /** animate-msg-in: появление снизу за 0.35 с. */
    private fun enter(v: View) {
        v.alpha = 0f
        v.translationY = dpi(8).toFloat()
        v.animate().alpha(1f).translationY(0f).setDuration(350)
            .setInterpolator(PathInterpolator(0.2f, 0.8f, 0.3f, 1f)).start()
    }

    /* ─────────────────────────── файлы ─────────────────────────── */

    private fun activity(): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    private fun launchPicker() {
        val a = activity() ?: return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        @Suppress("DEPRECATION")
        a.startActivityForResult(intent, REQUEST_PICK_FILES)
    }

    /** Вызовите из Activity.onActivityResult (передайте результат сюда). @return true, если результат был для этого экрана. */
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_PICK_FILES) return false
        if (resultCode != Activity.RESULT_OK || data == null) return true
        val uris = ArrayList<android.net.Uri>()
        data.clipData?.let { cd -> for (i in 0 until cd.itemCount) uris.add(cd.getItemAt(i).uri) }
        if (uris.isEmpty()) data.data?.let { uris.add(it) }
        val resolver = context.contentResolver
        val files = uris.mapNotNull { uri ->
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // не все провайдеры поддерживают постоянный доступ — хватит временного
            }
            var name = "file"
            var size = 0L
            resolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            UploadFile(name, mime, size) { resolver.openInputStream(uri) ?: throw java.io.IOException("Файл недоступен: $name") }
        }
        input.addFiles(files)
        return true
    }

    /* ─────────────────────────── «Назад» ─────────────────────────── */

    /** Закрывает верхний слой (меню модели, панель артефакта, drawer). @return true, если что-то закрыто. */
    fun handleBack(): Boolean {
        if (modelMenu.isShowing) { modelMenu.dismiss(); return true }
        if (artifactPanel.panelVisible) { openArtifact = null; artifactPanel.hide(); return true }
        if (drawer.isOpen) { drawer.close(); return true }
        return false
    }
}
