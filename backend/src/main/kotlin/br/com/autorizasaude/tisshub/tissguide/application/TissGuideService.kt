package br.com.autorizasaude.tisshub.tissguide.application

import br.com.autorizasaude.tisshub.authorization.domain.Authorization
import br.com.autorizasaude.tisshub.tissguide.domain.TissGuide
import br.com.autorizasaude.tisshub.tissguide.domain.TissGuideValidationStatus
import br.com.autorizasaude.tisshub.tissguide.infrastructure.TissGuideRepository
import jakarta.enterprise.context.ApplicationScoped
import org.xml.sax.SAXException
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

data class TissGuideGenerationResult(
    val tissGuide: TissGuide,
    val valid: Boolean
)

@ApplicationScoped
class TissGuideService(
    private val repository: TissGuideRepository
) {
    fun generateAndValidate(authorization: Authorization): TissGuideGenerationResult {
        val xml = buildXml(authorization)
        val validationError = validateXml(xml)
        val status = if (validationError == null) {
            TissGuideValidationStatus.VALID
        } else {
            TissGuideValidationStatus.INVALID
        }

        val tissGuide = TissGuide(
            tissGuideId = UUID.randomUUID(),
            tenantId = authorization.tenantId,
            authorizationId = authorization.authorizationId,
            tissVersion = "4.01.00",
            xmlContent = xml,
            xmlHash = sha256(xml),
            validationStatus = status,
            validationReport = validationError,
            createdAt = OffsetDateTime.now()
        )
        repository.insert(tissGuide)
        return TissGuideGenerationResult(
            tissGuide = tissGuide,
            valid = status == TissGuideValidationStatus.VALID
        )
    }

    private fun buildXml(authorization: Authorization): String {
        val proceduresXml = authorization.procedureCodes.joinToString("") { code ->
            """<procedure code="${escapeXml(code)}"/>"""
        }

        return """
            <tissGuide version="4.01.00">
              <authorizationId>${authorization.authorizationId}</authorizationId>
              <tenantId>${authorization.tenantId}</tenantId>
              <operatorCode>${escapeXml(authorization.operatorCode)}</operatorCode>
              <patientId>${escapeXml(authorization.patientId)}</patientId>
              <clinicalJustification>${escapeXml(authorization.clinicalJustification)}</clinicalJustification>
              <procedures>$proceduresXml</procedures>
            </tissGuide>
        """.trimIndent()
    }

    private fun validateXml(xml: String): String? {
        return try {
            val xsdUrl = javaClass.classLoader.getResource("xsd/tiss-guide-v1.xsd")
                ?: throw IllegalStateException("xsd/tiss-guide-v1.xsd not found")
            val schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(xsdUrl)
            val validator = schema.newValidator()
            validator.validate(StreamSource(StringReader(xml)))
            null
        } catch (ex: SAXException) {
            ex.message ?: "unknown-xsd-validation-error"
        }
    }

    private fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
