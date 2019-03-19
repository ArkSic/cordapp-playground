package com.luxoft.poc.mobi

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction

/**
 * Primitive surrogate of bank (or payment system) account owned by [owner];
 * [amount] stands for active balance or available credit limit.
 */
data class AccountState(
        val owner: Party,
        // TODO replace Int with something more realistic
        val amount: Int,
        override val participants: List<AbstractParty>,
        override val linearId: UniqueIdentifier = UniqueIdentifier(AccountState::class.java.simpleName)
) : LinearState {
    fun contractClassName(): ContractClassName = AccountContract::class.java.canonicalName
}

class AccountContract : Contract {

    override fun verify(tx: LedgerTransaction) {

        // TODO
        val command = tx.commands.single()
        when (command.value) {
            is MockInitAccountCommand -> {
                // no inputs
                // single output
                // credit limit is positive
                // reserved amounts list is empty
                // signers are owner and operator
            }
            is GuaranteePaymentsCommand -> {
                // single input: AccountState
                // single output of AccountState type
                // correct number of PaymentGuaranteeState outputs
                //
            }
            is RevokePaymentGuaranteesCommand -> {
                //
            }
            else -> throw IllegalArgumentException("Unrecognized command: ${command.value::class.java.canonicalName}")
        }
    }

}

// RESERVE /////////////////////////////////////////////////////////////////////

/**
 * TODO
 */
data class ReserveState(
        val account: UniqueIdentifier,
        // TODO replace Int with something more realistic
        val amount: Int,
        override val participants: List<AbstractParty>,
        override val linearId: UniqueIdentifier = UniqueIdentifier(ReserveState::class.java.simpleName)
) : LinearState {
    fun contractClassName() = ReserveStateContract::class.java.canonicalName
}

class ReserveStateContract : Contract {

    override fun verify(tx: LedgerTransaction) {
        // TODO
    }
}

// PAYMENT GUARANTEE ///////////////////////////////////////////////////////////

/**
 * TODO
 */
data class PaymentGuaranteeState(
        val requester: Party,
        val guarantor: Party,
        // TODO replace Int with something more realistic
        val amount: Int,
        val reserveId: UniqueIdentifier,
        override val linearId: UniqueIdentifier = UniqueIdentifier(PaymentGuaranteeState::class.java.simpleName)
) : LinearState {
    override val participants = listOf(requester)
    fun contractClassName(): ContractClassName = PaymentGuaranteeContract::class.java.canonicalName
}

class PaymentGuaranteeContract : Contract {

    override fun verify(tx: LedgerTransaction) {
        // TODO
    }
}

// PAYMENT OBLIGATION //////////////////////////////////////////////////////////

/**
 * TODO
 * N.B.
 */
data class PaymentObligationState(
        val payer: Party,
        val guarantor: Party,
        val reserveId: UniqueIdentifier,
        val beneficiary: Party,
        val commitmentId: UniqueIdentifier,
        override val linearId: UniqueIdentifier = UniqueIdentifier(PaymentObligationState::class.java.simpleName)
) : LinearState {
    // TODO clarify
    override val participants = listOf(payer, beneficiary)
    fun contractClassName() = PaymentObligationContract::class.java.canonicalName
}

class PaymentObligationContract : Contract {

    override fun verify(tx: LedgerTransaction) {
        // TODO
    }
}

// PAYMENT ORDER ///////////////////////////////////////////////////////////////

// TODO
