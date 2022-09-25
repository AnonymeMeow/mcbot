package mcbot

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.MessageContent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.content
import java.net.URL

object Hitokoto:Function(true) {
    override suspend operator fun invoke(event: Event, list: MutableMap<String, Deferred<Boolean>>): Boolean {
        if (status && event is MessageEvent) {
            val message = event.message
            val content = message.content
            if (message.all { it is PlainText || it !is MessageContent } && content.endsWith("一言")) {
                val suffix = mapOf(
                    "" to "",
                    "动画" to "a",
                    "漫画" to "b",
                    "游戏" to "c",
                    "文学" to "d",
                    "原创" to "e",
                    "网络" to "f",
                    "其他" to "g",
                    "影视" to "h",
                    "诗词" to "i",
                    "网易云" to "j",
                    "哲学" to "k",
                    "抖机灵" to "l"
                )[content.removeSuffix("一言")]
                if (suffix != null) {
                    val json = Json.parseToJsonElement(withContext(Dispatchers.IO) {
                        URL("https://v1.hitokoto.cn/" + if (suffix == "") "" else "?c=$suffix").openStream()
                    }.readBytes().decodeToString()).jsonObject
                    val from = json["from"]?.jsonPrimitive?.contentOrNull
                    val fromWho = json["from_who"]?.jsonPrimitive?.contentOrNull
                    (if (event is GroupMessageEvent) event.group else event.sender).sendMessage(
                        "「${
                            json["hitokoto"]?.jsonPrimitive?.contentOrNull
                        }」${
                            if (from != null) {
                                if (fromWho != null) {
                                    " --$from $fromWho"
                                } else {
                                    " --$from"
                                }
                            } else if (fromWho != null) {
                                " --$fromWho"
                            } else {
                                ""
                            }
                        }"
                    )
                    return true
                }
            }
        }
        return false
    }
}