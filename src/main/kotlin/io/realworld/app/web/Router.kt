package io.realworld.app.web

import io.ktor.auth.authenticate
import io.ktor.routing.Route
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.route
import io.realworld.app.web.controllers.ArticleController
import io.realworld.app.web.controllers.CommentController
import io.realworld.app.web.controllers.ProfileController
import io.realworld.app.web.controllers.TagController
import io.realworld.app.web.controllers.UserController

fun Route.users(userController: UserController) {
    route("users") {
        post { userController.register(this.context) }
        post("login") { userController.login(this.context) }
    }
    route("user") {
        authenticate {
            get { userController.getCurrent(this.context) }
            put { userController.update(this.context) }
        }
    }
}

fun Route.profiles(profileController: ProfileController) {
    route("profiles/{username}") {
        authenticate(optional = true) {
            get { profileController.get(this.context) }
            get("stats") { profileController.stats(this.context) }
        }
        authenticate {
            route("follow") {
                post { profileController.follow(this.context) }
                delete { profileController.unfollow(this.context) }
            }
        }
    }
}

fun Route.articles(articleController: ArticleController, commentController: CommentController) {
    route("articles") {
        // Public reads (authentication is optional and only used to compute `favorited`).
        authenticate(optional = true) {
            get { articleController.findBy(this.context) }
            get("{slug}") { articleController.get(this.context) }
            get("{slug}/comments") { commentController.findBySlug(this.context) }
        }
        // Authenticated actions.
        authenticate {
            get("feed") { articleController.feed(this.context) }
            get("feed/popular") { articleController.popular(this.context) }
            get("search") { articleController.search(this.context) }
            post { articleController.create(this.context) }
            route("{slug}") {
                put { articleController.update(this.context) }
                delete { articleController.delete(this.context) }
                route("favorite") {
                    post { articleController.favorite(this.context) }
                    delete { articleController.unfavorite(this.context) }
                }
                route("comments") {
                    post { commentController.add(this.context) }
                    delete("{id}") { commentController.delete(this.context) }
                }
            }
        }
    }
}

fun Route.tags(tagController: TagController) {
    route("tags") {
        authenticate(optional = true) {
            get { tagController.get(this.context) }
        }
    }
}
