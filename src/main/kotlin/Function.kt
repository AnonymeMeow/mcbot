package mcbot

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value

abstract class Function(var status:Boolean) {
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

    open fun load() {}
    open fun unload() {}
    open fun enable() {
        status = true
        config[this::class.simpleName!!] = true
    }

    open fun disable() {
        status = false
        config[this::class.simpleName!!] = false
    }

    companion object : AutoSavePluginConfig("McbotConfig") {
        val config: MutableMap<String, Boolean> by value()
        val ref = mutableMapOf<String, Function>()

        object Config : SimpleCommand(Mcbot, "mcbot") {
            @Handler
            suspend fun CommandSender.onCommand(vararg args: String) {
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
                    else -> sendMessage("Too many args.")
                }
            }
        }
    }
}