package mcbot

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.permission.AbstractPermitteeId
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.permission.PermissionService.Companion.permit
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.event.globalEventChannel

object Mcbot:KotlinPlugin(JvmPluginDescription(id="mcbot.Mcbot",name="Mcbot",version="0.1.0")){
    val normalPermission by lazy{
        PermissionService.INSTANCE.register(
            permissionId("normalPermission"),
            "普通用户权限"
        )
    }
    val adminPermission by lazy{
        PermissionService.INSTANCE.register(
            permissionId("adminPermission"),
            "管理员权限"
        )
    }
    override fun onEnable() {
        Function.reload()
        Function.Companion.Config.register()
        for (func in Function.ref) func.value.load()
        AbstractPermitteeId.AnyUser.permit(normalPermission)
        globalEventChannel().subscribeAlways<Event>{
            if (this is MessageEvent) {
                val hitokoto=async { Hitokoto(this@subscribeAlways) }
                if (this is GroupMessageEvent) {
                    val recall=async { Recall(this@subscribeAlways) }
                    val chatbot=async { ChatBot(this@subscribeAlways,awaitAll(hitokoto,recall).any()) }
                    val repeater=async { Repeater(this@subscribeAlways, awaitAll(hitokoto,recall,chatbot).any()) }
                }
            }
        }
        logger.info("Mcbot enabled")
    }
    override fun onDisable() {
        Function.Companion.Config.unregister()
        for (func in Function.ref) func.value.unload()
        logger.info("Mcbot disabled")
    }
}