package br.com.autorizasaude.tisshub.api

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.Test
import java.util.UUID

@QuarkusTest
class AuthorizationResourceTest {
    @Test
    fun `should create authorization`() {
        given()
            .header("X-Tenant-Id", UUID.randomUUID().toString())
            .contentType("application/json")
            .body(
                """
                {
                  "patientId": "P-001",
                  "operatorCode": "BRADESCO",
                  "procedureCodes": ["12345678"],
                  "clinicalJustification": "Indicacao medica"
                }
                """.trimIndent()
            )
            .`when`().post("/v1/authorizations")
            .then()
            .statusCode(201)
            .body("status", equalTo("DRAFT"))
    }
}
