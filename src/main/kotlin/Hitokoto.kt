package mcbot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.MessageContent
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.content
import java.net.URL

object Hitokoto:Function(true) {
    suspend operator fun invoke(msg: MessageEvent): Boolean {
        if (status) {
            val message = msg.message
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
                )[content.substring(0 until content.length - 2)]
                if (suffix != null) {
                    val json = Json.parseToJsonElement(withContext(Dispatchers.IO) {
                        URL("https://v1.hitokoto.cn/" + if (suffix == "") "" else "?c=$suffix").openStream()
                    }.readBytes().decodeToString()).jsonObject
                    val from = json["from"]
                    val fromWho = json["from_who"]
                    (if (msg is GroupMessageEvent) msg.group else msg.sender).sendMessage(
                        "「${
                            json["hitokoto"]?.jsonPrimitive?.contentOrNull
                        }」${
                            if (from != null) {
                                if (fromWho != null) {
                                    " --${from.jsonPrimitive.content} ${fromWho.jsonPrimitive.content}"
                                } else {
                                    " --${from.jsonPrimitive.content}"
                                }
                            } else if (fromWho != null) {
                                " --${fromWho.jsonPrimitive.content}"
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