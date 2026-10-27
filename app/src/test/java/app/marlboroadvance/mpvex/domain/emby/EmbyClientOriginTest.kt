package app.marlboroadvance.mpvex.domain.emby

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbyClientOriginTest {
  @Test
  fun sameOriginAcceptsEquivalentDefaultHttpsPort() {
    assertTrue(
      EmbyClient.sameOrigin(
        "https://emby.example.com/video/file.mkv",
        "https://emby.example.com:443/emby",
      ),
    )
  }

  @Test
  fun sameOriginRejectsThirdPartyBaiduUrl() {
    assertFalse(
      EmbyClient.sameOrigin(
        "https://d.pcs.baidu.com/file.mkv",
        "https://emby.example.com/emby",
      ),
    )
  }

  @Test
  fun sameOriginRejectsDifferentSchemeOrPort() {
    assertFalse(EmbyClient.sameOrigin("http://emby.example.com/file", "https://emby.example.com"))
    assertFalse(
      EmbyClient.sameOrigin(
        "https://emby.example.com:9443/file",
        "https://emby.example.com:443/emby",
      ),
    )
  }
}
