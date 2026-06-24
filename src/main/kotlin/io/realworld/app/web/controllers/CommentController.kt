package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.request.receive
import io.ktor.response.respond
import io.realworld.app.domain.CommentDTO
import io.realworld.app.domain.CommentsDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.exceptions.UnauthorizedException
import io.realworld.app.domain.service.CommentService

class CommentController(private val commentService: CommentService) {

    suspend fun add(ctx: ApplicationCall) {
        val slug = requireSlug(ctx)
        val comment = ctx.receive<CommentDTO>().comment
        requireNotNull(comment) { "Comment body is required." }
        ctx.respond(CommentDTO(commentService.add(requireEmail(ctx), slug, comment)))
    }

    suspend fun findBySlug(ctx: ApplicationCall) {
        val slug = requireSlug(ctx)
        ctx.respond(CommentsDTO(commentService.findBySlug(slug)))
    }

    suspend fun delete(ctx: ApplicationCall) {
        val slug = requireSlug(ctx)
        val id = ctx.parameters["id"]?.toLongOrNull()
            ?: throw IllegalArgumentException("Comment id is required.")
        commentService.delete(slug, id)
        ctx.respond(mapOf("message" to "Comment deleted."))
    }

    private fun requireEmail(ctx: ApplicationCall): String =
        ctx.authentication.principal<User>()?.email
            ?: throw UnauthorizedException("Authentication required.")

    private fun requireSlug(ctx: ApplicationCall): String =
        ctx.parameters["slug"] ?: throw IllegalArgumentException("Article slug is required.")
}
