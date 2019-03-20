package com.luxoft.poc.mobi

import com.luxoft.poc.mobi.model.data.*
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

/**
 * Abstract [LinearState] wrapper around [Commitment]
 */
interface CommitmentState<T: CommitmentDetails> : LinearState {
    val commitment: Commitment<T>
    fun contractClassName(): ContractClassName
}

/**
 * General [CommitmentState] [Contract] defines common set of commands to be used within transactions involving any
 * [CommitmentState]-s. All these commands require two signers: [Commitment.performer] and [Commitment.acceptor].
 */
interface CommitmentContract : Contract {

    // TODO there may be some common part of the verification contract too
}

// PROVISION ///////////////////////////////////////////////////////////////////

/**
 * [LinearState] wrapper around [ProvisionCommitment].
 */
data class ProvisionCommitmentState<T: ProvisionDetails>(
        override val commitment: ProvisionCommitment<T>,
        override val linearId: UniqueIdentifier = UniqueIdentifier(ProvisionCommitmentState::class.java.simpleName)
) : CommitmentState<T> {
    override val participants = listOf(commitment.performer, commitment.acceptor)
    override fun contractClassName(): ContractClassName = ProvisionCommitmentContract::class.java.canonicalName
}

/**
 * TODO
 */
class ProvisionCommitmentContract : CommitmentContract {

    override fun verify(tx: LedgerTransaction) {
        // TODO !!!
    }
}

// PAYMENT /////////////////////////////////////////////////////////////////////

/**
 * Some payment commitmentId: the payer acts as a [performer] and the beneficiary acts as an [acceptor].
 * [details] describe the payment terms and conditions. [amoumt] defines exact sum to pay.
 */
data class PaymentCommitment<T: PaymentDetails>(
        override val performer: Party,
        override val acceptor: Party,
        override val details: T,
        // TODO !!! Int --> something more realistic
        val amount: Int
) : Commitment<T>

/**
 * [LinearState] wrapper around [PaymentCommitment]
 */
data class PaymentCommitmentState<T: PaymentDetails>(
        override val commitment: PaymentCommitment<T>,
        override val linearId: UniqueIdentifier = UniqueIdentifier(PaymentCommitmentState::class.java.simpleName)
) : CommitmentState<T> {
    override val participants = listOf(commitment.performer, commitment.acceptor)
    override fun contractClassName(): ContractClassName = PaymentCommitmentContract::class.java.canonicalName
}

/**
 * TODO
 */
class PaymentCommitmentContract : CommitmentContract {

    override fun verify(tx: LedgerTransaction) {
        // TODO !!!
    }
}

// ACTION //////////////////////////////////////////////////////////////////////

/**
 * [LinearState] wrapper around [ActionCommitment].
 */
data class ActionCommitmentState<T: ActionDetails>(
        override val commitment: ActionCommitment<T>,
        override val linearId: UniqueIdentifier = UniqueIdentifier(ActionCommitmentState::class.java.simpleName)
) : CommitmentState<T> {
    override val participants = listOf(commitment.performer, commitment.acceptor)
    override fun contractClassName(): ContractClassName = ActionCommitmentContract::class.java.canonicalName
}

/**
 * TODO
 */
class ActionCommitmentContract : CommitmentContract {

    override fun verify(tx: LedgerTransaction) {
        // TODO !!!
    }
}

