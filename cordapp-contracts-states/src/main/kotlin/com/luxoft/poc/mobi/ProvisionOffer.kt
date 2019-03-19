package com.luxoft.poc.mobi

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException
import java.time.LocalDateTime

/**
 * Provision offer -- ordered list of unilateral commitments: e.g. "you pay an advance, we provide some stuff,
 * you perform something with that stuff, you pay the rest, we perform some final action"
 *
 * TODO ??? move to separate package/module accessible to cordapp, backend and (may be) frontend
 */
@CordaSerializable
data class ProvisionOffer(
        val offeror: Party,
        val offeree: Party,
        val validAfter: LocalDateTime,
        val validBefore: LocalDateTime,
        val commitments: List<Commitment<*>>
        // TODO ??? val provisionGuarantees: List<ProvisionGuarantee> = listOf()
)
