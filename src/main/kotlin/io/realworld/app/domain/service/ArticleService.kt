package io.realworld.app.domain.service

import io.realworld.app.domain.Article
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.domain.repository.ArticleRepository
import io.realworld.app.domain.repository.UserRepository

class ArticleService(
    private val articleRepository: ArticleRepository,
    private val userRepository: UserRepository
) {
    fun create(email: String, article: Article): Article {
        val author = userRepository.findByEmail(email)
            ?: throw NotFoundException("User not found to create article.")
        return articleRepository.create(author.id!!, article)
    }

    fun findBySlug(email: String?, slug: String): Article {
        return articleRepository.findBySlug(slug, userIdOrNull(email))
            ?: throw NotFoundException("Article not found.")
    }

    fun findBy(email: String?, tag: String?, author: String?, favorited: String?, limit: Int, offset: Int): List<Article> =
        articleRepository.findBy(tag, author, favorited, limit, offset, userIdOrNull(email))

    fun findFeed(email: String, limit: Int, offset: Int): List<Article> {
        val user = userRepository.findByEmail(email)
            ?: throw NotFoundException("User not found to load feed.")
        val followedIds = userRepository.findFollowedUserIds(user.id!!)
        return articleRepository.findFeed(followedIds, limit, offset, user.id)
    }

    fun search(email: String?, query: String?, limit: Int, offset: Int): List<Article> {
        require(!query.isNullOrBlank()) { "Search query 'q' must not be empty." }
        return articleRepository.search(query, limit, offset, userIdOrNull(email))
    }

    fun findPopular(email: String?, limit: Int, offset: Int): List<Article> =
        articleRepository.findPopular(limit, offset, userIdOrNull(email))

    fun update(email: String?, slug: String, article: Article): Article {
        articleRepository.findBySlug(slug) ?: throw NotFoundException("Article not found to update.")
        return articleRepository.update(slug, article, userIdOrNull(email))
            ?: throw NotFoundException("Article not found to update.")
    }

    fun delete(slug: String) {
        articleRepository.findBySlug(slug) ?: throw NotFoundException("Article not found to delete.")
        articleRepository.delete(slug)
    }

    fun favorite(email: String, slug: String): Article {
        val user = userRepository.findByEmail(email)
            ?: throw NotFoundException("User not found to favorite article.")
        return articleRepository.favorite(slug, user.id!!)
            ?: throw NotFoundException("Article not found to favorite.")
    }

    fun unfavorite(email: String, slug: String): Article {
        val user = userRepository.findByEmail(email)
            ?: throw NotFoundException("User not found to unfavorite article.")
        return articleRepository.unfavorite(slug, user.id!!)
            ?: throw NotFoundException("Article not found to unfavorite.")
    }

    private fun userIdOrNull(email: String?): Long? =
        email?.let { userRepository.findByEmail(it)?.id }
}
