package mcbot

import mcbot.Mcbot.permissionId
import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.console.permission.AbstractPermitteeId
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.permission.PermissionService.Companion.hasPermission
import net.mamoe.mirai.console.permission.PermissionService.Companion.permit
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.data.recallSource

object Recall {
    val recallperm by lazy {
        PermissionService.INSTANCE.register(
            permissionId("recallpermission"),
            "recallpermission",
            Mcbot.parentPermission
        )
    }

    fun load() {
        AbstractPermitteeId.AnyUser.permit(recallperm)
        GlobalEventChannel.parentScope(Mcbot).subscribeAlways<GroupMessageEvent> {
            val reply: QuoteReply? = message[QuoteReply]
            if (reply != null && message.content == "撤回" && reply.source.fromId == bot.id && toCommandSender().hasPermission(
                    recallperm
                )
            ) {
                reply.recallSource()
            }
        }
    }

    fun unload() {}
}