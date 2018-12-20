package org.sirix.rest.crud

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Context
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.auth.User
import io.vertx.ext.auth.oauth2.OAuth2Auth
import io.vertx.ext.web.RoutingContext
import io.vertx.kotlin.core.executeBlockingAwait
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.kotlin.ext.auth.authenticateAwait
import io.vertx.kotlin.ext.auth.isAuthorizedAwait
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.sirix.access.Databases
import org.sirix.api.Database
import org.sirix.api.ResourceManager
import org.sirix.api.XdmNodeWriteTrx
import org.sirix.rest.Auth
import java.nio.file.Path

class Delete(private val location: Path, private val keycloak: OAuth2Auth) {
    suspend fun handle(ctx: RoutingContext) {
        val dbName = ctx.pathParam("database")

        val user = Auth(keycloak).authenticateUser(ctx)

        val isAuthorized =
                if (dbName != null)
                    user.isAuthorizedAwait("realm:${dbName.toLowerCase()}-delete")
                else
                    user.isAuthorizedAwait("realm:delete")

        if (!isAuthorized) {
            ctx.fail(HttpResponseStatus.UNAUTHORIZED.code())
            return
        }

        val resName: String? = ctx.pathParam("resource")
        val nodeId: String? = ctx.queryParam("nodeId").getOrNull(0)

        if (dbName == null) {
            ctx.fail(IllegalArgumentException("Database name not given."))
            return
        }

        delete(dbName, resName, nodeId?.toLongOrNull(), ctx)
    }

    private suspend fun delete(dbPathName: String, resPathName: String?, nodeId: Long?, ctx: RoutingContext) {
        val dbFile = location.resolve(dbPathName)
        val context = ctx.vertx().orCreateContext
        val dispatcher = ctx.vertx().dispatcher()

        if (resPathName == null) {
            removeDatabase(dbFile, dispatcher)
            ctx.response().setStatusCode(204).end()
            return
        }

        val database = Databases.openDatabase(dbFile)

        database.use {
            if (nodeId == null) {
                removeResource(dispatcher, database, resPathName, ctx)
            } else {
                val manager = database.getResourceManager(resPathName)

                removeSubtree(manager, nodeId, context)
            }
        }

        if (!ctx.failed())
            ctx.response().setStatusCode(200).end()
    }

    private suspend fun removeDatabase(dbFile: Path?, dispatcher: CoroutineDispatcher) {
        withContext(dispatcher) {
            Databases.removeDatabase(dbFile)
        }
    }

    private suspend fun removeResource(dispatcher: CoroutineDispatcher, database: Database, resPathName: String?, ctx: RoutingContext): Any? {
        return try {
            withContext(dispatcher) {
                database.removeResource(resPathName)
            }
        } catch (e: IllegalStateException) {
            ctx.fail(IllegalStateException("Open resource managers found."))
        }
    }

    private suspend fun removeSubtree(manager: ResourceManager, nodeId: Long, context: Context): XdmNodeWriteTrx? {
        return context.executeBlockingAwait(Handler<Future<XdmNodeWriteTrx>> {
            manager.use { resourceManager ->
                val wtx = resourceManager.beginNodeWriteTrx()

                wtx.moveTo(nodeId)

                wtx.remove()
                wtx.commit()

                it.complete(wtx)
            }
        })
    }
}