package com.luxoft.poc.mobi.model.data

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

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
 * Some action commitmentId: the active party acts as a [performer] and the passive party acts as an [acceptor].
 * [details] describe the actions to be performed.
 */
data class ActionCommitment<T: ActionDetails>(
        override val performer: Party,
        override val acceptor: Party,
        override val details: T
) : Commitment<T>

