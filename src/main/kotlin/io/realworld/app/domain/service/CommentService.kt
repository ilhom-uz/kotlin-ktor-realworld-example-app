package io.realworld.app.domain.service

import io.realworld.app.domain.Comment
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.domain.repository.ArticleRepository
import io.realworld.app.domain.repository.CommentRepository
import io.realworld.app.domain.repository.UserRepository

class CommentService(
    private val commentRepository: CommentRepository,
    private val articleRepository: ArticleRepository,
    private val userRepository: UserRepository
) {
    fun add(email: String, slug: String, comment: Comment): Comment {
        require(comment.body.isNotBlank()) { "Comment body must not be empty." }
        val author = userRepository.findByEmail(email)
            ?: throw NotFoundException("User not found to add comment.")
        val articleId = articleRepository.findArticleIdBySlug(slug)
            ?: throw NotFoundException("Article not found to add comment.")
        return commentRepository.create(articleId, author.id!!, comment)
    }

    fun findBySlug(slug: String): List<Comment> {
        val articleId = articleRepository.findArticleIdBySlug(slug)
            ?: throw NotFoundException("Article not found to load comments.")
        return commentRepository.findByArticle(articleId)
    }

    fun delete(slug: String, id: Long) {
        articleRepository.findArticleIdBySlug(slug)
            ?: throw NotFoundException("Article not found to delete comment.")
        commentRepository.findById(id)
            ?: throw NotFoundException("Comment not found to delete.")
        commentRepository.delete(id)
    }
}
