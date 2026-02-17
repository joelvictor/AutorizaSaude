package br.com.autorizasaude.tisshub.tissguide.infrastructure

import br.com.autorizasaude.tisshub.tissguide.domain.TissGuide
import jakarta.enterprise.context.ApplicationScoped
import javax.sql.DataSource

@ApplicationScoped
class TissGuideRepository(
    private val dataSource: DataSource
) {
    fun insert(tissGuide: TissGuide) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                insert into tiss_guides (
                  tiss_guide_id, tenant_id, authorization_id, tiss_version,
                  xml_content, xml_hash, validation_status, validation_report, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, tissGuide.tissGuideId)
                statement.setObject(2, tissGuide.tenantId)
                statement.setObject(3, tissGuide.authorizationId)
                statement.setString(4, tissGuide.tissVersion)
                statement.setString(5, tissGuide.xmlContent)
                statement.setString(6, tissGuide.xmlHash)
                statement.setString(7, tissGuide.validationStatus.name)
                statement.setString(8, tissGuide.validationReport)
                statement.setObject(9, tissGuide.createdAt)
                statement.executeUpdate()
            }
        }
    }
}
