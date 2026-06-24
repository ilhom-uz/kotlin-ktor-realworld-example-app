package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.web.rules.AppRule
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests for Option C: GET /api/articles/search?q=<term>
 *
 * Searches article title and body content (case-insensitive) and returns results in
 * the standard article list format. Authentication is required.
 */
class ArticleSearchControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    private fun login(user: String = "search_user") =
        appRule.http.createUser("$user@valid_email.com", user)

    private fun createArticle(title: String, body: String, tags: List<String> = listOf("search")) {
        appRule.http.post<ArticleDTO>("/api/articles", ArticleDTO(Article(title = title, description = "desc", body = body, tagList = tags)))
    }

    @Test
    fun `search matches a term found in the title`() {
        login()
        createArticle(title = "A Guide to Zephyrqx", body = "ordinary body text")

        val response = appRule.http.get<ArticlesDTO>("/api/articles/search?q=zephyrqx")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1, response.body.articles.size)
        assertTrue(response.body.articles.first().title!!.contains("Zephyrqx"))
    }

    @Test
    fun `search matches a term found only in the body`() {
        login()
        createArticle(title = "Completely Unrelated Heading", body = "the body mentions wumpalope uniquely")

        val response = appRule.http.get<ArticlesDTO>("/api/articles/search?q=wumpalope")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1, response.body.articles.size)
        assertTrue(response.body.articles.first().body.contains("wumpalope"))
    }

    @Test
    fun `search is case insensitive`() {
        login()
        createArticle(title = "Taming Functional Dragons", body = "content about dragons")

        // Query in upper-case still matches the lower-case content.
        val response = appRule.http.get<ArticlesDTO>("/api/articles/search?q=DRAGONS")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1, response.body.articles.size)
    }

    @Test
    fun `search returns results in the standard article list format`() {
        login("search_shape")
        createArticle(title = "Shaped Quixotl Article", body = "quixotl in the body too", tags = listOf("a", "b"))

        val response = appRule.http.get<ArticlesDTO>("/api/articles/search?q=quixotl")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(response.body.articles.size, response.body.articlesCount)
        val article = response.body.articles.first()
        assertNotNull(article.slug)
        assertNotNull(article.author)
        assertEquals("search_shape", article.author?.username)
        assertTrue(article.tagList.containsAll(listOf("a", "b")))
    }

    @Test
    fun `search with no matching articles returns an empty list and 200`() {
        login()
        createArticle(title = "Nothing relevant here", body = "still nothing relevant")

        val response = appRule.http.get<ArticlesDTO>("/api/articles/search?q=termthatmatchesnothing")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertTrue(response.body.articles.isEmpty())
        assertEquals(0, response.body.articlesCount)
    }

    @Test
    fun `search respects the limit query parameter`() {
        login()
        createArticle(title = "Paging Match Alpha", body = "matchterm body")
        createArticle(title = "Paging Match Beta", body = "matchterm body")
        createArticle(title = "Paging Match Gamma", body = "matchterm body")

        val response = appRule.http.get<ArticlesDTO>("/api/articles/search?q=matchterm&limit=2")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(2, response.body.articles.size)
    }

    @Test
    fun `search respects the offset query parameter`() {
        login()
        createArticle(title = "Offset Match Alpha", body = "offsetterm body")
        createArticle(title = "Offset Match Beta", body = "offsetterm body")
        createArticle(title = "Offset Match Gamma", body = "offsetterm body")

        // 3 matches total; skipping the first 2 leaves exactly 1.
        val response = appRule.http.get<ArticlesDTO>("/api/articles/search?q=offsetterm&offset=2")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1, response.body.articles.size)
    }

    @Test
    fun `search without authentication returns 401`() {
        // No login on this fresh AppRule -> no Authorization header.
        assertEquals(HttpStatus.SC_UNAUTHORIZED, appRule.http.getStatus("/api/articles/search?q=anything"))
    }

    @Test
    fun `search without a query term returns 422`() {
        login("search_invalid")
        // 'q' omitted entirely -> blank query -> unprocessable entity.
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, appRule.http.getStatus("/api/articles/search"))
    }

    @Test
    fun `search with a blank query term returns 422`() {
        login("search_blank")
        assertEquals(HttpStatus.SC_UNPROCESSABLE_ENTITY, appRule.http.getStatus("/api/articles/search?q="))
    }
}
