package com.luxoft.poc.mobi

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

/**
 * Represents "materialised" [ProvisionOffer]
 * TODO
 */
data class ProvisionAgreementState(
        val provider: Party,
        val consumer: Party,
        val commitmentIds: List<UniqueIdentifier>,
        val paymentObligationIds: List<UniqueIdentifier>,
        override val linearId: UniqueIdentifier = UniqueIdentifier(ProvisionAgreementState::class.java.simpleName)
) : LinearState {
    override val participants = listOf(provider, consumer)
    fun contractClassName() : ContractClassName = ProvisionAgreementContract::class.java.canonicalName
}

/**
 * TODO
 */
class ProvisionAgreementContract : Contract {

    override fun verify(tx: LedgerTransaction) {
        // TODO
    }
}