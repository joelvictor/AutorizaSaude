package br.com.autorizasaude.tisshub.operatordispatch.integration

import br.com.autorizasaude.tisshub.operatordispatch.application.OperatorAdapter
import br.com.autorizasaude.tisshub.operatordispatch.application.OperatorAdapterPollResult
import br.com.autorizasaude.tisshub.operatordispatch.application.OperatorAdapterSendResult
import br.com.autorizasaude.tisshub.operatordispatch.application.ExternalAuthorizationStatus
import br.com.autorizasaude.tisshub.operatordispatch.domain.DispatchType
import br.com.autorizasaude.tisshub.operatordispatch.domain.OperatorDispatch
import br.com.autorizasaude.tisshub.operatordispatch.domain.TechnicalStatus
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

@ApplicationScoped
class TypeBOperatorAdapter : OperatorAdapter {
    override val dispatchType: DispatchType = DispatchType.TYPE_B

    override fun send(dispatch: OperatorDispatch): OperatorAdapterSendResult {
        return OperatorAdapterSendResult(
            technicalStatus = TechnicalStatus.POLLING,
            externalProtocol = "B-${UUID.randomUUID().toString().take(8)}"
        )
    }

    override fun poll(dispatch: OperatorDispatch): OperatorAdapterPollResult {
        val normalizedOperator = dispatch.operatorCode
            .trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9]+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')

        return if (normalizedOperator.contains("ALLIANZ")) {
            OperatorAdapterPollResult(
                externalStatus = ExternalAuthorizationStatus.DENIED,
                operatorReference = dispatch.externalProtocol,
                denialReasonCode = "COVERAGE_EXCLUSION",
                denialReason = "Procedimento nao coberto pelo plano"
            )
        } else {
            OperatorAdapterPollResult(
                externalStatus = ExternalAuthorizationStatus.APPROVED,
                operatorReference = dispatch.externalProtocol,
                denialReasonCode = null,
                denialReason = null
            )
        }
    }
}
