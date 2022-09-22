package mcbot

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage
import net.mamoe.mirai.console.command.RawCommand
import net.mamoe.mirai.console.command.SimpleCommand
import net.mamoe.mirai.console.permission.AbstractPermitteeId
import net.mamoe.mirai.console.permission.PermissionService.Companion.permit
import net.mamoe.mirai.message.data.MessageChain
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

object McServer {
    val sockets= mutableMapOf<Long,Socket>()
    val senders= mutableMapOf<Long,PrintWriter>()
    fun load(){
        Connect.register()
        Sender.register()
        AbstractPermitteeId.AnyUser.permit(Connect.permission)
        AbstractPermitteeId.AnyUser.permit(Sender.permission)
    }
    fun unload(){
        Connect.unregister()
        Sender.unregister()
        for (item in sockets){
            item.value.close()
        }
    }
}

object Connect:SimpleCommand(Mcbot,"connect"){
    @Handler suspend fun CommandSender.onCommand(ip:String,port:Int){
        if (this is MemberCommandSenderOnMessage){
            if (McServer.sockets[group.id]==null){
                try {
                    val socket=Socket(ip,port)
                    group.sendMessage("Connected.")
                    McServer.sockets[group.id] = socket
                    McServer.senders[group.id]=PrintWriter(socket.getOutputStream())
                    val receiver=BufferedReader(InputStreamReader(socket.getInputStream()))
                    object:Thread(){
                        override fun run() {
                            runBlocking { group.sendMessage("Thread launched.") }
                            var msg=""
                            while (true){
                                msg=receiver.readLine()
                                runBlocking { group.sendMessage(msg) }
                            }
                        }
                    }.start()
                }catch (e:java.lang.Exception){
                    group.sendMessage(e.message?:"Error(s) occurred during connecting to the server.")
                }
            }else{
                group.sendMessage("This group has already connected to a server:"+McServer.sockets[group.id]!!.inetAddress.hostAddress)
            }
        }
    }
}

object Sender:RawCommand(Mcbot,"sd"){
    override suspend fun CommandSender.onCommand(args: MessageChain){
        if (this is MemberCommandSenderOnMessage && McServer.senders[group.id]!=null){
            McServer.senders[group.id]!!.write(this.name+":"+args.contentToString()+"\n")
            McServer.senders[group.id]!!.flush()
        }
    }
}