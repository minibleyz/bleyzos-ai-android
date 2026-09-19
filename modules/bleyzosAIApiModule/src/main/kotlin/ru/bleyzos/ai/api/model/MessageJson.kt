package ru.bleyzos.ai.api.model

import org.json.JSONArray
import org.json.JSONObject

/*
 * JSON-формат совпадает с тем, что веб-версия кладёт в meta.message,
 * поэтому файлы истории читаемы и сервером, и веб-клиентом.
 */

private fun JSONObject.strOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

internal fun Attachment.toJson(): JSONObject = JSONObject()
    .put("id", id).put("name", name).put("size", size).put("mime", mime)

internal fun attachmentFromJson(o: JSONObject) = Attachment(
    id = o.optString("id"),
    name = o.optString("name"),
    size = o.optLong("size"),
    mime = o.optString("mime"),
)

internal fun ToolCall.toJson(): JSONObject {
    val a = JSONObject()
    args.forEach { (k, v) -> a.put(k, v) }
    return JSONObject().put("id", id).put("name", name).put("args", a)
        .put("status", status.wire)
        .apply {
            if (output != null) put("output", output)
            if (ms != null) put("ms", ms)
        }
}

internal fun toolCallFromJson(o: JSONObject): ToolCall {
    val args = LinkedHashMap<String, String>()
    o.optJSONObject("args")?.let { a ->
        val it = a.keys()
        while (it.hasNext()) {
            val k = it.next()
            args[k] = a.opt(k)?.toString().orEmpty()
        }
    }
    // Инструмент, который «бежал» в момент закрытия приложения, считаем прерванным.
    val status = ToolStatus.from(o.optString("status")).let { if (it == ToolStatus.RUNNING) ToolStatus.ERROR else it }
    return ToolCall(
        id = o.optString("id"),
        name = o.optString("name"),
        args = args,
        status = status,
        output = o.strOrNull("output"),
        ms = if (o.has("ms") && !o.isNull("ms")) o.optLong("ms") else null,
    )
}

internal fun Artifact.toJson(): JSONObject = JSONObject().put("name", name).put("kind", kind.wire).apply {
    if (content != null) put("content", content)
    if (url != null) put("url", url)
}

internal fun artifactFromJson(o: JSONObject) = Artifact(
    name = o.optString("name"),
    kind = ArtifactKind.from(o.optString("kind")),
    content = o.strOrNull("content"),
    url = o.strOrNull("url"),
)

internal fun Message.toJson(): JSONObject {
    val o = JSONObject().put("id", id).put("role", role.wire).put("content", content)
    if (attachments.isNotEmpty()) o.put("attachments", JSONArray().also { arr -> attachments.forEach { arr.put(it.toJson()) } })
    if (parts.isNotEmpty()) o.put("parts", JSONArray().also { arr ->
        parts.forEach { p ->
            arr.put(
                when (p) {
                    is Part.Text -> JSONObject().put("kind", "text").put("text", p.text)
                    is Part.Tool -> JSONObject().put("kind", "tool").put("call", p.call.toJson())
                }
            )
        }
    })
    if (artifacts.isNotEmpty()) o.put("artifacts", JSONArray().also { arr -> artifacts.forEach { arr.put(it.toJson()) } })
    variants?.let { vs ->
        o.put("variants", JSONArray().also { arr ->
            vs.forEach { v ->
                arr.put(
                    JSONObject().put("content", v.content)
                        .put("tail", JSONArray().also { t -> v.tail.forEach { m -> t.put(m.toJson()) } })
                )
            }
        })
    }
    variantIndex?.let { o.put("variantIndex", it) }
    return o
}

internal fun messageFromJson(o: JSONObject): Message {
    val attachments = o.optJSONArray("attachments")?.let { a ->
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::attachmentFromJson) }
    } ?: emptyList()
    val parts = o.optJSONArray("parts")?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            val p = a.optJSONObject(i) ?: return@mapNotNull null
            when (p.optString("kind")) {
                "text" -> Part.Text(p.optString("text"))
                "tool" -> p.optJSONObject("call")?.let { Part.Tool(toolCallFromJson(it)) }
                else -> null
            }
        }
    } ?: emptyList()
    val artifacts = o.optJSONArray("artifacts")?.let { a ->
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::artifactFromJson) }
    } ?: emptyList()
    val variants = o.optJSONArray("variants")?.let { a ->
        (0 until a.length()).mapNotNull { i ->
            val v = a.optJSONObject(i) ?: return@mapNotNull null
            val tail = v.optJSONArray("tail")?.let { t ->
                (0 until t.length()).mapNotNull { j -> t.optJSONObject(j)?.let(::messageFromJson) }
            } ?: emptyList()
            MessageVariant(v.optString("content"), tail)
        }
    }
    return Message(
        id = o.optString("id"),
        role = Role.from(o.optString("role")),
        content = o.optString("content"),
        attachments = attachments,
        parts = parts,
        artifacts = artifacts,
        variants = variants,
        variantIndex = if (o.has("variantIndex") && !o.isNull("variantIndex")) o.optInt("variantIndex") else null,
    )
}

internal fun Session.toJson(): JSONObject = JSONObject()
    .put("id", id).put("title", title).put("updatedAt", updatedAt)
    .put("messages", JSONArray().also { arr -> messages.forEach { arr.put(it.toJson()) } })

internal fun sessionFromJson(o: JSONObject): Session {
    val msgs = o.optJSONArray("messages")?.let { a ->
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::messageFromJson) }
    } ?: emptyList()
    return Session(o.optString("id"), o.optString("title"), msgs, o.optLong("updatedAt"))
}
