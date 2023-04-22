package mcbot

import mcbot.DataBase.buildFromCode
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.*
import kotlin.math.min

object LookUp : SimpleCommand(Mcbot, "lookup") {
    data class GroupSearchResult(
        var page: Int,
        val inline: Boolean,
        val forward: Boolean,
        val result: List<String>,
        val key: String,
        val messagePerPage: Int
    ) {
        var lastReply: MessageSource? = null
        suspend fun handle(msg: GroupMessageEvent) {
            if (lastReply != null) {
                when (msg.message.content) {
                    "prev" -> {
                        if (page > 0) page--
                        else {
                            msg.group.sendMessage(QuoteReply(msg.message) + "Index out of range.")
                            return
                        }
                    }
                    "next" -> {
                        if (page >= (result.size - 1) / messagePerPage) {
                            msg.group.sendMessage(QuoteReply(msg.message) + "Index out of range.")
                            return
                        } else page++
                    }
                    else -> {
                        try {
                            val set = msg.message.content.toInt()
                            if (set < 0 || set > (result.size - 1) / messagePerPage) {
                                msg.group.sendMessage(QuoteReply(msg.message) + "Index out of range.")
                                return
                            }
                            page = set
                        } catch (e: Exception) {
                            msg.group.sendMessage(QuoteReply(msg.message) + (e.message ?: "Internal error"))
                            return
                        }
                    }
                }
            }
            if (inline) {
                if (forward) {
                    lastReply =
                        msg.group.sendMessage(buildForwardMessage(
                            msg.group,
                            object : ForwardMessage.DisplayStrategy {
                                override fun generateTitle(forward: RawForwardMessage): String = "${key}的查询结果"
                                override fun generatePreview(forward: RawForwardMessage): List<String> =
                                    listOf("共${result.size}条结果")

                                override fun generateSummary(forward: RawForwardMessage): String =
                                    "#Page:${page}/${(result.size - 1) / 100}"

                                override fun generateBrief(forward: RawForwardMessage): String = "[查询结果]"
                            }) {
                            val time = currentTime - 100
                            for (i in (100 * page until min(100 * (page + 1), result.size))) {
                                msg.bot at time + i % 100 says PlainText("#${i + 1}:") + result[i].buildFromCode(msg.group)
                            }
                        }).source
                } else {
                    var reply: Message = QuoteReply(msg.message)
                    for (i in (messagePerPage * page until min(messagePerPage * (page + 1), result.size))) {
                        reply += PlainText("${if (i % messagePerPage == 0) "" else "\n"}#${i + 1}:") + result[i].buildFromCode(msg.group)
                    }
                    lastReply =
                        msg.group.sendMessage(reply + if (result.size > messagePerPage) "\n#Page:${page}/${(result.size - 1) / messagePerPage}" else "").source
                }
            } else {
                lastReply = msg.group.sendMessage(
                    QuoteReply(msg.message) + result.drop(messagePerPage * page).take(messagePerPage)
                        .joinToString("\n") + if (result.size > messagePerPage) "\n#Page:${page}/${(result.size - 1) / messagePerPage}" else ""
                ).source
            }
        }
    }

    val searchResult = mutableMapOf<Long, GroupSearchResult>()

    @Handler
    suspend fun CommandSender.onCommand(vararg args: String) {
        if (this is MemberCommandSenderOnMessage && fromEvent.message.all { it is PlainText || it !is MessageContent }) {
            val data = DataBase[group.id]
            if (data.status) {
                val param = mutableMapOf(
                    'i' to false,
                    'k' to false,
                    'f' to false,
                    'n' to false
                )
                val key: String
                if (args.isEmpty()) key = ""
                else if (args[0].startsWith("-")) {
                    for (char in args[0].removePrefix("-")) {
                        if (char in param) {
                            param[char] = true
                        } else {
                            group.sendMessage("Unknown parameter ${char}.")
                            return
                        }
                    }
                    key = if (args.size > 1) args[1] else ""
                } else key = args[0]
                if (param['n']!!) {
                    try {
                        DataBase[group.id].messagePerPage = key.toInt()
                        sendMessage("Done.")
                    } catch (e: Exception) {
                        sendMessage(e.message ?: "Internal error.")
                    }
                    return
                }
                val result = if (param['i']!!) data.Cn else data.Eq
                if (param['k']!!) {
                    if (result.contains(key)) {
                        searchResult.remove(group.id)
                        searchResult[group.id] = GroupSearchResult(
                            0,
                            true,
                            param['f']!!,
                            result[key]!!,
                            key,
                            DataBase[group.id].messagePerPage
                        )
                        searchResult[group.id]!!.handle(fromEvent)
                    } else {
                        group.sendMessage(QuoteReply(fromEvent.message) + "$key not found.")
                        return
                    }
                } else {
                    if (result.keys.none { it.contains(key) }) {
                        group.sendMessage(QuoteReply(fromEvent.message) + "Empty.")
                    } else {
                        searchResult.remove(group.id)
                        searchResult[group.id] =
                            GroupSearchResult(
                                0,
                                false,
                                param['f']!!,
                                result.keys.filter { it.contains(key) },
                                key,
                                DataBase[group.id].messagePerPage
                            )
                        searchResult[group.id]!!.handle(fromEvent)
                    }
                }
            }
        }
    }
}