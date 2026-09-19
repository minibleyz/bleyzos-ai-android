package ru.bleyzos.ai.design.widgets

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import ru.bleyzos.ai.api.model.AttachmentKind
import ru.bleyzos.ai.api.model.Attachments
import ru.bleyzos.ai.design.icons.BzIcon
import ru.bleyzos.ai.design.icons.BzIconView
import ru.bleyzos.ai.design.theme.BzColors
import ru.bleyzos.ai.design.theme.BzDrawables
import ru.bleyzos.ai.design.theme.dpi

internal fun attachmentIcon(mime: String, name: String): BzIcon = when (Attachments.kind(mime, name)) {
    AttachmentKind.IMAGE -> BzIcon.IMAGE
    AttachmentKind.AUDIO -> BzIcon.FILE_AUDIO
    AttachmentKind.VIDEO -> BzIcon.FILM
    AttachmentKind.ARCHIVE -> BzIcon.FILE_ARCHIVE
    AttachmentKind.CODE -> BzIcon.FILE_CODE
    AttachmentKind.PDF, AttachmentKind.TEXT -> BzIcon.FILE_TEXT
    AttachmentKind.FILE -> BzIcon.FILE
}

/** Чип вложения: иконка, имя (до 150dp), размер, необязательный крестик — attachment-chip.tsx. */
class BzAttachmentChip(
    context: Context,
    name: String,
    size: Long,
    mime: String,
    onRemove: (() -> Unit)? = null,
) : LinearLayout(context) {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = BzDrawables.rect(context, BzColors.card, 10, BzColors.border, 1)
        elevation = dp(1)
        padSym(10, 6)

        add(BzIconView(context, attachmentIcon(mime, name), 14, BzColors.brand), size(14), size(14), mr = 6)

        val title = context.bzText(name, 12f, BzColors.primary, 500, singleLine = true)
        title.maxWidth = dpi(150)
        add(title)
        add(context.bzText(Attachments.formatBytes(size), 12f, BzColors.mutedForeground), ml = 6)

        if (onRemove != null) {
            val x = BzIconButton(context, BzIcon.X, sizeDp = 16, iconDp = 12, radiusDp = 8)
            x.setOnClickListener { onRemove() }
            x.contentDescription = "Убрать файл $name"
            add(x, size(16), size(16), ml = 6)
        }
    }

    private fun dp(v: Number) = context.resources.displayMetrics.density * v.toFloat()

}
