package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.request.receive
import io.ktor.response.respond
import io.realworld.app.domain.ArticleDTO
import io.realworld.app.domain.ArticlesDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.service.ArticleService

class ArticleController(private val articleService: ArticleService) {

    suspend fun findBy(ctx: ApplicationCall) {
        val tag = ctx.parameters["tag"]
        val author = ctx.parameters["author"]
        val favorited = ctx.parameters["favorited"]
        val limit = (ctx.parameters["limit"] ?: "20").toInt()
        val offset = (ctx.parameters["offset"] ?: "0").toInt()
        val articles = articleService.findBy(emailOf(ctx), tag, author, favorited, limit, offset)
        ctx.respond(ArticlesDTO(articles, articles.size))
    }

    suspend fun feed(ctx: ApplicationCall) {
        val limit = (ctx.parameters["limit"] ?: "20").toInt()
        val offset = (ctx.parameters["offset"] ?: "0").toInt()
        val articles = articleService.findFeed(requireEmail(ctx), limit, offset)
        ctx.respond(ArticlesDTO(articles, articles.size))
    }

    suspend fun search(ctx: ApplicationCall) {
        val query = ctx.parameters["q"]
        val limit = (ctx.parameters["limit"] ?: "20").toInt()
        val offset = (ctx.parameters["offset"] ?: "0").toInt()
        val articles = articleService.search(emailOf(ctx), query, limit, offset)
        ctx.respond(ArticlesDTO(articles, articles.size))
    }

    suspend fun popular(ctx: ApplicationCall) {
        val limit = (ctx.parameters["limit"] ?: "20").toInt()
        val offset = (ctx.parameters["offset"] ?: "0").toInt()
        val articles = articleService.findPopular(emailOf(ctx), limit, offset)
        ctx.respond(ArticlesDTO(articles, articles.size))
    }

    suspend fun get(ctx: ApplicationCall) {
        val slug = requireSlug(ctx)
        ctx.respond(ArticleDTO(articleService.findBySlug(emailOf(ctx), slug)))
    }

    suspend fun create(ctx: ApplicationCall) {
        val article = ctx.receive<ArticleDTO>().article
        requireNotNull(article) { "Article body is required." }
        require(!article.body.isBlank()) { "Article body must not be empty." }
        ctx.respond(ArticleDTO(articleService.create(requireEmail(ctx), article)))
    }

    suspend fun update(ctx: ApplicationCall) {
        val slug = requireSlug(ctx)
        val article = ctx.receive<ArticleDTO>().article
        requireNotNull(article) { "Article body is required." }
        ctx.respond(ArticleDTO(articleService.update(emailOf(ctx), slug, article)))
    }

    suspend fun delete(ctx: ApplicationCall) {
        articleService.delete(requireSlug(ctx))
        ctx.respond(mapOf("message" to "Article deleted."))
    }

    suspend fun favorite(ctx: ApplicationCall) {
        val slug = requireSlug(ctx)
        ctx.respond(ArticleDTO(articleService.favorite(requireEmail(ctx), slug)))
    }

    suspend fun unfavorite(ctx: ApplicationCall) {
        val slug = requireSlug(ctx)
        ctx.respond(ArticleDTO(articleService.unfavorite(requireEmail(ctx), slug)))
    }

    private fun emailOf(ctx: ApplicationCall): String? =
        ctx.authentication.principal<User>()?.email

    private fun requireEmail(ctx: ApplicationCall): String =
        emailOf(ctx) ?: throw io.realworld.app.domain.exceptions.UnauthorizedException("Authentication required.")

    private fun requireSlug(ctx: ApplicationCall): String =
        ctx.parameters["slug"] ?: throw IllegalArgumentException("Article slug is required.")
}
