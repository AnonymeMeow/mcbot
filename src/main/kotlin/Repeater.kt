package mcbot

import mcbot.Mcbot.reload
import kotlinx.serialization.Serializable
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.register
import net.mamoe.mirai.console.command.CommandManager.INSTANCE.unregister
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.permission.AbstractPermitteeId
import net.mamoe.mirai.console.permission.PermissionService.Companion.permit
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode
import net.mamoe.mirai.message.data.*

object Repeater{
    object RepeaterConfig:AutoSavePluginConfig("RepeaterConfig"){
        @Serializable class GroupConfig{
            var state=true
            var threshold=3
            var probability=50
        }
        val config:MutableMap<Long,GroupConfig> by value()
        operator fun get(id:Long)=config[id]
        fun check(id:Long){
            if (id !in config) config[id]=GroupConfig()
        }
        object RepeaterCommand:CompositeCommand(Mcbot,"repeat"){
            @SubCommand suspend fun CommandSender.threshold(){
                if (this is MemberCommandSenderOnMessage){
                    check(group.id)
                    sendMessage("当前触发消息复读的相同消息数量阈值是${config[this.group.id]!!.threshold}")
                }
            }
            @SubCommand suspend fun CommandSender.threshold(threshold:Int){
                if (this is MemberCommandSenderOnMessage){
                    check(group.id)
                    if (threshold < 1){
                        sendMessage("阈值必须大于或等于1")
                        return
                    }
                    config[this.group.id]!!.threshold=threshold
                    sendMessage("阈值已更新为${threshold}")
                }
            }
            @SubCommand suspend fun CommandSender.probability(){
                if (this is MemberCommandSenderOnMessage){
                    check(group.id)
                    sendMessage("当前复读的概率是${config[this.group.id]!!.probability}")
                }
            }
            @SubCommand suspend fun CommandSender.probability(probability:Int){
                if (this is MemberCommandSenderOnMessage){
                    check(group.id)
                    if (probability<0 || probability>100){
                        sendMessage("概率必须在0~100之间")
                        return
                    }
                    config[this.group.id]!!.probability=probability
                    sendMessage("复读概率已更新为${probability}")
                }
            }
            @SubCommand suspend fun CommandSender.on(){
                if (this is MemberCommandSenderOnMessage){
                    check(group.id)
                    if (config[group.id]!!.state){
                        sendMessage("复读已是开启状态")
                    }else{
                        config[group.id]!!.state=true
                        sendMessage("复读已开启")
                    }
                }
            }
            @SubCommand suspend fun CommandSender.off(){
                if (this is MemberCommandSenderOnMessage){
                    check(group.id)
                    if (config[group.id]!!.state){
                        config[group.id]!!.state=false
                        sendMessage("复读已关闭")
                    }else{
                        sendMessage("复读已是关闭状态")
                    }
                }
            }
        }
    }
    class RepeatState{
        var content:String?=null
        var count:Int=0
        operator fun invoke(message:String):Int{
            if (message==content){
                if (count>0)count+=1
            }else{
                content=message
                count=1
            }
            return count
        }
    }
    private val counter= mutableMapOf<Long,RepeatState>()
    fun load(){
        RepeaterConfig.reload()
        RepeaterConfig.RepeaterCommand.register()
        AbstractPermitteeId.AnyUser.permit(RepeaterConfig.RepeaterCommand.permission)
        GlobalEventChannel.parentScope(Mcbot).subscribeAlways<GroupMessageEvent> {
            val res=if (ChatBot.DataBase[group.id].status) ChatBot.DataBase[group.id].match(message) else null
            RepeaterConfig.check(group.id)
            if (RepeaterConfig[group.id]!!.state) {
                if (group.id !in counter) counter[group.id] = RepeatState()
                if (message.all {
                        if (it is MessageContent) {
                            if (it !is PlainText && it !is Image && it !is At && it !is Face && it !is MarketFace) return@all false
                        } else if (it is MessageMetadata) {
                            if (it is QuoteReply || it is ShowImageFlag) return@all false
                        }
                        return@all true
                    }) {
                    if (counter[group.id]!!(message.serializeToMiraiCode()) >= RepeaterConfig[group.id]!!.threshold) {
                        if (!message.content.startsWith(CommandManager.commandPrefix) && res==null) {
                            if ((1..100).random() <= RepeaterConfig[group.id]!!.probability) {
                                group.sendMessage(message)
                                counter[group.id]!!.count = 0
                            }
                        }
                    }
                } else {
                    counter[group.id]!!.content = null
                    counter[group.id]!!.count = 0
                }
            }
            if (res!=null) group.sendMessage(res.deserializeMiraiCode(group))
        }
    }
    fun unload(){
        RepeaterConfig.RepeaterCommand.unregister()
    }
}