package io.realworld.app.domain.service

import io.realworld.app.domain.Profile
import io.realworld.app.domain.Stats
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.domain.repository.ArticleRepository
import io.realworld.app.domain.repository.CommentRepository
import io.realworld.app.domain.repository.UserRepository

class ProfileService(
    private val userRepository: UserRepository,
    private val articleRepository: ArticleRepository,
    private val commentRepository: CommentRepository
) {
    fun getByUsername(email: String?, username: String): Profile {
        val user = userRepository.findByUsername(username)
            ?: throw NotFoundException("Profile not found.")
        val following = email != null && userRepository.findIsFollowUser(email, user.id!!)
        return Profile(user.username, user.bio, user.image, following)
    }

    fun follow(email: String, username: String): Profile {
        val user = userRepository.follow(email, username)
        return Profile(user.username, user.bio, user.image, true)
    }

    fun unfollow(email: String, username: String): Profile {
        val user = userRepository.unfollow(email, username)
        return Profile(user.username, user.bio, user.image, false)
    }

    fun getStats(username: String): Stats {
        val user = userRepository.findByUsername(username)
            ?: throw NotFoundException("Profile not found to load stats.")
        return Stats(
            articlesCount = articleRepository.countByAuthor(user.id!!),
            commentsCount = commentRepository.countByAuthor(user.id),
            favoritesCount = articleRepository.countFavoritesByUser(user.id)
        )
    }
}
