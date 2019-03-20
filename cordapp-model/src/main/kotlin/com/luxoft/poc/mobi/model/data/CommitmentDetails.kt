package com.luxoft.poc.mobi.model.data

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.LocalDateTime

// TODO ??? is there some common contract / interface ???
@CordaSerializable
interface CommitmentDetails

// PROVISION ///////////////////////////////////////////////////////////////////

/**
 * Provision details and conditions
 * TODO: now acts MOSTLY as an indicator interface
 * TODO ??? extend the contract ???
 */
@CordaSerializable
interface ProvisionDetails : CommitmentDetails {
    // TODO: clarify -- e.g. connected flights
    fun isCoveredBy(others: List<ProvisionDetails>) : Boolean
}

// TRIP PROVISION DETAILS //////////////////////////////////////////////////////

@CordaSerializable
data class GeoPoint(val lat: Float, val lng: Float)

@CordaSerializable
enum class TransportType { AIRLINER, BIKE, CAR, FERRY, TAXI, TRAIN }

/**
 * Trip provision details
 */
@CordaSerializable
data class TripProvisionDetails(
        val from: GeoPoint,
        val to: GeoPoint,
        val departAfter: LocalDateTime,
        val arriveBefore: LocalDateTime,
        val transportType: TransportType
) : ProvisionDetails {

    /**
     * TODO
     */
    override fun isCoveredBy(others: List<ProvisionDetails>): Boolean {
        var result = others.isNotEmpty()
        others.forEach {
            result = result
                    && it is TripProvisionDetails
                    && it.transportType == transportType
        }
        if (result) {
            val first = others.first() as TripProvisionDetails
            val last = others.last() as TripProvisionDetails
            result = result
                    && from == first.from
                    && to == last.to
                    // we depart in time or later
                    && !departAfter.isAfter(first.departAfter)
                    // we arrive in time or earlier
                    && !arriveBefore.isBefore(last.arriveBefore)
        }
        return result
    }
}

// PAYMENT /////////////////////////////////////////////////////////////////////

/**
 * Payment details and conditions
 */
@CordaSerializable
interface PaymentDetails : CommitmentDetails {
    val payBefore: LocalDateTime
}

/**
 * Terms and conditions applicable to payments performed prior to the service provision
 */
@CordaSerializable
data class PrePaymentDetails(
        override val payBefore: LocalDateTime,
        val refundableBefore: LocalDateTime? = null
) : PaymentDetails

/**
 * Terms and conditions applicable to payments performed after the service provision
 */
@CordaSerializable
data class PostPaymentDetails(
        override val payBefore: LocalDateTime,
        val trustedGuarantors: Set<Party> = setOf()
) : PaymentDetails

// ACTION //////////////////////////////////////////////////////////////////////

/**
 * Action details and conditions
 * TODO: now acts as an indicator interface
 * TODO ??? define some useful contract ???
 */
@CordaSerializable
interface ActionDetails : CommitmentDetails

/**
 * Dummy action details -- intended for testing ONLY
 */
@CordaSerializable
data class DummyActionDetails(
        val details: String = "Dummy action details"
) : ActionDetails