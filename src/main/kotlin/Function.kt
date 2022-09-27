package mcbot

import kotlinx.coroutines.Deferred
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.permission.AbstractPermitteeId
import net.mamoe.mirai.console.permission.PermissionService.Companion.cancel
import net.mamoe.mirai.console.permission.PermissionService.Companion.permit
import net.mamoe.mirai.event.Event

@Suppress("LeakingThis", "unused")
abstract class Function(var status: Boolean) {
    init {
        val name = this::class.simpleName!!
        if (name !in config) {
            config[name] = status
        } else {
            status = config[name]!!
        }
        ref[name] = this
    }

    open val description = "Mcbot function."

    open fun unload() {}
    open fun enable() {
        status = true
        config[this::class.simpleName!!] = true
    }

    open fun disable() {
        status = false
        config[this::class.simpleName!!] = false
    }

    abstract suspend operator fun invoke(event: Event, list: MutableMap<String, Deferred<Boolean>>): Boolean

    companion object : AutoSavePluginConfig("McbotConfig") {
        var owner: Long by value()
        val config: MutableMap<String, Boolean> by value()
        val ref = mutableMapOf<String, Function>()

        object Config : SimpleCommand(Mcbot, "mcbot") {
            @Handler
            suspend fun CommandSender.onCommand(vararg args: String) {
                config.run { forEach { (key, _) -> if (key !in ref) remove(key) } }
                when (args.size) {
                    0 -> {
                        sendMessage("Loaded functions:\n" + config.map { "${it.key}:${it.value}" }.joinToString("\n"))
                    }
                    1 -> {
                        val name = args[0]
                        if (name in config) {
                            sendMessage("${name}:\n" + ref[name]!!.description + "\nstatus:${config[name]!!}")
                        } else {
                            sendMessage("$name not found.")
                        }
                    }
                    2 -> {
                        val name = args[0]
                        if (name in config) {
                            when (args[1]) {
                                "on" -> {
                                    if (ref[name]!!.status) {
                                        sendMessage("$name is already enabled.")
                                    } else {
                                        ref[name]!!.enable()
                                        sendMessage("$name enabled.")
                                    }
                                }
                                "off" -> {
                                    if (ref[name]!!.status) {
                                        ref[name]!!.disable()
                                        sendMessage("$name disabled.")
                                    } else {
                                        sendMessage("$name is already disabled.")
                                    }
                                }
                                else -> sendMessage("Unknown command:${args[1]}")
                            }
                        } else {
                            sendMessage("$name not found.")
                        }
                    }
                    3 -> {
                        if (args[0] == "su") {
                            when (args[1]) {
                                "add" -> {
                                    try {
                                        val permittee = args[2].removePrefix("@").toLong()
                                        AbstractPermitteeId.parseFromString("u$permittee").permit(Mcbot.adminPermission)
                                        sendMessage("Done.")
                                    } catch (e: Exception) {
                                        sendMessage(e.message ?: "Internal error.")
                                    }
                                }
                                "cancel" -> {
                                    try {
                                        val permittee = args[2].removePrefix("@").toLong()
                                        AbstractPermitteeId.parseFromString("u$permittee")
                                            .cancel(Mcbot.adminPermission, true)
                                        sendMessage("Done.")
                                    } catch (e: Exception) {
                                        sendMessage(e.message ?: "Internal error.")
                                    }
                                }
                                else -> sendMessage("Unknown command ${args[1]}.")
                            }
                        } else {
                            sendMessage("Syntax error.")
                        }
                    }
                    else -> sendMessage("Too many args.")
                }
            }
        }
    }
}