package com.luxoft.poc.mobi.service

import com.luxoft.poc.mobi.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.NetworkMapCache
import java.lang.Math.abs
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.logging.Logger

/**
 *
 */
abstract class ProvisionOfferDS(
        val properties: Properties,
        val services: ServiceHub
) {
    abstract fun composeOffers(requestedCommitment: ProvisionCommitment<ProvisionDetails>): List<ProvisionOffer>
}
