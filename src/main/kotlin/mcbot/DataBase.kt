package mcbot

import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.isUploaded
import net.mamoe.mirai.utils.ExternalResource.Companion.uploadAsImage
import java.io.File

object DataBase : AutoSavePluginConfig("ChatBotDataBase") {
    @Serializable
    data class GroupData(
        var status: Boolean = true,
        var messagePerPage: Int = 20,
        val Eq: MutableMap<String, MutableList<String>> = mutableMapOf(),
        val Cn: MutableMap<String, MutableList<String>> = mutableMapOf()
    ) {
        suspend fun match(event: GroupMessageEvent): Message? {
            val msg = event.message
            if (msg.toMessageChain().all { it is PlainText || it !is MessageContent } && msg.content in Eq)
                return Eq[msg.content]!!.random().buildFromCode(event.group)
            val candi = mutableListOf<String>()
            for (str in msg.toMessageChain().filterIsInstance<PlainText>()) {
                for (item in Cn) {
                    if (str.content.contains(item.key)) {
                        candi.add(item.key)
                    }
                }
            }
            if (candi.size > 0) return Cn[candi.random()]!!.random().buildFromCode(event.group)
            return null
        }
    }

    fun Image.getFile():(Int)->File {
        val id = imageId.substringBefore('.')
        val ext = imageId.substringAfter('.')
        val name = Mcbot.dataFolder.list { _, name -> name.startsWith(id) && name.endsWith(".$ext") }?.firstOrNull()
            ?: return { File(Mcbot.dataFolder,"$id.$ext") }
        return {
            if (it==0) File(Mcbot.dataFolder,name)
            else {
                val refCountStr = name.removeSurrounding(id, ".$ext")
                val refCount = (if (refCountStr.isEmpty()) 0 else refCountStr.toInt()) + it
                if (refCount>0) File(Mcbot.dataFolder,"$id$refCount.$ext")
                else File(Mcbot.dataFolder,"$id.$ext")
            }
        }
    }

    suspend inline fun String.buildFromCode(group: Group) = deserializeMiraiCode(group).map {
        if (it is Image && !it.isUploaded(group.bot)) {
            val file=it.getFile()(0)
            if (file.exists()) file.uploadAsImage(group)
            else PlainText("[图片]")
        } else it
    }.toMessageChain()

    private val dataBase: MutableMap<Long, GroupData> by value()
    operator fun get(id: Long): GroupData {
        if (dataBase[id] == null) dataBase[id] = GroupData()
        return dataBase[id]!!
    }
}