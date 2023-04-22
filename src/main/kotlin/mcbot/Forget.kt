package mcbot

import mcbot.DataBase.buildFromCode
import mcbot.DataBase.getFile
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageContent
import net.mamoe.mirai.message.data.PlainText
import java.io.File

object Forget : SimpleCommand(Mcbot, "forget") {
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
                    suspend fun delFromCode(code: String) = code.buildFromCode(group).forEach {
                        if (it is Image) {
                            val getter=it.getFile()
                            val file=getter(0)
                            if (file.exists()) {
                                val target=getter(-1)
                                if (target.exists()) file.delete()
                                else file.renameTo(target)
                            }
                        }
                    }

                    val list = if (param['i']!!) data.Cn else data.Eq
                    if (key in list) {
                        if (param['k']!!) {
                            list[key]!!.forEach { delFromCode(it) }
                            list.remove(key)
                        } else {
                            for (i in value.sortedDescending()) {
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