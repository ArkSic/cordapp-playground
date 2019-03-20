package com.luxoft.poc.mobi.model.service

import com.luxoft.poc.mobi.model.data.ProvisionCommitment
import com.luxoft.poc.mobi.model.data.ProvisionDetails
import com.luxoft.poc.mobi.model.data.ProvisionOffer
import net.corda.core.node.ServiceHub
import java.util.*

/**
 *
 */
abstract class ProvisionOfferDS(
        val properties: Properties,
        val services: ServiceHub
) {
    abstract fun composeOffers(requestedCommitment: ProvisionCommitment<ProvisionDetails>): List<ProvisionOffer>
}
