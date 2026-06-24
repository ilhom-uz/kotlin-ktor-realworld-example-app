package io.realworld.app.config

import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.jwt
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.route
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.ApplicationEngineFactory
import io.ktor.server.engine.BaseApplicationEngine
import io.ktor.server.engine.EngineAPI
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.realworld.app.domain.exceptions.NotFoundException
import io.realworld.app.domain.exceptions.UnauthorizedException
import io.realworld.app.utils.JwtProvider
import io.realworld.app.web.ErrorResponse
import io.realworld.app.web.articles
import io.realworld.app.web.controllers.ArticleController
import io.realworld.app.web.controllers.CommentController
import io.realworld.app.web.controllers.ProfileController
import io.realworld.app.web.controllers.TagController
import io.realworld.app.web.controllers.UserController
import io.realworld.app.web.profiles
import io.realworld.app.web.tags
import io.realworld.app.web.users
import org.kodein.di.generic.instance

const val SERVER_PORT = 8080

@KtorExperimentalAPI
@EngineAPI
fun setup(isCio: Boolean = true): BaseApplicationEngine {
    // A uniquely-named in-memory database per setup keeps each test fully isolated
    // (the pooled connection keeps it alive for the JVM via DB_CLOSE_DELAY=-1).
    val dbName = "realworld_${System.nanoTime()}"
    DbConfig.setup("jdbc:h2:mem:$dbName;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false", "sa", "")
    return server(if (isCio) CIO else Netty)
}

@KtorExperimentalAPI
@EngineAPI
fun server(
    engine: ApplicationEngineFactory<BaseApplicationEngine,
        out ApplicationEngine.Configuration>
): BaseApplicationEngine {
    return embeddedServer(
        engine,
        port = SERVER_PORT,
        watchPaths = listOf("mainModule"),
        module = Application::mainModule
    )
}

@KtorExperimentalAPI
fun Application.mainModule() {
    val userController by ModulesConfig.kodein.instance<UserController>()
    val profileController by ModulesConfig.kodein.instance<ProfileController>()
    val articleController by ModulesConfig.kodein.instance<ArticleController>()
    val commentController by ModulesConfig.kodein.instance<CommentController>()
    val tagController by ModulesConfig.kodein.instance<TagController>()

    install(CallLogging)
    install(ContentNegotiation) {
        jackson {
        }
    }
    install(Authentication) {
        jwt {
            verifier(JwtProvider.verifier)
            authSchemes("Token")
            validate { credential ->
                if (credential.payload.audience.contains(JwtProvider.audience)) {
                    userController.getUserByEmail(credential.payload.claims["email"]?.asString())
                } else null
            }
        }
    }
    install(StatusPages) {
        exception<NotFoundException> { cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(mapOf("body" to listOf(cause.message))))
        }
        exception<UnauthorizedException> { cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(mapOf("body" to listOf(cause.message))))
        }
        exception<IllegalArgumentException> { cause ->
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(mapOf("body" to listOf(cause.message))))
        }
        exception<Throwable> { cause ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(mapOf("body" to listOf(cause.message))))
        }
    }

    install(Routing) {
        route("api") {
            users(userController)
            profiles(profileController)
            articles(articleController, commentController)
            tags(tagController)
        }
    }
}
