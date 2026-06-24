package io.realworld.app.web.controllers

import io.ktor.application.ApplicationCall
import io.ktor.auth.authentication
import io.ktor.response.respond
import io.realworld.app.domain.ProfileDTO
import io.realworld.app.domain.StatsDTO
import io.realworld.app.domain.User
import io.realworld.app.domain.exceptions.UnauthorizedException
import io.realworld.app.domain.service.ProfileService

class ProfileController(private val profileService: ProfileService) {

    suspend fun get(ctx: ApplicationCall) {
        val username = requireUsername(ctx)
        ctx.respond(ProfileDTO(profileService.getByUsername(emailOf(ctx), username)))
    }

    suspend fun follow(ctx: ApplicationCall) {
        val username = requireUsername(ctx)
        ctx.respond(ProfileDTO(profileService.follow(requireEmail(ctx), username)))
    }

    suspend fun unfollow(ctx: ApplicationCall) {
        val username = requireUsername(ctx)
        ctx.respond(ProfileDTO(profileService.unfollow(requireEmail(ctx), username)))
    }

    suspend fun stats(ctx: ApplicationCall) {
        val username = requireUsername(ctx)
        ctx.respond(StatsDTO(profileService.getStats(username)))
    }

    private fun emailOf(ctx: ApplicationCall): String? =
        ctx.authentication.principal<User>()?.email

    private fun requireEmail(ctx: ApplicationCall): String =
        emailOf(ctx) ?: throw UnauthorizedException("Authentication required.")

    private fun requireUsername(ctx: ApplicationCall): String =
        ctx.parameters["username"] ?: throw IllegalArgumentException("Username is required.")
}
