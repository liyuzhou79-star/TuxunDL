package com.tuxundl.android.tortest

/**
 * Plain-language view of the Tor log: a small local rule table
 * that rewrites the lines we know how to explain (bootstrap
 * stages, listeners, common warnings) into everyday words, in
 * order. Purely offline string matching — nothing is sent
 * anywhere, and nothing is invented: a line no rule recognises
 * is dropped from this view (warnings/errors collapse to one
 * generic note), never paraphrased into a guess. The app's own
 * log lines are already written in plain language and pass
 * through untouched.
 */
object TorLogTranslate {

    private val torPrefix = Regex("""^\w{3} \d+ [\d:.]+ """)
    private val severity = Regex("""^\[(notice|warn|err)\] (.*)$""")
    private val bootstrapped = Regex("""Bootstrapped (\d+)% \(([^)]+)\): (.*)""")
    private val stuckAt = Regex("""Problem bootstrapping\. Stuck at (\d+)%""")
    private val listenerPort = Regex("""127\.0\.0\.1:(\d+)""")
    private val torVersion = Regex("""Tor (\S+) running on""")

    /** Bootstrap stage tag → what it means in everyday words. */
    private fun stageText(tag: String): String? = when (tag) {
        "starting" -> "Tor 正在启动"
        "conn" -> "正在连接第一个中继节点"
        "conn_done" -> "已连上第一个中继节点"
        "handshake", "handshaking" -> "正在和中继节点握手、协商加密"
        "handshaking_done" -> "和第一个节点的加密握手完成了"
        "onehop_create" -> "正在建立一条加密通道，去取节点名单"
        "requesting_status" -> "正在向目录服务器要全网节点名单"
        "loading_status" -> "正在下载全网节点名单"
        "loading_keys" -> "正在加载校验节点名单用的证书"
        "requesting_descriptors" -> "正在查询中继节点的详细信息"
        "loading_descriptors" -> "正在下载中继节点信息"
        "enough_dirinfo" -> "节点名单已经够用了，可以开始组装线路"
        "ap_conn" -> "正在组装三跳线路"
        "ap_handshake" -> "正在和线路的第一个节点完成握手"
        "ap_handshake_done" -> "线路握手完成，马上就通"
        "done" -> "已连通！流量现在经过 Tor 网络"
        else -> null
    }

    fun translate(lines: List<String>): List<String> {
        // Bootstrap stages repeat a lot while the percent climbs;
        // keep one entry per stage run, at its latest percent.
        var pendingStage: Pair<String, String>? = null // tag -> text
        val out = mutableListOf<String>()

        fun flushStage() {
            pendingStage?.let { out += it.second }
            pendingStage = null
        }

        fun emit(text: String) {
            flushStage()
            if (out.lastOrNull() != text) out += text
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            // The app's own lines are already plain language.
            if (line.any { it.code in 0x4E00..0x9FFF }) {
                emit(line)
                continue
            }
            val body = torPrefix.replace(line, "")
            val match = severity.find(body)
            val level = match?.groupValues?.get(1)
            val message = match?.groupValues?.get(2) ?: body

            val boot = bootstrapped.find(message)
            if (boot != null) {
                val percent = boot.groupValues[1].toInt()
                val tag = boot.groupValues[2]
                val stage = stageText(tag)
                val text = when {
                    tag == "done" -> stage!!
                    stage != null -> "$stage（$percent%）"
                    else -> "连接进度 $percent%"
                }
                val previous = pendingStage
                if (previous != null && previous.first == tag) {
                    pendingStage = tag to text
                } else {
                    flushStage()
                    pendingStage = tag to text
                }
                continue
            }

            translateLine(level, message)?.let { emit(it) }
        }
        flushStage()
        return out
    }

    /** One non-bootstrap line → its plain version, or null to skip. */
    private fun translateLine(level: String?, message: String): String? {
        stuckAt.find(message)?.let { stuck ->
            val percent = stuck.groupValues[1]
            val reason = when {
                message.contains("Connection refused") ->
                    "对方拒绝了连接，这座桥可能已被封锁或下线了"

                message.contains("timed out", true) || message.contains("Timeout") ->
                    "连接超时，对方一直没有回应"

                message.contains("Network unreachable") || message.contains("No route") ->
                    "当前网络到不了对方那里"

                message.contains("handshake", true) ->
                    "握手没谈成，可能是桥不对，或者连接被干扰了"

                else -> "卡住的原因看不出来，Tor 还在重试"
            }
            return "连接卡在 $percent% 没有进展：$reason"
        }

        torVersion.find(message)?.let {
            return "Tor 程序已启动（版本 ${it.groupValues[1]}）"
        }

        return when {
            message.contains("Opened Socks listener") -> {
                val port = listenerPort.find(message)?.groupValues?.get(1)
                if (port != null) "本地代理口（$port）已打开" else "本地代理口已打开"
            }

            message.contains("Opened HTTP tunnel listener") ->
                "浏览器用的 Tor 通道口已打开"

            message.contains("Opened Control listener") ->
                "控制口已打开，App 靠它指挥 Tor"

            message.contains("successfully opened a circuit") ->
                "第一条三跳线路已经建好，可以用了"

            message.contains("Delaying directory fetches") ->
                "网桥还在准备中，先等一下再获取节点名单"

            message.contains("directory information is no longer up-to-date") ->
                "节点名单过期了，正在重新获取"

            message.contains("I learned some more directory information") ->
                "又拿到了一些节点信息，还不够组装线路，继续获取中"

            message.contains("Your Guard") && message.contains("down") ->
                "入口节点暂时失联，正在换一个"

            message.contains("Catching signal TERM") ->
                "收到关闭指令，Tor 正常退出"

            level == "warn" -> when {
                message.contains("connections have failed") ->
                    "已经有好几次连接失败了，Tor 还在重试"

                message.contains("unable to connect", true) ->
                    "连不上对方：对方没回应，或者拒绝了连接"

                message.contains("SOCKS") ->
                    "通过网桥的这条连接失败了"

                message.contains("handshake", true) ->
                    "加密握手没谈成，可能是桥不对，或者连接被干扰了"

                else -> "⚠ 有一条警告，Tor 还在继续尝试（细节看原始日志）"
            }

            level == "err" -> "⛔ 出了一条错误（细节看原始日志）"

            else -> null
        }
    }
}
