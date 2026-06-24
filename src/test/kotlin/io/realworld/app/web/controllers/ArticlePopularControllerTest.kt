package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.web.rules.AppRule
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests for Option A: GET /api/articles/feed/popular
 *
 * Returns articles sorted by number of favorites (most favorited first), with
 * limit/offset paging. Authentication is required.
 */
class ArticlePopularControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    private fun loginAs(user: String) =
        appRule.http.createUser("$user@valid_email.com", user)

    private fun createArticle(title: String): String {
        val article = Article(title = title, description = "desc", body = "body", tagList = listOf("popular"))
        return appRule.http.post<ArticleDTO>("/api/articles", ArticleDTO(article)).body.article?.slug
            ?: error("article was not created")
    }

    private fun favorite(slug: String) {
        appRule.http.post<ArticleDTO>("/api/articles/$slug/favorite", "")
    }

    private fun popular(query: String = ""): ArticlesDTO =
        appRule.http.get<ArticlesDTO>("/api/articles/feed/popular$query").body

    @Test
    fun `popular orders the more favorited article before the less favorited one`() {
        loginAs("popular_author")
        val popularSlug = createArticle("Popular Most Favorited Alpha")
        val lessPopularSlug = createArticle("Popular Less Favorited Beta")

        loginAs("popular_fan1"); favorite(popularSlug)
        loginAs("popular_fan2"); favorite(popularSlug)

        val articles = popular("?limit=100").articles
        val slugs = articles.map { it.slug }
        assertTrue("more favorited article must come first", slugs.indexOf(popularSlug) < slugs.indexOf(lessPopularSlug))
        assertEquals(2L, articles.first { it.slug == popularSlug }.favoritesCount)
    }

    @Test
    fun `popular orders three articles by descending favorite count`() {
        loginAs("popular_three_author")
        val two = createArticle("Three Tier Two Favorites")
        val one = createArticle("Three Tier One Favorite")
        val zero = createArticle("Three Tier Zero Favorites")

        loginAs("popular_three_fan1"); favorite(two); favorite(one)
        loginAs("popular_three_fan2"); favorite(two)

        val slugs = popular("?limit=100").articles.map { it.slug }
        assertTrue(slugs.indexOf(two) < slugs.indexOf(one))
        assertTrue(slugs.indexOf(one) < slugs.indexOf(zero))
    }

    @Test
    fun `popular includes articles that have no favorites`() {
        loginAs("popular_zero_author")
        val unfavored = createArticle("Popular Never Favorited")

        val article = popular("?limit=100").articles.first { it.slug == unfavored }
        assertEquals(0L, article.favoritesCount)
    }

    @Test
    fun `unfavoriting lowers the favorite count and reorders the list`() {
        loginAs("popular_unfav_author")
        val first = createArticle("Unfav Reorder First")
        val second = createArticle("Unfav Reorder Second")

        // Both start with one favorite from different fans.
        loginAs("popular_unfav_fan1"); favorite(first)
        loginAs("popular_unfav_fan2")
        favorite(second)
        // fan2 also favorites `first` (first now has 2), then removes it (back to 1, tie with second).
        favorite(first)
        appRule.http.delete("/api/articles/$first/favorite")

        val firstArticle = popular("?limit=100").articles.first { it.slug == first }
        assertEquals(1L, firstArticle.favoritesCount)
    }

    @Test
    fun `popular respects the limit query parameter`() {
        loginAs("popular_paging")
        createArticle("Popular Paging One")
        createArticle("Popular Paging Two")

        val body = popular("?limit=1")
        assertEquals(1, body.articles.size)
        assertEquals(body.articles.size, body.articlesCount)
    }

    @Test
    fun `popular respects the offset query parameter`() {
        loginAs("popular_offset")
        createArticle("Popular Offset One")
        createArticle("Popular Offset Two")

        // 2 articles total; skipping the first leaves exactly 1.
        val body = popular("?offset=1")
        assertEquals(1, body.articles.size)
    }

    @Test
    fun `popular without authentication returns 401`() {
        assertEquals(HttpStatus.SC_UNAUTHORIZED, appRule.http.getStatus("/api/articles/feed/popular"))
    }
}
