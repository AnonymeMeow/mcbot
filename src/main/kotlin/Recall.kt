package mcbot

import kotlinx.coroutines.Deferred
import mcbot.Mcbot.permissionId
import net.mamoe.mirai.console.command.CommandSender.Companion.toCommandSender
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.permission.PermissionService.Companion.hasPermission
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.QuoteReply
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.message.data.recallSource

object Recall:Function(true) {
    private val recallPermission by lazy {
        PermissionService.INSTANCE.register(
            permissionId("recallPermission"),
            "Permission for recalling a bot message.",
            Mcbot.adminPermission
        )
    }

    override suspend operator fun invoke(event: Event, list: MutableMap<String, Deferred<Boolean>>): Boolean {
        if (status && event is GroupMessageEvent) {
            val reply = event.message[QuoteReply]
            if (event.toCommandSender().hasPermission(recallPermission) &&
                reply != null &&
                event.message.content == "撤回" &&
                reply.source.fromId == event.bot.id
            ) {
                reply.recallSource()
                return true
            }
        }
        return false
    }
}