package io.realworld.app.domain.repository

import io.realworld.app.domain.Comment
import io.realworld.app.domain.User
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Date

internal object Comments : LongIdTable() {
    val articleId: Column<Long> = long("article_id")
    val authorId: Column<Long> = long("author_id")
    val body: Column<String> = text("body")
    val createdAt: Column<Long> = long("created_at")
    val updatedAt: Column<Long> = long("updated_at")
}

class CommentRepository {
    init {
        transaction {
            SchemaUtils.create(Comments)
        }
    }

    fun create(articleId: Long, authorId: Long, comment: Comment): Comment {
        val now = System.currentTimeMillis()
        val id = transaction {
            Comments.insertAndGetId { row ->
                row[Comments.articleId] = articleId
                row[Comments.authorId] = authorId
                row[body] = comment.body
                row[createdAt] = now
                row[updatedAt] = now
            }.value
        }
        return findById(id) ?: comment
    }

    fun findByArticle(articleId: Long): List<Comment> = transaction {
        Comments.select { Comments.articleId eq articleId }
            .orderBy(Comments.createdAt to SortOrder.DESC)
            .toList()
            .map { buildComment(it) }
    }

    fun findById(id: Long): Comment? = transaction {
        Comments.select { Comments.id eq id }
            .firstOrNull()
            ?.let { buildComment(it) }
    }

    fun delete(id: Long) {
        transaction {
            Comments.deleteWhere { Comments.id eq id }
        }
    }

    fun countByAuthor(authorId: Long): Int = transaction {
        Comments.select { Comments.authorId eq authorId }.count()
    }

    private fun buildComment(row: ResultRow): Comment {
        val authorId = row[Comments.authorId]
        val authorRow = Users.select { Users.id eq authorId }.first()
        val author = User(
            id = authorRow[Users.id].value,
            email = authorRow[Users.email],
            username = authorRow[Users.username],
            bio = authorRow[Users.bio],
            image = authorRow[Users.image]
        )
        return Comment(
            id = row[Comments.id].value,
            createdAt = Date(row[Comments.createdAt]),
            updatedAt = Date(row[Comments.updatedAt]),
            body = row[Comments.body],
            author = author
        )
    }
}
