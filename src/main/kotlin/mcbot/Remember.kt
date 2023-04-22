package mcbot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mcbot.DataBase.getFile
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.nextEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import java.io.File
import java.net.URL

object Remember : SimpleCommand(Mcbot, "remember") {
    @Handler
    suspend fun CommandSender.onCommand(vararg args: String) {
        if (this is MemberCommandSenderOnMessage && fromEvent.message.all { it is PlainText || it !is MessageContent }) {
            val data = DataBase[group.id]
            if (data.status) {
                fun pushString(key: String, value: List<String>, inline: Boolean) {
                    val list = if (inline) data.Cn else data.Eq
                    if (!list.contains(key)) list[key] = mutableListOf()
                    for (elem in value) {
                        if (elem !in list[key]!!) {
                            list[key]!!.add(elem)
                        }
                    }
                }

                suspend fun pushMessage(
                    key: String,
                    value: List<MessageChain>,
                    inline: Boolean
                ) {
                    val list = if (inline) data.Cn else data.Eq
                    if (!list.contains(key)) list[key] = mutableListOf()
                    for (elem in value) {
                        if (elem.serializeToMiraiCode() !in list[key]!!) {
                            list[key]!!.add(elem.serializeToMiraiCode())
                            elem.filterIsInstance<Image>().forEach {
                                if (it.imageType == ImageType.UNKNOWN) {
                                    sendMessage(QuoteReply(elem) + "图片下载失败.")
                                    return@forEach
                                }
                                withContext(Dispatchers.IO) {
                                    val getter=it.getFile()
                                    val file=getter(0)
                                    if (file.exists()) file.renameTo(getter(1))
                                    else file.writeBytes(URL(it.queryUrl()).openStream().readBytes())
                                }
                            }
                        }
                    }
                }
                if (args.size == 1) {
                    val msg =
                        GlobalEventChannel.nextEvent<GroupMessageEvent> { it.bot == bot && it.group == group && it.sender.id == this@onCommand.user.id }.message
                    if (msg.all { it !is MessageContent || (it is PlainText || it is Image || it is At || it is Face) }) {
                        pushMessage(args[0], listOf(msg), false)
                    } else if (msg.all { it is ForwardMessage || it !is MessageContent }) {
                        pushMessage(args[0],
                            msg[ForwardMessage]!!.nodeList.filter { it -> it.messageChain.all { it !is MessageContent || (it is PlainText || it is Image || it is At || it is Face) } }
                                .map { it.messageChain },
                            false
                        )
                    }
                } else if (args.size == 2 && args[0] == "-i") {
                    val msg =
                        GlobalEventChannel.nextEvent<GroupMessageEvent> { it.bot == bot && it.group == group && it.sender.id == this@onCommand.user.id }.message
                    if (msg.all { it !is MessageContent || (it is PlainText || it is Image || it is At || it is Face) }) {
                        pushMessage(args[1], listOf(msg), true)
                    } else if (msg.all { it is ForwardMessage || it !is MessageContent }) {
                        pushMessage(args[1],
                            msg[ForwardMessage]!!.nodeList.filter { it -> it.messageChain.all { it !is MessageContent || (it is PlainText || it is Image || it is At || it is Face) } }
                                .map { it.messageChain },
                            true
                        )
                    }
                } else if (args.size > 1) {
                    if (args[0] == "-i") {
                        pushString(args[1], args.slice(2 until args.size), true)
                    } else {
                        pushString(args[0], args.slice(1 until args.size), false)
                    }
                }
                group.sendMessage("Done.")
            }
        }
    }
}