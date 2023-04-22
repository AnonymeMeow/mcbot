package mcbot

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.permission.AbstractPermitteeId
import net.mamoe.mirai.console.permission.PermissionService.Companion.permit
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.*

object Mcbot : KotlinPlugin(JvmPluginDescription(id = "mcbot.Mcbot", name = "Mcbot", version = "0.1.0")) {
    override fun onEnable() {
        DataBase.reload()
        Remember.register()
        Forget.register()
        LookUp.register()
        ChatBot.register()
        AbstractPermitteeId.AnyUser.permit(parentPermission)
        globalEventChannel().subscribeAlways<GroupMessageEvent> {
            if (DataBase[group.id].status &&
                !message.content.startsWith(CommandManager.commandPrefix)
            ) {
                val quote = message[QuoteReply]?.source
                val last = LookUp.searchResult[group.id]?.lastReply
                if (quote != null &&
                    last != null &&
                    quote.ids.contentEquals(last.ids) &&
                    quote.fromId == last.fromId
                ) {
                    LookUp.searchResult[group.id]?.handle(this)
                } else {
                    val reply = DataBase[group.id].match(this)
                    if (reply != null) {
                        group.sendMessage(reply)
                    }
                }
            }
        }
        logger.info("Mcbot enabled")
    }

    override fun onDisable() {
        Remember.unregister()
        Forget.unregister()
        LookUp.unregister()
        ChatBot.unregister()
        logger.info("Mcbot disabled")
    }
}