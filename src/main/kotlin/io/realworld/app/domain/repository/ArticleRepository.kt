package io.realworld.app.domain.repository

import io.realworld.app.domain.Article
import io.realworld.app.domain.User
import io.realworld.app.domain.exceptions.NotFoundException
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
// Tags is declared in TagRepository.kt (same module) and shared so GET /api/tags reflects article tags.
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.Date

internal object Articles : LongIdTable() {
    val slug: Column<String> = varchar("slug", 255).uniqueIndex()
    val title: Column<String> = varchar("title", 255)
    val description: Column<String?> = varchar("description", 1000).nullable()
    val body: Column<String> = text("body")
    val authorId: Column<Long> = long("author_id")
    val createdAt: Column<Long> = long("created_at")
    val updatedAt: Column<Long> = long("updated_at")
}

internal object ArticleTags : LongIdTable() {
    val articleId: Column<Long> = long("article_id")
    val tag: Column<String> = varchar("tag", 100)
}

internal object ArticleFavorites : Table() {
    val articleId: Column<Long> = long("article_id").primaryKey()
    val userId: Column<Long> = long("user_id").primaryKey()
}

class ArticleRepository {
    init {
        transaction {
            SchemaUtils.create(Articles)
            SchemaUtils.create(ArticleTags)
            SchemaUtils.create(ArticleFavorites)
        }
    }

    fun create(authorId: Long, article: Article): Article {
        val now = System.currentTimeMillis()
        val slug = transaction {
            val uniqueSlug = generateUniqueSlug(article.title)
            val newId = Articles.insertAndGetId { row ->
                row[Articles.slug] = uniqueSlug
                row[title] = article.title ?: ""
                row[description] = article.description
                row[body] = article.body
                row[Articles.authorId] = authorId
                row[createdAt] = now
                row[updatedAt] = now
            }.value
            article.tagList.distinct().forEach { name ->
                ArticleTags.insert { row ->
                    row[articleId] = newId
                    row[tag] = name
                }
                if (Tags.select { Tags.name eq name }.count() == 0) {
                    Tags.insert { row -> row[Tags.name] = name }
                }
            }
            uniqueSlug
        }
        return findBySlug(slug, authorId) ?: throw NotFoundException("Article not found after creation.")
    }

    fun findBySlug(slug: String, currentUserId: Long? = null): Article? = transaction {
        Articles.select { Articles.slug eq slug }
            .firstOrNull()
            ?.let { buildArticle(it, currentUserId) }
    }

    fun findBy(
        tag: String?,
        author: String?,
        favoritedBy: String?,
        limit: Int,
        offset: Int,
        currentUserId: Long? = null
    ): List<Article> = transaction {
        val rows = Articles.selectAll()
            .orderBy(Articles.createdAt to SortOrder.DESC)
            .toList()
        rows.map { buildArticle(it, currentUserId) }
            .asSequence()
            .filter { article -> author == null || article.author?.username == author }
            .filter { article -> tag == null || article.tagList.contains(tag) }
            .filter { article -> favoritedBy == null || isFavoritedByUsername(article.slug, favoritedBy) }
            .drop(offset)
            .take(limit)
            .toList()
    }

    fun findFeed(followedAuthorIds: List<Long>, limit: Int, offset: Int, currentUserId: Long? = null): List<Article> {
        if (followedAuthorIds.isEmpty()) return emptyList()
        return transaction {
            Articles.selectAll()
                .orderBy(Articles.createdAt to SortOrder.DESC)
                .toList()
                .filter { followedAuthorIds.contains(it[Articles.authorId]) }
                .drop(offset)
                .take(limit)
                .map { buildArticle(it, currentUserId) }
        }
    }

    fun search(query: String, limit: Int, offset: Int, currentUserId: Long? = null): List<Article> = transaction {
        Articles.selectAll()
            .orderBy(Articles.createdAt to SortOrder.DESC)
            .toList()
            .filter { row ->
                row[Articles.title].contains(query, ignoreCase = true) ||
                    row[Articles.body].contains(query, ignoreCase = true)
            }
            .drop(offset)
            .take(limit)
            .map { buildArticle(it, currentUserId) }
    }

    fun findPopular(limit: Int, offset: Int, currentUserId: Long? = null): List<Article> = transaction {
        Articles.selectAll()
            .toList()
            .map { buildArticle(it, currentUserId) }
            .sortedWith(compareByDescending<Article> { it.favoritesCount }.thenByDescending { it.createdAt })
            .drop(offset)
            .take(limit)
    }

    fun update(slug: String, article: Article, currentUserId: Long? = null): Article? {
        transaction {
            Articles.update({ Articles.slug eq slug }) { row ->
                if (article.title != null) row[title] = article.title
                if (article.description != null) row[description] = article.description
                row[body] = article.body
                row[updatedAt] = System.currentTimeMillis()
            }
        }
        return findBySlug(slug, currentUserId)
    }

    fun delete(slug: String) {
        transaction {
            val articleId = articleIdBySlug(slug) ?: throw NotFoundException("Article not found to delete.")
            ArticleTags.deleteWhere { ArticleTags.articleId eq articleId }
            ArticleFavorites.deleteWhere { ArticleFavorites.articleId eq articleId }
            Articles.deleteWhere { Articles.slug eq slug }
        }
    }

    fun favorite(slug: String, userId: Long): Article? {
        transaction {
            val articleId = articleIdBySlug(slug) ?: throw NotFoundException("Article not found to favorite.")
            val alreadyFavorited = ArticleFavorites
                .select { (ArticleFavorites.articleId eq articleId) and (ArticleFavorites.userId eq userId) }
                .count() > 0
            if (!alreadyFavorited) {
                ArticleFavorites.insert { row ->
                    row[ArticleFavorites.articleId] = articleId
                    row[ArticleFavorites.userId] = userId
                }
            }
        }
        return findBySlug(slug, userId)
    }

    fun unfavorite(slug: String, userId: Long): Article? {
        transaction {
            val articleId = articleIdBySlug(slug) ?: throw NotFoundException("Article not found to unfavorite.")
            ArticleFavorites.deleteWhere {
                (ArticleFavorites.articleId eq articleId) and (ArticleFavorites.userId eq userId)
            }
        }
        return findBySlug(slug, userId)
    }

    fun countByAuthor(authorId: Long): Int = transaction {
        Articles.select { Articles.authorId eq authorId }.count()
    }

    fun countFavoritesByUser(userId: Long): Int = transaction {
        ArticleFavorites.select { ArticleFavorites.userId eq userId }.count()
    }

    fun findArticleIdBySlug(slug: String): Long? = transaction {
        articleIdBySlug(slug)
    }

    private fun buildArticle(row: ResultRow, currentUserId: Long?): Article {
        val articleId = row[Articles.id].value
        val authorId = row[Articles.authorId]
        val authorRow = Users.select { Users.id eq authorId }.first()
        val author = User(
            id = authorRow[Users.id].value,
            email = authorRow[Users.email],
            username = authorRow[Users.username],
            bio = authorRow[Users.bio],
            image = authorRow[Users.image]
        )
        val tags = ArticleTags.select { ArticleTags.articleId eq articleId }.map { it[ArticleTags.tag] }
        val favoritesCount = ArticleFavorites.select { ArticleFavorites.articleId eq articleId }.count().toLong()
        val favorited = currentUserId != null && ArticleFavorites
            .select { (ArticleFavorites.articleId eq articleId) and (ArticleFavorites.userId eq currentUserId) }
            .count() > 0
        return Article(
            slug = row[Articles.slug],
            title = row[Articles.title],
            description = row[Articles.description],
            body = row[Articles.body],
            tagList = tags,
            createdAt = Date(row[Articles.createdAt]),
            updatedAt = Date(row[Articles.updatedAt]),
            favorited = favorited,
            favoritesCount = favoritesCount,
            author = author
        )
    }

    private fun articleIdBySlug(slug: String): Long? =
        Articles.select { Articles.slug eq slug }.firstOrNull()?.get(Articles.id)?.value

    private fun isFavoritedByUsername(slug: String?, username: String): Boolean {
        slug ?: return false
        return transaction {
            val articleId = articleIdBySlug(slug) ?: return@transaction false
            val user = Users.select { Users.username eq username }.firstOrNull() ?: return@transaction false
            ArticleFavorites
                .select { (ArticleFavorites.articleId eq articleId) and (ArticleFavorites.userId eq user[Users.id].value) }
                .count() > 0
        }
    }

    private fun generateUniqueSlug(title: String?): String {
        val base = slugify(title)
        var candidate = base
        var suffix = 1
        while (Articles.select { Articles.slug eq candidate }.count() > 0) {
            suffix++
            candidate = "$base-$suffix"
        }
        return candidate
    }

    private fun slugify(title: String?): String =
        (title ?: "")
            .lowercase()
            .trim()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "article" }
}
