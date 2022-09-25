# `mcbot`, a [`mirai`](https://github.com/mamoe/mirai) plugin for QQ

`mcbot`是一个基于[`mirai`](https://github.com/mamoe/mirai)的QQ插件，部分功能实现参考了[yqbot](https://github.com/Yongqi-Zhuo/yqbot)和[彩虹桥](https://github.com/niltok/tesseract)的实现。

目前具有的功能：
- 一言（[Hitokoto](#[一言](src/main/kotlin/Hitokoto.kt))）
- 撤回（[Recall](#[撤回](src/main/kotlin/Recall.kt))）
- 自动回复（[ChatBot](#[自动回复](src/main/kotlin/ChatBot.kt))）
- 复读（[Repeater](#[复读](src/main/kotlin/Repeater.kt))）
- 与Minecraft服务器通讯（未完成）（[McServer](#[与Minecraft服务器通讯（未完成）](src/main/kotlin/McServer.kt))）

## 基本功能

所有功能均在[Mcbot](src/main/kotlin/Mcbot.kt)的run函数中，在bot上线时由BotOnlineEvent初始化。各功能之间是解耦的，可以简单地将其从run函数中移除以达到不加载此功能的目的。

[Function](src/main/kotlin/Function.kt)是所有功能的父类，实现了自动加载以及全局管理功能。其中伴生对象的成员变量owner是bot主人的QQ号，请在${miraiFolder}/config/mcbot.Mcbot/McbotConfig.yml中加入一行：

owner: ${your QQ number}

否则插件会将QQ号为0的用户设为主人，可能会导致一些未定义行为。通过/mcbot命令可以对mcbot进行功能的全局管理和管理员的设置，用法如下：

- `/mcbot`:列出所有已加载的功能和其状态
- `/mcbot <functionName>`:列出指定功能和其说明以及其状态
- `/mcbot <functionName> <on/off>`:全局开启/关闭指定功能
- `/mcbot su <add/cancel> <QQ number/[mirai:at:target]>`:将指定用户设为bot管理员或撤销其管理员，可以通过QQ号或at操作确定被执行用户

## [一言](src/main/kotlin/Hitokoto.kt)

在任意地方（群聊，临时会话，好友会话等）发送“一言”或指定方面的一言，如“动画一言”，bot会回复一句话，和它的出处和作者（如果有的话）。一言是基于一个api实现的（[hitokoto](https://hitokoto.cn)），具体指定方面可以是以下方面：
- 动画
- 漫画
- 游戏
- 文学
- 原创
- 网络
- 影视
- 诗词
- 网易云
- 哲学
- 抖机灵
- 其他

## [撤回](src/main/kotlin/Recall.kt)

可以让bot在群聊中撤回一条指定消息，此操作需要管理员权限。对要撤回的消息回复“撤回”即可，撤回两分钟以前的消息需要bot是群管理员。

## [自动回复](src/main/kotlin/ChatBot.kt)

（此功能待完善）

在群聊中可由关键词触发bot随机回复，目前支持全文匹配关键词和文本中匹配关键词，关键词目前只能是纯文本，回复目前支持<a id = "ReplyType">纯文本，图片，at某人以及QQ原生表情</a>。若存在多个文本中匹配则随机选择一个关键词进行回复。可以通过`/chatbot <on/off>`开启/关闭本群自动回复功能。对关键词和回复的操作如下：

- `/remember [-i] <keyword> [reply...]`:记住指定关键词的回复，其中-i选项表示关键词为文本中匹配，若回复为空，则bot会等待命令发起人的下一消息，若其满足回复[消息类型](#ReplyType)要求则将其放入回复列表，若为转发消息则将其内满足回复[消息类型](#ReplyType)要求的回复全放入回复列表，若前者均不满足则跳过此次命令，直接到完成阶段（Done.）。注：目前未实现图片保存功能，自动回复内的图片生存周期不可预测，随时可能丢失。
- `/forget [-ik] <keyword> [index...]`:忘记某一关键词或指定回复，其中-i选项代表关键词为文本中匹配关键词，-k选项表示忘记该关键词的所有回复。该指令用index来指代回复，因为可能会存在图片等无法用纯文本指代的回复。
- `/lookup [-i] <keyword> <index>`:查询指定关键词的第index个回复，其中-i选项代表关键词为文本中匹配关键词。
- 更多`/lookup`功能:将来会加入更多查询功能，如查询所有匹配关键词并分页显示，查询匹配关键词下的所有回复等。

## [复读](src/main/kotlin/Repeater.kt)

在群聊中相同消息数量超过阈值后有一定概率触发复读。每个群可分别设置复读的开关，阈值和概率：

- `/repeat <on/off>`:开启/关闭本群复读
- `/repeat threshold [threshold]`:查询或设置本群复读阈值
- `/repeat probability [probability]`:查询或设置本群复读概率

## [与Minecraft服务器通讯（未完成）](src/main/kotlin/McServer.kt)

本bot的本意，与[mcbot-mcside](https://github.com/Zhu-Aemon/mcbot-mcside)一起使用，目前只完成了连接到服务器以及互相转发消息的功能，socket协议以及转发事件等功能正在筹划中。此功能默认处于关闭状态。