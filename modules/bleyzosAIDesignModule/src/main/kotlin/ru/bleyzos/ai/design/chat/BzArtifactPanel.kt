package ru.bleyzos.ai.design.chat

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import ru.bleyzos.ai.api.BleyzosAIClient
import ru.bleyzos.ai.api.model.Artifact
import ru.bleyzos.ai.api.model.ArtifactKind
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.markdown.BzMarkdownView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.dpi
import ru.bleyzos.ai.design.widgets.BzButton
import ru.bleyzos.ai.design.widgets.BzButtonStyle
import ru.bleyzos.ai.design.widgets.BzIconButton
import ru.bleyzos.ai.design.widgets.MATCH
import ru.bleyzos.ai.design.widgets.WRAP
import ru.bleyzos.ai.design.widgets.add
import ru.bleyzos.ai.design.widgets.bzText
import ru.bleyzos.ai.design.widgets.hbox
import ru.bleyzos.ai.design.widgets.pad
import ru.bleyzos.ai.design.widgets.size
import ru.bleyzos.ai.design.widgets.vbox

/** Правая выезжающая панель предпросмотра артефакта — artifact-panel.tsx. */
class BzArtifactPanel(
    context: Context,
    private val client: BleyzosAIClient,
    private val onClose: () -> Unit,
    private val toast: (String) -> Unit,
) : FrameLayout(context) {

    private val scrim = View(context).apply { setBackgroundColor(Color.BLACK); alpha = 0.3f }
    private val panel = context.vbox()
    private val header = context.hbox()
    private val body = FrameLayout(context)
    private val title = context.bzText("", 13.5f, BzColors.primary, 600, singleLine = true)
    private val subtitle = context.bzText("", 11f, BzColors.mutedForeground)
    private val iconBox = context.hbox()
    private val icon = BzIconView(context, BzIcon.FILE_CODE, 16, BzColors.alpha(BzColors.foreground, 0.6f))
    private val download: BzIconButton
    private var current: Artifact? = null
    private var web: WebView? = null
    private var topInset = 0
    private var bottomInset = 0

    init {
        visibility = GONE
        scrim.setOnClickListener { onClose() }
        addView(scrim, LayoutParams(MATCH, MATCH))

        panel.setBackgroundColor(BzColors.background)
        panel.isClickable = true // не пропускаем касания к скриму
        BzDrawables.warmShadow(panel, 16)
        val w = if (context.resources.configuration.screenWidthDp < 640) MATCH else dpi(480)
        addView(panel, LayoutParams(w, MATCH, Gravity.END))

        header.pad(16, 0, 16, 0)
        iconBox.gravity = Gravity.CENTER
        iconBox.background = BzDrawables.rect(context, BzColors.card, 9, BzColors.input, 1)
        iconBox.addView(icon, LayoutParams(size(16), size(16)))
        header.add(iconBox, size(32), size(32), mr = 10)
        val titles = context.vbox()
        titles.add(title)
        titles.add(subtitle)
        header.add(titles, weight = 1f, w = 0)
        download = BzIconButton(context, BzIcon.DOWNLOAD, 36, 18)
        download.contentDescription = "Скачать файл"
        download.setOnClickListener { saveCurrent() }
        header.add(download, size(36), size(36))
        val close = BzIconButton(context, BzIcon.X, 36, 18)
        close.contentDescription = "Закрыть панель"
        close.setOnClickListener { onClose() }
        header.add(close, size(36), size(36), ml = 2)
        panel.add(header, MATCH, 56)
        panel.add(View(context).apply { setBackgroundColor(BzColors.border) }, MATCH, 1)
        panel.add(body, MATCH, 0, weight = 1f)
    }

    fun setInsets(top: Int, bottom: Int) {
        topInset = top; bottomInset = bottom
        panel.setPadding(0, top, 0, bottom)
    }

    val panelVisible: Boolean get() = visibility == VISIBLE

    fun show(a: Artifact) {
        if (current === a && panelVisible) return
        val wasShown = panelVisible
        current = a
        title.text = a.name
        subtitle.text = when (a.kind) {
            ArtifactKind.CODE -> "Код"
            ArtifactKind.HTML -> "HTML · живой предпросмотр"
            ArtifactKind.MARKDOWN -> "Markdown"
            ArtifactKind.IMAGE -> "Изображение"
            ArtifactKind.ZIP -> "ZIP-архив"
        }
        icon.icon = artifactIcon(a.kind)
        download.visibility = if (a.url != null) VISIBLE else GONE
        renderBody(a)
        visibility = VISIBLE
        if (!wasShown) {
            scrim.alpha = 0f
            scrim.animate().alpha(0.3f).setDuration(200).start()
            panel.translationX = dpi(520).toFloat()
            panel.animate().translationX(0f).setDuration(250).start()
        }
    }

    fun hide() {
        if (!panelVisible) return
        current = null
        scrim.animate().alpha(0f).setDuration(180).start()
        panel.animate().translationX(dpi(520).toFloat()).setDuration(200).withEndAction {
            visibility = GONE
            releaseWeb()
            body.removeAllViews()
        }.start()
    }

    private fun releaseWeb() {
        web?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.destroy() }
        web = null
    }

    private fun renderBody(a: Artifact) {
        releaseWeb()
        body.removeAllViews()
        when (a.kind) {
            ArtifactKind.HTML -> body.addView(webView().also { wv ->
                wv.settings.javaScriptEnabled = true // как sandbox="allow-scripts" в вебе
                wv.loadDataWithBaseURL(null, a.content.orEmpty(), "text/html", "utf-8", null)
            }, LayoutParams(MATCH, MATCH))
            ArtifactKind.MARKDOWN -> {
                val md = BzMarkdownView(context).apply { pad(20, 16, 20, 16); setContent(a.content.orEmpty()) }
                body.addView(ScrollView(context).apply { addView(md, LayoutParams(MATCH, WRAP)) }, LayoutParams(MATCH, MATCH))
            }
            ArtifactKind.CODE -> {
                val tv = context.bzText(a.content ?: "Пустой файл", 12.5f, BzColors.alpha(BzColors.foreground, 0.85f), mono = true, lineHeightMult = 1.6f)
                tv.setHorizontallyScrolling(true)
                tv.pad(20, 16, 20, 16)
                val h = HorizontalScrollView(context).apply { addView(tv, LayoutParams(WRAP, WRAP)) }
                body.addView(ScrollView(context).apply { isFillViewport = true; addView(h, LayoutParams(WRAP, WRAP)) }, LayoutParams(MATCH, MATCH))
            }
            ArtifactKind.IMAGE -> renderImage(a)
            ArtifactKind.ZIP -> renderZip(a)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun webView(): WebView {
        val wv = WebView(context)
        wv.setBackgroundColor(Color.WHITE)
        wv.settings.apply {
            allowFileAccess = false
            allowContentAccess = false
            javaScriptEnabled = false
            domStorageEnabled = false
            setSupportMultipleWindows(false)
        }
        // Предпросмотр не должен уводить на другие страницы.
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
        web = wv
        return wv
    }

    private fun renderImage(a: Artifact) {
        val holder = FrameLayout(context).apply { pad(24, 24, 24, 24) }
        body.addView(holder, LayoutParams(MATCH, MATCH))
        val url = a.url
        if (url == null) {
            holder.addView(context.bzText("Изображение недоступно", 13f, BzColors.mutedForeground).apply { gravity = Gravity.CENTER }, LayoutParams(MATCH, MATCH))
            return
        }
        holder.addView(context.bzText("Загрузка…", 13f, BzColors.mutedForeground), LayoutParams(WRAP, WRAP, Gravity.CENTER))
        val expected = a
        client.files.fetchBytes(url) { bytes, err ->
            if (current !== expected) return@fetchBytes
            holder.removeAllViews()
            if (bytes == null) {
                holder.addView(context.bzText(err?.message ?: "Не удалось загрузить", 13f, BzColors.error), LayoutParams(WRAP, WRAP, Gravity.CENTER))
                return@fetchBytes
            }
            val bmp: Bitmap? = if (a.name.endsWith(".svg", true)) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) {
                val iv = ImageView(context).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    background = BzDrawables.rect(context, 0, 12)
                    clipToOutline = true
                    contentDescription = a.name
                }
                holder.addView(iv, LayoutParams(MATCH, MATCH))
            } else {
                // SVG и всё, что не декодируется Bitmap-ом, показываем через WebView без скриптов
                val wv = webView()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val mime = if (a.name.endsWith(".svg", true)) "image/svg+xml" else "image/*"
                wv.loadDataWithBaseURL(
                    null,
                    "<html><body style='margin:0;display:flex;align-items:center;justify-content:center;height:100vh;background:#fff'>" +
                        "<img style='max-width:100%;max-height:100%' src='data:$mime;base64,$b64'></body></html>",
                    "text/html", "utf-8", null,
                )
                holder.addView(wv, LayoutParams(MATCH, MATCH))
            }
        }
    }

    private fun renderZip(a: Artifact) {
        val col = context.vbox().apply { gravity = Gravity.CENTER; pad(24, 24, 24, 24) }
        val box = context.hbox().apply {
            gravity = Gravity.CENTER
            background = BzDrawables.rect(context, BzColors.card, 18, BzColors.input, 1)
        }
        box.addView(BzIconView(context, BzIcon.FILE_ARCHIVE, 32, BzColors.brand), LayoutParams(size(32), size(32)))
        col.add(box, size(64), size(64))
        col.add(context.bzText(a.name, 15f, BzColors.primary, 600).apply { gravity = Gravity.CENTER }, mt = 16)
        col.add(context.bzText("Полный архив песочницы, включая скрытые файлы", 13f, BzColors.mutedForeground).apply { gravity = Gravity.CENTER }, mt = 4)
        if (a.url != null) {
            val b = BzButton(context, "Скачать архив", BzButtonStyle.PRIMARY, BzIcon.DOWNLOAD, textSp = 14f, radiusDp = 12, hPad = 20, vPad = 10)
            b.setOnClickListener { saveCurrent() }
            col.add(b, mt = 16)
        }
        body.addView(col, LayoutParams(MATCH, MATCH))
    }

    private fun saveCurrent() {
        val a = current ?: return
        val url = a.url ?: return
        toast("Скачиваю…")
        client.files.saveToDownloads(url, a.name) { uri, err ->
            toast(if (uri != null) "Сохранено в «Загрузки/Bleyzos AI»" else (err?.message ?: "Не удалось сохранить файл"))
        }
    }

    override fun onDetachedFromWindow() {
        releaseWeb()
        super.onDetachedFromWindow()
    }
}
