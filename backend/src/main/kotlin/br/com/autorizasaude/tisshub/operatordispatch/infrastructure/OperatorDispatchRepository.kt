package br.com.autorizasaude.tisshub.operatordispatch.infrastructure

import br.com.autorizasaude.tisshub.operatordispatch.domain.DispatchType
import br.com.autorizasaude.tisshub.operatordispatch.domain.OperatorDispatch
import br.com.autorizasaude.tisshub.operatordispatch.domain.TechnicalStatus
import jakarta.enterprise.context.ApplicationScoped
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

@ApplicationScoped
class OperatorDispatchRepository(
    private val dataSource: DataSource
) {
    fun insert(dispatch: OperatorDispatch) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                insert into operator_dispatches (
                  dispatch_id, tenant_id, authorization_id, operator_code, dispatch_type,
                  technical_status, attempt_count, external_protocol, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, dispatch.dispatchId)
                statement.setObject(2, dispatch.tenantId)
                statement.setObject(3, dispatch.authorizationId)
                statement.setString(4, dispatch.operatorCode)
                statement.setString(5, dispatch.dispatchType.name)
                statement.setString(6, dispatch.technicalStatus.name)
                statement.setInt(7, dispatch.attemptCount)
                statement.setString(8, dispatch.externalProtocol)
                statement.setObject(9, dispatch.createdAt)
                statement.setObject(10, dispatch.updatedAt)
                statement.executeUpdate()
            }
        }
    }

    fun findLatestByAuthorization(tenantId: UUID, authorizationId: UUID): OperatorDispatch? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select dispatch_id, tenant_id, authorization_id, operator_code, dispatch_type,
                       technical_status, attempt_count, external_protocol, created_at, updated_at
                from operator_dispatches
                where tenant_id = ? and authorization_id = ?
                order by created_at desc
                limit 1
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.setObject(2, authorizationId)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }
                    return resultSet.toDispatch()
                }
            }
        }
    }

    fun update(dispatch: OperatorDispatch) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                update operator_dispatches
                set technical_status = ?, attempt_count = ?, external_protocol = ?, updated_at = ?
                where dispatch_id = ? and tenant_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, dispatch.technicalStatus.name)
                statement.setInt(2, dispatch.attemptCount)
                statement.setString(3, dispatch.externalProtocol)
                statement.setObject(4, dispatch.updatedAt)
                statement.setObject(5, dispatch.dispatchId)
                statement.setObject(6, dispatch.tenantId)
                statement.executeUpdate()
            }
        }
    }

    private fun ResultSet.toDispatch(): OperatorDispatch = OperatorDispatch(
        dispatchId = getObject("dispatch_id", UUID::class.java),
        tenantId = getObject("tenant_id", UUID::class.java),
        authorizationId = getObject("authorization_id", UUID::class.java),
        operatorCode = getString("operator_code"),
        dispatchType = DispatchType.valueOf(getString("dispatch_type")),
        technicalStatus = TechnicalStatus.valueOf(getString("technical_status")),
        attemptCount = getInt("attempt_count"),
        externalProtocol = getString("external_protocol"),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java)
    )
}
