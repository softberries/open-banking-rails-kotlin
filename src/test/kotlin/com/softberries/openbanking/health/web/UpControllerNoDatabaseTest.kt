package com.softberries.openbanking.health.web

import com.softberries.openbanking.config.SecurityConfiguration
import com.softberries.openbanking.web.ApplicationRoutes
import com.softberries.openbanking.web.WebSecurityConventions
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import javax.sql.DataSource

/** `/up` must answer without any database (liveness, U-522): this slice has no DataSource at all. */
@WebMvcTest(UpController::class)
@Import(SecurityConfiguration::class, WebSecurityConventions::class, ApplicationRoutes::class)
class UpControllerNoDatabaseTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `answers 200 without a DataSource, without a User-Agent and without a session`() {
        context.getBeanNamesForType(DataSource::class.java).size shouldBe 0

        val result =
            mvc
                .get("/up")
                .andExpect {
                    status { isOk() }
                    content { bytes(UpController.BODY) }
                    header { doesNotExist("Set-Cookie") }
                }.andReturn()

        result.request.getSession(false) shouldBe null
    }

    @Test
    fun `paths without a route are not blocked by security and keep their 404`() {
        mvc.get("/admin/does-not-exist").andExpect { status { isNotFound() } }
    }
}
