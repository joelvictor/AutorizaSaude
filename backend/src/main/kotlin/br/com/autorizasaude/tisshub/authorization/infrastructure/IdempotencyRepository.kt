package br.com.autorizasaude.tisshub.authorization.infrastructure

import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID
import javax.sql.DataSource

data class IdempotencyRecord(
    val requestHash: String,
    val authorizationId: UUID?
)

@ApplicationScoped
class IdempotencyRepository(
    private val dataSource: DataSource
) {
    fun find(tenantId: UUID, idempotencyKey: String): IdempotencyRecord? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select request_hash, authorization_id
                from idempotency_keys
                where tenant_id = ? and idempotency_key = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.setString(2, idempotencyKey)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }
                    return IdempotencyRecord(
                        requestHash = resultSet.getString("request_hash"),
                        authorizationId = resultSet.getObject("authorization_id", UUID::class.java)
                    )
                }
            }
        }
    }

    fun insertPending(tenantId: UUID, idempotencyKey: String, requestHash: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                insert into idempotency_keys (tenant_id, idempotency_key, request_hash)
                values (?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.setString(2, idempotencyKey)
                statement.setString(3, requestHash)
                statement.executeUpdate()
            }
        }
    }

    fun linkAuthorization(tenantId: UUID, idempotencyKey: String, authorizationId: UUID, responseSnapshot: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                update idempotency_keys
                set authorization_id = ?, response_snapshot = ?
                where tenant_id = ? and idempotency_key = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, authorizationId)
                statement.setString(2, responseSnapshot)
                statement.setObject(3, tenantId)
                statement.setString(4, idempotencyKey)
                statement.executeUpdate()
            }
        }
    }
}
