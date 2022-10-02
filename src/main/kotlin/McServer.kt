package mcbot

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mcbot.Mcbot.reload
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

@Suppress("unused")
object McServer:Function(true) {
    object McServerIP : AutoSavePluginConfig("McServerIP") {
        val IP: MutableMap<Long, Pair<String, Int>> by value()
        operator fun get(id: Long) = IP[id]
        operator fun set(id: Long, ip: Pair<String, Int>) {
            IP[id] = ip
        }
    }

    class GroupConnection(ip: Pair<String, Int>, val group: Group) {
        private val socket: Socket
        private var bufferedReader: BufferedReader
        var printWriter: PrintWriter
        private var thread: Thread

        init {
            socket = Socket(ip.first, ip.second)
            socket.keepAlive = true
            bufferedReader = BufferedReader(InputStreamReader(socket.getInputStream()))
            printWriter = PrintWriter(socket.getOutputStream())
            thread = object : Thread() {
                override fun run() {
                    var receive: String
                    synchronized(printWriter) {
                        printWriter.println("Connect")
                        printWriter.println(socket.localAddress.hostAddress)
                    }
                    receive = bufferedReader.readLine()
                    if (receive != "Accepted") {
                        receive = bufferedReader.readLine()
                        this@GroupConnection.close(true)
                        Mcbot.launch { group.sendMessage("Failed to connect to server ${ip.first}:${ip.second}, error:${receive}.") }
                        throw Throwable(receive)
                    }
                    while (true) {
                        receive = bufferedReader.readLine()
                        when (receive) {
                            "MessageEvent" -> {
                                receive = bufferedReader.readLine()
                                Mcbot.launch { group.sendMessage(receive) }
                            }
                            "ConnectTerminate" -> {
                                receive = bufferedReader.readLine()
                                when (receive) {
                                    "Request" -> {
                                        synchronized(printWriter) {
                                            printWriter.println("ConnectTerminate")
                                            printWriter.println("Receive")
                                        }
                                    }
                                    "Receive" -> {}
                                    else -> continue
                                }
                                this@GroupConnection.close(true)
                                return
                            }
                        }
                    }
                }
            }
            thread.start()
        }

        fun close(active: Boolean = false) {
            if (active) {
                synchronized(printWriter) {
                    printWriter.println("ConnectTerminate")
                    printWriter.println("Request")
                }
            }
            printWriter.close()
            bufferedReader.close()
            socket.close()
            connections.remove(group.id)
            thread.join()
        }
    }

    val connections = mutableMapOf<Long, GroupConnection>()

    object ConnectionManager : CompositeCommand(Mcbot, "connect", parentPermission = Mcbot.adminPermission) {
        @SubCommand
        suspend fun CommandSender.setAndConnect(ip: String, port: Int) {
            if (this is MemberCommandSenderOnMessage) {
                if (group.id in connections) {
                    val ipAddress = McServerIP[group.id]!!
                    sendMessage("This group has already connected to server ${ipAddress.first}:${ipAddress.second}, terminate the connection if you want to change the server.")
                } else {
                    McServerIP[group.id] = Pair(ip, port)
                    resume()
                    sendMessage("OK")
                }
            }
        }

        @SubCommand
        suspend fun CommandSender.stop() {
            if (this is MemberCommandSenderOnMessage && group.id in connections) {
                withContext(Dispatchers.IO) {
                    try {
                        connections[group.id]!!.close()
                        sendMessage("OK")
                    } catch (e: Exception) {
                        sendMessage(e.message ?: "Internal error.")
                    }
                }
            }
        }

        @SubCommand
        suspend fun CommandSender.resume() {
            if (this is MemberCommandSenderOnMessage && group.id in McServerIP.IP && group.id !in connections) {
                withContext(Dispatchers.IO) {
                    try {
                        connections[group.id] = GroupConnection(McServerIP[group.id]!!, group)
                        sendMessage("OK")
                    } catch (e: Exception) {
                        sendMessage(e.message ?: "Internal error.")
                    }
                }
            }
        }

        @SubCommand
        suspend fun CommandSender.status() {
            if (this is MemberCommandSenderOnMessage) {
                if (group.id in McServerIP.IP) {
                    val ipAddress = McServerIP[group.id]!!
                    if (group.id in connections) {
                        sendMessage("This group has connected to server ${ipAddress.first}:${ipAddress.second}.")
                    } else {
                        sendMessage("This group has been bound to server ${ipAddress.first}:${ipAddress.second}, send /connect resume to connect.")
                    }
                } else {
                    sendMessage("This group hasn't bound to any server.")
                }
            }
        }
    }

    init {
        McServerIP.reload()
        if (status) ConnectionManager.register()
    }

    override suspend fun invoke(event: Event, list: MutableMap<String, Deferred<Boolean>>): Boolean {
        if (status &&
            event is GroupMessageEvent &&
            event.group.id in connections &&
            (event.message.content.startsWith(":") || event.message.content.startsWith("："))
        ) {
            val sender = connections[event.group.id]!!.printWriter
            synchronized(sender) {
                sender.println("MessageEvent")
                sender.println("|" + (event.sender.nameCardOrNick + ":" + event.message.content).replace("\n", "\n|"))
                sender.println("End")
            }
            return true
        }
        return false
    }

    override fun unload() {
        if (status) {
            ConnectionManager.unregister()
            Mcbot.launch {
                connections.forEach {
                    withContext(Dispatchers.IO) {
                        try {
                            it.value.close()
                        } catch (e: Exception) {
                            Mcbot.logger.info(e.message ?: "Failed to terminate connection of group ${it.key}.")
                        }
                    }
                }
            }
        }
    }

    override fun enable() {
        super.enable()
        ConnectionManager.register()
    }

    override fun disable() {
        super.disable()
        ConnectionManager.unregister()
        Mcbot.launch {
            connections.forEach {
                withContext(Dispatchers.IO) {
                    try {
                        it.value.close()
                    } catch (e: Exception) {
                        Mcbot.logger.info(e.message ?: "Failed to terminate connection of group ${it.key}.")
                    }
                }
            }
        }
    }
}