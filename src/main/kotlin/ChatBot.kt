package mcbot

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import mcbot.Mcbot.reload
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.nextEvent
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.isUploaded
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import java.io.File
import java.net.URL
import javax.imageio.ImageIO
import kotlin.math.min

@Suppress("unused")
object ChatBot : Function(true) {
    val imagePath = Mcbot.dataFolderPath.toString() + "/ChatBot"
    suspend inline fun buildFromCode(code: String, group: Group) =
        code.deserializeMiraiCode(group).map {
            if (it is Image && !it.isUploaded(group.bot)) {
                group.uploadImage(
                    File(
                        imagePath + "/${group.id}",
                        it.imageId
                    )
                )
            } else it
        }.toMessageChain()

    object DataBase : AutoSavePluginConfig("ChatBotDataBase") {
        @Serializable
        data class GroupData(
            var status: Boolean = true,
            val Eq: MutableMap<String, MutableList<String>> = mutableMapOf(),
            val Cn: MutableMap<String, MutableList<String>> = mutableMapOf()
        ) {
            suspend fun match(event: GroupMessageEvent): Message? {
                val msg = event.message
                if (msg.toMessageChain().all { it is PlainText || it !is MessageContent } && msg.content in Eq)
                    return buildFromCode(Eq[msg.content]!!.random(), event.group)
                val candi = mutableListOf<String>()
                for (str in msg.toMessageChain().filterIsInstance<PlainText>()) {
                    for (item in Cn) {
                        if (str.content.contains(item.key)) {
                            candi.add(item.key)
                        }
                    }
                }
                if (candi.size > 0) return buildFromCode(Cn[candi.random()]!!.random(), event.group)
                return null
            }
        }

        private val dataBase: MutableMap<Long, GroupData> by value()
        operator fun get(id: Long): GroupData {
            if (dataBase[id] == null) dataBase[id] = GroupData()
            return dataBase[id]!!
        }
    }

    object RefCount : AutoSavePluginData("ChatBot/ReferenceCount") {
        private val refCount: MutableMap<Long, MutableMap<String, Int>> by value()
        fun refPlus(groupId: Long, imageId: String) {
            if (refCount[groupId] == null) refCount[groupId] = mutableMapOf()
            if (refCount[groupId]!![imageId] == null) refCount[groupId]!![imageId] = 1
            else refCount[groupId]!![imageId] = refCount[groupId]!![imageId]!! + 1
        }

        fun refMinus(groupId: Long, imageId: String) {
            if (refCount[groupId] == null || refCount[groupId]!![imageId] == null) {
                File(imagePath + "/${groupId}", imageId).delete()
                return
            }
            refCount[groupId]!![imageId] = refCount[groupId]!![imageId]!! - 1
            if (refCount[groupId]!![imageId]!! == 0) refCount[groupId]!!.remove(imageId)
        }
    }

    object Remember : SimpleCommand(Mcbot, "remember", parentPermission = Mcbot.normalPermission) {
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
                                    withContext(Dispatchers.IO) {
                                        val f = File(imagePath + "/${group.id}", it.imageId)
                                        if (f.exists()) {
                                            RefCount.refPlus(group.id, it.imageId)
                                        } else {
                                            f.parentFile.mkdirs()
                                            ImageIO.write(ImageIO.read(URL(it.queryUrl())), it.imageType.name, f)
                                        }
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

    object Forget : SimpleCommand(Mcbot, "forget", parentPermission = Mcbot.adminPermission) {
        @Handler
        suspend fun CommandSender.onCommand(vararg args: String) {
            if (this is MemberCommandSenderOnMessage && fromEvent.message.all { it is PlainText || it !is MessageContent }) {
                val data = DataBase[group.id]
                if (data.status) {
                    if (args.size >= 2) {
                        val param = mutableMapOf(
                            'i' to false,
                            'k' to false
                        )
                        val key: String
                        val value: List<Int>
                        if (args[0].startsWith("-")) {
                            for (char in args[0].removePrefix("-")) {
                                if (char in param) {
                                    param[char] = true
                                } else {
                                    group.sendMessage("Unknown parameter ${char}.")
                                    return
                                }
                            }
                            key = args[1]
                            try {
                                value = args.slice(2 until args.size).map { it.toInt() }
                            } catch (err: Exception) {
                                group.sendMessage(err.message ?: "Internal error.")
                                return
                            }
                        } else {
                            key = args[0]
                            try {
                                value = args.slice(1 until args.size).map { it.toInt() }
                            } catch (err: Exception) {
                                group.sendMessage(err.message ?: "Internal error.")
                                return
                            }
                        }
                        fun delFromCode(code: String) {
                            """\[mirai:image:\{[\da-fA-F-]+}\.[a-zA-Z]+]""".toRegex()
                                .findAll(code).forEach {
                                    RefCount.refMinus(group.id, it.value.removeSurrounding("[mirai:image:", "]"))
                                }
                        }

                        val list = if (param['i']!!) data.Cn else data.Eq
                        if (key in list) {
                            if (param['k']!!) {
                                list[key]!!.forEach { delFromCode(it) }
                                list.remove(key)
                            } else {
                                for (i in value) {
                                    if (i <= list[key]!!.size && i > 0) {
                                        delFromCode(list[key]!![i - 1])
                                        list[key]!!.removeAt(i - 1)
                                    }
                                }
                                if (list[key]!!.isEmpty()) list.remove(key)
                            }
                        } else {
                            group.sendMessage("$key not found.")
                            return
                        }
                        group.sendMessage("Done.")
                    } else {
                        group.sendMessage("Syntax error.")
                    }
                }
            }
        }
    }

    object LookUp : SimpleCommand(Mcbot, "lookup", parentPermission = Mcbot.normalPermission) {
        const val messagePerPage=20
        data class GroupSearchResult(var page: Int, val forward: Boolean, val result: List<String>, val key: String) {
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
                if (forward) {
                    lastReply =
                        msg.group.sendMessage(buildForwardMessage(msg.group, object : ForwardMessage.DisplayStrategy {
                            override fun generateTitle(forward: RawForwardMessage): String = "${key}的查询结果"
                            override fun generatePreview(forward: RawForwardMessage): List<String> =
                                listOf("共${result.size}条结果")

                            override fun generateSummary(forward: RawForwardMessage): String =
                                "#Page:${page}/${(result.size - 1) / messagePerPage}"

                            override fun generateBrief(forward: RawForwardMessage): String = "[查询结果]"
                        }) {
                            val time = currentTime - messagePerPage
                            for (i in (messagePerPage*page until min(messagePerPage*(page+1),result.size))) {
                                msg.bot at time + i % messagePerPage says PlainText("#${i + 1}:") + buildFromCode(
                                    result[i],
                                    msg.group
                                )
                            }
                            msg.bot at currentTime says "#Page:${page}/${(result.size - 1) / messagePerPage}"
                        }).source
                } else {
                    lastReply = msg.group.sendMessage(
                        QuoteReply(msg.message) + result.drop(messagePerPage * page).take(messagePerPage)
                            .joinToString("\n") + "\n#Page:${page}/${(result.size - 1) / messagePerPage}"
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
                        'k' to false
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
                    val result = if (param['i']!!) data.Cn else data.Eq
                    if (param['k']!!) {
                        if (result.contains(key)) {
                            searchResult.remove(group.id)
                            searchResult[group.id] = GroupSearchResult(0, true, result[key]!!, key)
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
                                GroupSearchResult(0, false, result.keys.filter { it.contains(key) }, key)
                            searchResult[group.id]!!.handle(fromEvent)
                        }
                    }
                }
            }
        }
    }

    object ChatBot : CompositeCommand(Mcbot, "chatbot", parentPermission = Mcbot.adminPermission) {
        @SubCommand
        suspend fun CommandSender.on() {
            if (this is MemberCommandSenderOnMessage) {
                val data = DataBase[group.id]
                if (data.status) {
                    group.sendMessage("自动回复已经是开启状态")
                } else {
                    data.status = false
                    group.sendMessage("自动回复已开启")
                }
            }
        }

        @SubCommand
        suspend fun CommandSender.off() {
            if (this is MemberCommandSenderOnMessage) {
                val data = DataBase[group.id]
                if (data.status) {
                    data.status = false
                    group.sendMessage("自动回复已关闭")
                } else {
                    group.sendMessage("自动回复已经是关闭状态")
                }
            }
        }
    }

    private val mute = listOf("Recall", "Hitokoto")
    override suspend operator fun invoke(event: Event, list: MutableMap<String, Deferred<Boolean>>): Boolean {
        if (status &&
            event is GroupMessageEvent &&
            DataBase[event.group.id].status &&
            !mute.map { list[it] ?: Mcbot.async { false } }.awaitAll().any { it } &&
            !event.message.content.startsWith(CommandManager.commandPrefix)
        ) {
            val quote = event.message[QuoteReply]?.source
            val last = LookUp.searchResult[event.group.id]?.lastReply
            if (quote != null &&
                last != null &&
                quote.ids.contentEquals(last.ids) &&
                quote.fromId == last.fromId
            ) {
                LookUp.searchResult[event.group.id]?.handle(event)
                return true
            } else {
                val reply = DataBase[event.group.id].match(event)
                if (reply != null) {
                    event.group.sendMessage(reply)
                    return true
                }
            }
        }
        return false
    }

    init {
        DataBase.reload()
        RefCount.reload()
        if (status) {
            Remember.register()
            Forget.register()
            LookUp.register()
            ChatBot.register()
        }
    }

    override fun unload() {
        if (status) {
            Remember.unregister()
            Forget.unregister()
            LookUp.unregister()
            ChatBot.unregister()
        }
    }

    override fun enable() {
        super.enable()
        Remember.register()
        Forget.register()
        LookUp.register()
        ChatBot.register()
    }

    override fun disable() {
        super.disable()
        Remember.unregister()
        Forget.unregister()
        LookUp.unregister()
        ChatBot.unregister()
        LookUp.searchResult.clear()
    }
}