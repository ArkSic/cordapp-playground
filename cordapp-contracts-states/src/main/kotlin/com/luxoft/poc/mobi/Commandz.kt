package com.luxoft.poc.mobi

import com.luxoft.poc.mobi.model.data.ProvisionOffer
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party

/**
 * TODO
 */
class MockInitAccountCommand(
        val owner: Party,
        val operator: Party,
        // TODO: replace with something more realistic
        val initialAmount: Int
) : CommandData

/**
 * TODO
 * The [requester] (account owner) asks the [guarantor] (payment operator) to guarantee that the account owner is
 * solvent within the total sum of specified [amounts]. The guarantor "freezes" the given [amounts] at [account]
 * to secure provided guarantees.
 * * Signers: [requester] (initial) and [guarantor].
 * * See: [GuaranteePaymentsFlow].
 */
class GuaranteePaymentsCommand(
//        val requester: Party,
//        val guarantor: Party,
        val account: UniqueIdentifier,
        // TODO: replace with something more realistic
        val amounts: List<Int>
) : CommandData

/**
 * TODO
 * The account owner asks the pay
 */
class RevokePaymentGuaranteesCommand : CommandData

/**
 * TODO
 * The [offer.offeree] asks the [offer.offeror] to confirm that the given [ProvisionOffer] has been originated by the
 * given offeror, addressed to the given offeree and has not been expired yet.
 * * Signers: [offer.offree] (initial)  and [offer.offeror]
 * * See [ConfirmOfferFlow].
 */
class ConfirmOfferCommand(
        val offer: ProvisionOffer
) : CommandData


/**
 * TODO
 * The [offer.offeree] accepts the given [offer] and provides required payment [guarantees] (if any).
 * * Signers: [offer.offeree] (initial) and [offer.offeror]
 * * See [AcceptOfferFlow].
 */
class AcceptOfferCommand(
        val offer: ProvisionOffer,
        val guarantees: List<UniqueIdentifier>
) : CommandData