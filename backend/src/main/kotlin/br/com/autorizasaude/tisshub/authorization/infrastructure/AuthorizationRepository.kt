package br.com.autorizasaude.tisshub.authorization.infrastructure

import br.com.autorizasaude.tisshub.authorization.domain.Authorization
import br.com.autorizasaude.tisshub.authorization.domain.AuthorizationStatus
import jakarta.enterprise.context.ApplicationScoped
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

@ApplicationScoped
class AuthorizationRepository(
    private val dataSource: DataSource
) {
    fun insert(authorization: Authorization) {
        dataSource.connection.use { connection ->
            val authorizationPk = connection.prepareStatement(
                """
                insert into authorizations (
                  authorization_id, tenant_id, patient_id, operator_code, status,
                  clinical_justification, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                Statement.RETURN_GENERATED_KEYS
            ).use { statement ->
                statement.setObject(1, authorization.authorizationId)
                statement.setObject(2, authorization.tenantId)
                statement.setString(3, authorization.patientId)
                statement.setString(4, authorization.operatorCode)
                statement.setString(5, authorization.status.name)
                statement.setString(6, authorization.clinicalJustification)
                statement.setObject(7, authorization.createdAt)
                statement.setObject(8, authorization.updatedAt)
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    if (!keys.next()) {
                        throw IllegalStateException("Failed to generate authorizations.id")
                    }
                    keys.getLong(1)
                }
            }

            connection.prepareStatement(
                """
                insert into authorization_procedures (authorization_pk, tenant_id, procedure_code)
                values (?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                authorization.procedureCodes.forEach { code ->
                    statement.setLong(1, authorizationPk)
                    statement.setObject(2, authorization.tenantId)
                    statement.setString(3, code)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    fun findById(tenantId: UUID, authorizationId: UUID): Authorization? {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select id, authorization_id, tenant_id, patient_id, operator_code, status,
                       clinical_justification, created_at, updated_at
                from authorizations
                where tenant_id = ? and authorization_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tenantId)
                statement.setObject(2, authorizationId)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) {
                        return null
                    }
                    val procedures = findProcedures(connection, tenantId, resultSet.getLong("id"))
                    return resultSet.toAuthorization(procedures)
                }
            }
        }
    }

    fun updateStatus(authorization: Authorization) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                update authorizations
                set status = ?, updated_at = ?
                where tenant_id = ? and authorization_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, authorization.status.name)
                statement.setObject(2, authorization.updatedAt)
                statement.setObject(3, authorization.tenantId)
                statement.setObject(4, authorization.authorizationId)
                statement.executeUpdate()
            }
        }
    }

    private fun findProcedures(connection: Connection, tenantId: UUID, authorizationPk: Long): List<String> {
        connection.prepareStatement(
            """
            select procedure_code
            from authorization_procedures
            where tenant_id = ? and authorization_pk = ?
            order by procedure_code
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, tenantId)
            statement.setLong(2, authorizationPk)
            statement.executeQuery().use { resultSet ->
                val procedures = mutableListOf<String>()
                while (resultSet.next()) {
                    procedures += resultSet.getString("procedure_code")
                }
                return procedures
            }
        }
    }

    private fun ResultSet.toAuthorization(procedureCodes: List<String>): Authorization = Authorization(
        authorizationId = getObject("authorization_id", UUID::class.java),
        tenantId = getObject("tenant_id", UUID::class.java),
        patientId = getString("patient_id"),
        operatorCode = getString("operator_code"),
        procedureCodes = procedureCodes,
        clinicalJustification = getString("clinical_justification"),
        status = AuthorizationStatus.valueOf(getString("status")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java)
    )
}
