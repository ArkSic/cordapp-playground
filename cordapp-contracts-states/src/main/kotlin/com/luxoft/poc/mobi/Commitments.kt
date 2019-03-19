package com.luxoft.poc.mobi

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction

/**
 * Abstraction of some unilateral commitmentId: a [performer] promises something to an [acceptor]. That "something"may be:
 * * to provide some commodities and/or services -- see [ProvisionCommitment];
 * * to pay some amount of money -- see [PaymentCommitment];
 * * to perform some action -- see [ActionCommitment];
 * * etc.
 * The subject matter of the commitmentId depends on concrete descendant class.
 * The terms and the conditions are covered by [details] property.
 */
@CordaSerializable
interface Commitment<T: CommitmentDetails> {
    val performer: Party
    val acceptor: Party
    val details: T
}

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
 * Some provision commitmentId: the provider acts as a [performer] and the consumer acts as an [acceptor].
 * [details] describe subject of the provision, terms and conditions, etc.
 */
@CordaSerializable
data class ProvisionCommitment<T: ProvisionDetails>(
        override val performer: Party,
        override val acceptor: Party,
        override val details: T
) : Commitment<T>

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
 * Some action commitmentId: the active party acts as a [performer] and the passive party acts as an [acceptor].
 * [details] describe the actions to be performed.
 */
data class ActionCommitment<T: ActionDetails>(
        override val performer: Party,
        override val acceptor: Party,
        override val details: T
) : Commitment<T>

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

