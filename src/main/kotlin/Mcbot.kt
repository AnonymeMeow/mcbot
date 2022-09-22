package mcbot

import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin

object Mcbot:KotlinPlugin(JvmPluginDescription(id="mcbot.Mcbot",name="Mcbot",version="0.1.0")){
    override fun onEnable() {
        Repeater.load()
        ChatBot.load()
        McServer.load()
        Recall.load()
        logger.info("Mcbot enabled")
    }
    override fun onDisable() {
        Repeater.unload()
        ChatBot.unload()
        McServer.unload()
        Recall.unload()
        logger.info("Mcbot disabled")
    }
}