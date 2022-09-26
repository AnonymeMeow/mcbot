package mcbot

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import mcbot.Mcbot.reload
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.nextEvent
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.*

@Suppress("unused")
object ChatBot:Function(true) {
    object DataBase : AutoSavePluginConfig("ChatBotDataBase") {
        @Serializable
        data class GroupData(
            var status: Boolean = true,
            val Eq: MutableMap<String, MutableList<String>> = mutableMapOf(),
            val Cn: MutableMap<String, MutableList<String>> = mutableMapOf()
        ) {
            fun match(msg: Message): String? {
                if (msg.toMessageChain().all { it is PlainText || it !is MessageContent }) {
                    if (msg.content in Eq) return Eq[msg.content]!!.random()
                }
                val candi = mutableListOf<String>()
                for (str in msg.toMessageChain().filterIsInstance<PlainText>()) {
                    for (item in Cn) {
                        if (str.content.contains(item.key)) {
                            candi.add(item.key)
                        }
                    }
                }
                if (candi.size > 0) {
                    return Cn[candi.random()]!!.random()
                }
                return null
            }
        }

        private val dataBase: MutableMap<Long, GroupData> by value()
        operator fun get(id: Long): GroupData {
            if (dataBase[id] == null) dataBase[id] = GroupData()
            return dataBase[id]!!
        }
    }

    object Remember : SimpleCommand(Mcbot, "remember", parentPermission = Mcbot.normalPermission) {
        @Handler
        suspend fun CommandSender.onCommand(vararg args: String) {
            if (this is MemberCommandSenderOnMessage) {
                val data = DataBase[group.id]
                if (data.status) {
                    if (args.size == 1) {
                        val msg =
                            GlobalEventChannel.nextEvent<GroupMessageEvent> { it.bot == bot && it.group == group && it.sender.id == this@onCommand.user.id }.message
                        if (msg.all { it !is MessageContent || (it is PlainText || it is Image || it is At || it is Face) }) {
                            push(args[0], listOf(msg.serializeToMiraiCode()), data.Eq)
                        } else if (msg.all { it is ForwardMessage || it !is MessageContent }) {
                            push(args[0],
                                msg[ForwardMessage]!!.nodeList.filter { it -> it.messageChain.all { it !is MessageContent || (it is PlainText || it is Image || it is At || it is Face) } }
                                    .map { it.messageChain.serializeToMiraiCode() },
                                data.Eq
                            )
                        }
                    } else if (args.size == 2 && args[0] == "-i") {
                        val msg =
                            GlobalEventChannel.nextEvent<GroupMessageEvent> { it.bot == bot && it.group == group && it.sender.id == this@onCommand.user.id }.message
                        if (msg.all { it !is MessageContent || (it is PlainText || it is Image || it is At || it is Face) }) {
                            push(args[1], listOf(msg.serializeToMiraiCode()), data.Cn)
                        } else if (msg.all { it is ForwardMessage || it !is MessageContent }) {
                            push(args[1],
                                msg[ForwardMessage]!!.nodeList.filter { it -> it.messageChain.all { it !is MessageContent || (it is PlainText || it is Image || it is At || it is Face) } }
                                    .map { it.messageChain.serializeToMiraiCode() },
                                data.Cn
                            )
                        }
                    } else if (args.size > 1) {
                        if (args[0] == "-i") {
                            push(args[1], args.slice(2 until args.size), data.Cn)
                        } else {
                            push(args[0], args.slice(1 until args.size), data.Eq)
                        }
                    }
                    group.sendMessage("Done.")
                }
            }
        }

        private fun push(key: String, value: List<String>, list: MutableMap<String, MutableList<String>>) {
            if (list.contains(key)) {
                for (elem in value) {
                    if (elem !in list[key]!!) {
                        list[key]!!.add(elem)
                    }
                }
            } else {
                list[key] = value.toMutableList()
            }
        }
    }

    object Forget : SimpleCommand(Mcbot, "forget", parentPermission = Mcbot.adminPermission) {
        @Handler
        suspend fun CommandSender.onCommand(vararg args: String) {
            if (this is MemberCommandSenderOnMessage) {
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
                            for (char in args[0].substring(1)) {
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
                        if (param['i']!!) {
                            if (key in data.Cn) {
                                if (param['k']!!) {
                                    data.Cn.remove(key)
                                } else {
                                    for (i in value) if (i <= data.Cn[key]!!.size && i > 0) data.Cn[key]!!.removeAt(i - 1)
                                    if (data.Cn[key]!!.isEmpty()) data.Cn.remove(key)
                                }
                            } else {
                                group.sendMessage("$key not found.")
                                return
                            }
                        } else {
                            if (key in data.Eq) {
                                if (param['k']!!) {
                                    data.Eq.remove(key)
                                } else {
                                    for (i in value) if (i <= data.Eq[key]!!.size && i > 0) data.Eq[key]!!.removeAt(i - 1)
                                    if (data.Eq[key]!!.isEmpty()) data.Eq.remove(key)
                                }
                            } else {
                                group.sendMessage("$key not found.")
                                return
                            }
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
        //TODO
        @Handler
        suspend fun CommandSender.onCommand() {
            if (this is MemberCommandSenderOnMessage) {
                val data = DataBase[group.id]
                if (data.status) {
                    group.sendMessage("此功能正绝赞咕咕中，敬请期待。")
                }
            }
        }

        //TODO
        @Handler
        suspend fun CommandSender.onCommand(key: String) {
            if (this is MemberCommandSenderOnMessage) {
                val data = DataBase[group.id]
                if (data.status) {
                    group.sendMessage("此功能正绝赞咕咕中，敬请期待。")
                }
            }
        }

        @Handler
        suspend fun CommandSender.onCommand(key: String, index: Int) {
            if (this is MemberCommandSenderOnMessage) {
                val data = DataBase[group.id]
                if (data.status) {
                    if (key in data.Eq) {
                        if (index > 0 && index <= data.Eq[key]!!.size) {
                            group.sendMessage(data.Eq[key]!![index - 1].deserializeMiraiCode(group))
                        } else {
                            group.sendMessage("Index out of range.")
                        }
                    } else {
                        group.sendMessage("$key not found.")
                    }
                }
            }
        }

        @Handler
        suspend fun CommandSender.onCommand(param: String, key: String, index: Int) {
            if (this is MemberCommandSenderOnMessage) {
                val data = DataBase[group.id]
                if (data.status) {
                    if (param == "-i") {
                        if (key in data.Cn) {
                            if (index > 0 && index <= data.Cn[key]!!.size) {
                                group.sendMessage(data.Cn[key]!![index - 1].deserializeMiraiCode(group))
                            } else {
                                group.sendMessage("Index out of range.")
                            }
                        } else {
                            group.sendMessage("$key not found.")
                        }
                    } else {
                        group.sendMessage("Syntax error.")
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
            val reply = DataBase[event.group.id].match(event.message)
            if (reply != null) {
                event.group.sendMessage(reply.deserializeMiraiCode(event.group))
                return true
            }
        }
        return false
    }

    init {
        DataBase.reload()
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
    }
}