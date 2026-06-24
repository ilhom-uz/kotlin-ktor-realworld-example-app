package io.realworld.app.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.realworld.app.domain.repository.ArticleFavorites
import io.realworld.app.domain.repository.ArticleTags
import io.realworld.app.domain.repository.Articles
import io.realworld.app.domain.repository.Comments
import io.realworld.app.domain.repository.Follows
import io.realworld.app.domain.repository.Tags
import io.realworld.app.domain.repository.Users
import org.h2.tools.Server
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DbConfig {
    fun setup(jdbcUrl: String, username: String, password: String) {
        Server.createPgServer().start()
        val config = HikariConfig().also { config ->
            config.jdbcUrl = jdbcUrl
            config.username = username
            config.password = password
        }
        Database.connect(HikariDataSource(config))
        createSchema()
    }

    private fun createSchema() = transaction {
        SchemaUtils.create(Users, Follows, Articles, ArticleTags, ArticleFavorites, Comments, Tags)
    }
}
