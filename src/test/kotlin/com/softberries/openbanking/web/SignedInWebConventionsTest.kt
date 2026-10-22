package com.softberries.openbanking.web

import com.softberries.openbanking.support.IntegrationTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpMethod
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request

/** The real filter chain (servlet filters + Spring Security) with an authenticated principal, as a signed-in user has. */
@AutoConfigureMockMvc
class SignedInWebConventionsTest : IntegrationTest() {
    @Autowired
    private lateinit var mvc: MockMvc

    @Test
    fun `GET admin is the 301 to analytics for a signed-in user (oracle GET_admin main session)`() {
        mvc.get("/admin") { with(user("main")) }.andExpect {
            status { isEqualTo(301) }
            header { string("Location", "http://localhost/admin/analytics") }
        }
    }

    @ParameterizedTest(name = "{0}")
    @UnportedRoutesSource
    fun `unported routes answer 404 for a signed-in user (X-16)`(
        method: String,
        path: String,
    ) {
        mvc.request(HttpMethod.valueOf(method), path) { with(user("main")) }.andExpect { status { isNotFound() } }
    }
}
