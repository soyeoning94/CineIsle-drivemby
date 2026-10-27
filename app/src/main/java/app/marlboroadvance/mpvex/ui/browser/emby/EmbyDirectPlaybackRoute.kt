package app.marlboroadvance.mpvex.ui.browser.emby

import app.marlboroadvance.mpvex.preferences.GsyKernelKind
import app.marlboroadvance.mpvex.ui.player.engine.EngineKind
import java.net.URI

/**
 * 百度 PCS 直链不能交给 mpv 的 native HTTPS 路径：在 Android 17 / HyperOS 上会触发
 * fdsan，表现为播放器一直停在“正在加载”或进程被系统重启。
 *
 * 这类地址统一交给 GSY 的 ExoPlayer（Java/Media3 网络栈），同时只保留服务端要求的
 * User-Agent，绝不补 Referer（百度会以 31326 拒绝带 Referer 的请求）。
 */
internal object EmbyDirectPlaybackRoute {
  data class Route(
    val engine: EngineKind?,
    val forcedGsyKernel: GsyKernelKind? = null,
    val lockGsyKernel: Boolean = false,
    val streamHeaders: Map<String, String> = emptyMap(),
  )

  fun choose(
    url: String,
    requiredHeaders: Map<String, String>,
    requestedEngine: EngineKind?,
  ): Route {
    val userAgent =
      requiredHeaders.entries
        .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
        ?.value
    val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    val isBaiduPcs =
      userAgent.equals(BAIDU_USER_AGENT, ignoreCase = true) ||
        host == "d.pcs.baidu.com" ||
        host == "baidupcs.com" ||
        host.endsWith(".baidupcs.com")

    if (!isBaiduPcs) {
      return Route(engine = requestedEngine, streamHeaders = requiredHeaders)
    }

    val safeHeaders = linkedMapOf("User-Agent" to BAIDU_USER_AGENT)
    requiredHeaders.forEach { (key, value) ->
      if (
        !key.equals("User-Agent", ignoreCase = true) &&
          !key.equals("Referer", ignoreCase = true) &&
          !key.equals("Range", ignoreCase = true) &&
          !key.equals("Accept-Encoding", ignoreCase = true)
      ) {
        safeHeaders[key] = value
      }
    }
    return Route(
      engine = EngineKind.GSY,
      forcedGsyKernel = GsyKernelKind.EXO,
      lockGsyKernel = true,
      streamHeaders = safeHeaders,
    )
  }

  private const val BAIDU_USER_AGENT = "pan.baidu.com"
}
