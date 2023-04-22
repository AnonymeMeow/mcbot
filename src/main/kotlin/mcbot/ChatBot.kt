package mcbot

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.MemberCommandSenderOnMessage

object ChatBot : CompositeCommand(Mcbot, "chatbot") {
    @SubCommand
    suspend fun CommandSender.on() {
        if (this is MemberCommandSenderOnMessage) {
            val data = DataBase[group.id]
            if (data.status) {
                group.sendMessage("自动回复已经是开启状态")
            } else {
                data.status = true
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