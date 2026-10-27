package app.marlboroadvance.mpvex.ui.browser.emby

import app.marlboroadvance.mpvex.preferences.GsyKernelKind
import app.marlboroadvance.mpvex.ui.player.engine.EngineKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbyDirectPlaybackRouteTest {
  @Test
  fun `百度 PCS 域名即使没有 UA 也强制走 GSY Exo`() {
    val route =
      EmbyDirectPlaybackRoute.choose(
        url = "https://d.pcs.baidu.com/file/example",
        requiredHeaders = emptyMap(),
        requestedEngine = EngineKind.MPV,
      )

    assertEquals(EngineKind.GSY, route.engine)
    assertEquals(GsyKernelKind.EXO, route.forcedGsyKernel)
    assertEquals("pan.baidu.com", route.streamHeaders["User-Agent"])
    assertTrue(route.lockGsyKernel)
  }

  @Test
  fun `百度路由保留服务端必需头但删除 Referer`() {
    val route =
      EmbyDirectPlaybackRoute.choose(
        url = "https://d.pcs.baidu.com/file/example",
        requiredHeaders =
          mapOf(
            "User-Agent" to "pan.baidu.com",
            "Cookie" to "session=value",
            "Referer" to "https://drivemby.com",
          ),
        requestedEngine = EngineKind.MPV,
      )

    assertEquals("pan.baidu.com", route.streamHeaders["User-Agent"])
    assertEquals("session=value", route.streamHeaders["Cookie"])
    assertFalse(route.streamHeaders.keys.any { it.equals("Referer", ignoreCase = true) })
  }

  @Test
  fun `百度专用 UA 即使首跳域名变化仍强制走 GSY Exo`() {
    val route =
      EmbyDirectPlaybackRoute.choose(
        url = "https://cdn.example.com/signed/video.mp4",
        requiredHeaders = mapOf("user-agent" to "pan.baidu.com"),
        requestedEngine = EngineKind.MPV,
      )

    assertEquals(EngineKind.GSY, route.engine)
    assertEquals(GsyKernelKind.EXO, route.forcedGsyKernel)
    assertEquals("pan.baidu.com", route.streamHeaders["User-Agent"])
    assertTrue(route.lockGsyKernel)
  }

  @Test
  fun `普通 Emby 地址保持用户选择的播放内核`() {
    val route =
      EmbyDirectPlaybackRoute.choose(
        url = "https://emby.example.com/emby/Videos/1/stream",
        requiredHeaders = emptyMap(),
        requestedEngine = EngineKind.MPV,
      )

    assertEquals(EngineKind.MPV, route.engine)
    assertEquals(null, route.forcedGsyKernel)
    assertFalse(route.lockGsyKernel)
  }
}
