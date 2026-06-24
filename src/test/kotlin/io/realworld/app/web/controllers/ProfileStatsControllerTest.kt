package io.realworld.app.web.controllers

import io.realworld.app.domain.Article
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.Comment
import io.realworld.app.domain.CommentDTO
import io.realworld.app.domain.StatsDTO
import io.realworld.app.web.rules.AppRule
import io.realworld.app.web.util.HttpUtil
import org.apache.http.HttpStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tests for Option B: GET /api/profiles/:username/stats
 *
 * Returns the user's articlesCount, commentsCount and favoritesCount.
 * Reading stats is public; an unknown username returns 404.
 */
class ProfileStatsControllerTest {
    @Rule
    @JvmField
    val appRule = AppRule()

    private fun loginAs(user: String) =
        appRule.http.createUser("$user@valid_email.com", user)

    private fun createArticle(title: String): String {
        val article = Article(title = title, description = "d", body = "body", tagList = listOf("stats"))
        return appRule.http.post<ArticleDTO>("/api/articles", ArticleDTO(article)).body.article?.slug
            ?: error("article was not created")
    }

    private fun statsOf(username: String): StatsDTO =
        appRule.http.get<StatsDTO>("/api/profiles/$username/stats").body

    @Test
    fun `stats reflect the users articles, comments and favorites`() {
        loginAs("stats_active")
        val slug = createArticle("Stats Active Article")
        createArticle("Stats Active Second")
        appRule.http.post<ArticleDTO>("/api/articles/$slug/favorite", "")
        appRule.http.post<CommentDTO>("/api/articles/$slug/comments", CommentDTO(Comment(body = "nice article")))

        val stats = statsOf("stats_active").stats
        assertEquals(2, stats.articlesCount)
        assertEquals(1, stats.commentsCount)
        assertEquals(1, stats.favoritesCount)
    }

    @Test
    fun `stats are all zero for a user with no activity`() {
        loginAs("stats_idle")

        val stats = statsOf("stats_idle").stats
        assertEquals(0, stats.articlesCount)
        assertEquals(0, stats.commentsCount)
        assertEquals(0, stats.favoritesCount)
    }

    @Test
    fun `articlesCount only counts the users own articles`() {
        loginAs("stats_author_a")
        createArticle("Author A First")
        createArticle("Author A Second")
        loginAs("stats_author_b")
        createArticle("Author B Only")

        assertEquals(2, statsOf("stats_author_a").stats.articlesCount)
        assertEquals(1, statsOf("stats_author_b").stats.articlesCount)
    }

    @Test
    fun `favoritesCount counts articles the user favorited including other peoples articles`() {
        loginAs("stats_fav_author")
        val slug = createArticle("Favoritable Article")

        loginAs("stats_fav_reader")
        appRule.http.post<ArticleDTO>("/api/articles/$slug/favorite", "")

        // The reader favorited someone else's article.
        assertEquals(1, statsOf("stats_fav_reader").stats.favoritesCount)
        // The author favorited nothing.
        assertEquals(0, statsOf("stats_fav_author").stats.favoritesCount)
    }

    @Test
    fun `favoritesCount decreases after unfavoriting`() {
        loginAs("stats_unfav")
        val slug = createArticle("Unfavoritable Article")
        appRule.http.post<ArticleDTO>("/api/articles/$slug/favorite", "")
        assertEquals(1, statsOf("stats_unfav").stats.favoritesCount)

        appRule.http.delete("/api/articles/$slug/favorite")
        assertEquals(0, statsOf("stats_unfav").stats.favoritesCount)
    }

    @Test
    fun `commentsCount increases with each comment`() {
        loginAs("stats_commenter")
        val slug = createArticle("Commented Article")
        appRule.http.post<CommentDTO>("/api/articles/$slug/comments", CommentDTO(Comment(body = "first")))
        appRule.http.post<CommentDTO>("/api/articles/$slug/comments", CommentDTO(Comment(body = "second")))

        assertEquals(2, statsOf("stats_commenter").stats.commentsCount)
    }

    @Test
    fun `stats can be read without authentication`() {
        loginAs("stats_public")
        createArticle("Public Stats Article")

        // A fresh client with no Authorization header.
        val anonymous = HttpUtil(appRule.port)
        val response = anonymous.get<StatsDTO>("/api/profiles/stats_public/stats")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1, response.body.stats.articlesCount)
    }

    @Test
    fun `stats can be read by an authenticated user`() {
        loginAs("stats_authed")
        createArticle("Authed Stats Article")

        val response = appRule.http.get<StatsDTO>("/api/profiles/stats_authed/stats")

        assertEquals(HttpStatus.SC_OK, response.status)
        assertEquals(1, response.body.stats.articlesCount)
    }

    @Test
    fun `stats for an unknown user returns 404`() {
        assertEquals(HttpStatus.SC_NOT_FOUND, appRule.http.getStatus("/api/profiles/no_such_user_zzz/stats"))
    }
}
