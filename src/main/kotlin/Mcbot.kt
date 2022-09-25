package mcbot

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.permission.AbstractPermitteeId
import net.mamoe.mirai.console.permission.PermissionService
import net.mamoe.mirai.console.permission.PermissionService.Companion.permit
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.globalEventChannel

object Mcbot:KotlinPlugin(JvmPluginDescription(id="mcbot.Mcbot",name="Mcbot",version="0.1.0")) {
    val normalPermission by lazy {
        PermissionService.INSTANCE.register(
            permissionId("normalPermission"),
            "普通用户权限"
        )
    }
    val adminPermission by lazy {
        PermissionService.INSTANCE.register(
            permissionId("adminPermission"),
            "管理员权限"
        )
    }

    override fun onEnable() {
        Function.reload()
        Function.Companion.Config.register()
        AbstractPermitteeId.parseFromString("u${Function.owner}").permit(parentPermission)
        AbstractPermitteeId.AnyUser.permit(normalPermission)
        globalEventChannel().subscribeAlways<Event> {
            fun run(vararg funcList: Function) {
                fun MutableMap<String, Deferred<Boolean>>.add(key: Function) {
                    this[key::class.simpleName!!] = async { key(this@subscribeAlways, this@add) }
                }

                val list = mutableMapOf<String, Deferred<Boolean>>()
                for (func in funcList) list.add(func)
            }
            run(
                Recall,
                Hitokoto,
                McServer,
                ChatBot,
                Repeater
            )
        }
        logger.info("Mcbot enabled")
    }

    override fun onDisable() {
        Function.Companion.Config.unregister()
        for (func in Function.ref) func.value.unload()
        logger.info("Mcbot disabled")
    }
}