package com.luxoft.poc.mobi.service

import com.luxoft.poc.mobi.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.NetworkMapCache
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.logging.Logger

/**
 * TODO
 */
abstract class MockTripProvisionDS(
        properties: Properties,
        services: ServiceHub
) : ProvisionOfferDS(properties, services) {

    val LOGGER = Logger.getGlobal()

    // we offer 5% discount for each guaranteed payment
    val guaranteeDiscount = 5
    // we offer 10% discount for each pre-payment
    val prepaymentDiscount = 10
    // we offer 20% discount for each non-refundable pre-payment
    val nonRefundablePrepaymentDiscount = 20
    // trusted guarantors for guaranteed payment commitments
    val trustedGuarantors = listTrustedGuarantors(properties, services.networkMapCache)


    abstract val myTransportType: TransportType
    abstract fun initSchedule(): Schedules.Schedule

    private val schedule by lazy { initSchedule() }

    override fun composeOffers(requestedCommitment: ProvisionCommitment<ProvisionDetails>): List<ProvisionOffer> {
        if (requestedCommitment.details !is TripProvisionDetails)
            return listOf()

        val requestedDetails = requestedCommitment.details as TripProvisionDetails
        if (requestedDetails.transportType != myTransportType)
            return listOf()

        val provisionCommitments = schedule.departuresAndArrivals(requestedDetails)
                .map {
                    ProvisionCommitment(
                            performer = requestedCommitment.performer,
                            acceptor = requestedCommitment.acceptor,
                            details = TripProvisionDetails(
                                    from = requestedDetails.from,
                                    to = requestedDetails.to,
                                    departAfter = it.first,
                                    arriveBefore = it.second,
                                    transportType = myTransportType
                            ))
                }

        val result = mutableListOf<ProvisionOffer>()
        val validAfter = LocalDateTime.now()
        val validBefore = validAfter.plusMinutes(properties.getProperty("OfferValidityPeriod", "1800").toLong())
        provisionCommitments.forEach {
            val basePrice = Schedules.tripPrice(it.details)
            result.add(offer_100nonRefundable_Provision(it, validAfter, validBefore, basePrice))
            // offers with guaranteed payments make sense if some trusted guarantors are specified
            if (trustedGuarantors.isNotEmpty()) {
                result.add(offer_50refundable_Provision_50guaranteed(it, validAfter, validBefore, basePrice))
                result.add(offer_33nonRefundable_33refundable_Provision_33guaranteed(it, validAfter, validBefore, basePrice))
            }
        }

        return result
    }

    /**
     * 100% non-refundable prepayment
     */
    private fun offer_100nonRefundable_Provision(
            provisionCommitment: ProvisionCommitment<TripProvisionDetails>,
            validAfter: LocalDateTime,
            validBefore: LocalDateTime,
            basePrice: Int
    ) = ProvisionOffer(
            offeror = provisionCommitment.performer,
            offeree = provisionCommitment.acceptor,
            validAfter = validAfter,
            validBefore = validBefore,
            commitments = listOf(
                    PaymentCommitment(
                            performer = provisionCommitment.acceptor,
                            acceptor = provisionCommitment.performer,
                            // pre-payment is NOT refundable
                            details = PrePaymentDetails(
                                    payBefore = validBefore
                            ),
                            amount = ((100 - nonRefundablePrepaymentDiscount) * basePrice) / 100),
                    provisionCommitment
            )
    )

    /**
     * 50% refundable prepayment and 50% guaranteed post-payment
     */
    private fun offer_50refundable_Provision_50guaranteed(
            provisionCommitment: ProvisionCommitment<TripProvisionDetails>,
            validAfter: LocalDateTime,
            validBefore: LocalDateTime,
            basePrice: Int
    ) = ProvisionOffer(
            offeror = provisionCommitment.performer,
            offeree = provisionCommitment.acceptor,
            validAfter = validAfter,
            validBefore = validBefore,
            commitments = listOf(
                    PaymentCommitment(
                            performer = provisionCommitment.acceptor,
                            acceptor = provisionCommitment.performer,
                            // is refundable before departure
                            details = PrePaymentDetails(
                                    payBefore = validBefore,
                                    refundableBefore = provisionCommitment.details.departAfter
                            ),
                            amount = ((100 - prepaymentDiscount) * basePrice) / 200),
                    provisionCommitment,
                    PaymentCommitment(
                            performer = provisionCommitment.acceptor,
                            acceptor = provisionCommitment.performer,
                            details = PostPaymentDetails(
                                    payBefore = provisionCommitment.details.arriveBefore.plusMinutes(30),
                                    trustedGuarantors = trustedGuarantors
                            ),
                            amount = ((100 - guaranteeDiscount) * basePrice) / 200)
            )
    )

    /**
     * 33.3% non-refundable prepayment, 33.3% refundable prepayment and 33.3% guaranteed post-payment
     */
    private fun offer_33nonRefundable_33refundable_Provision_33guaranteed(
            provisionCommitment: ProvisionCommitment<TripProvisionDetails>,
            validAfter: LocalDateTime,
            validBefore: LocalDateTime,
            basePrice: Int
    ) = ProvisionOffer(
            offeror = provisionCommitment.performer,
            offeree = provisionCommitment.acceptor,
            validAfter = validAfter,
            validBefore = validBefore,
            commitments = listOf(
                    PaymentCommitment(
                            performer = provisionCommitment.acceptor,
                            acceptor = provisionCommitment.performer,
                            // pre-payment is NOT refundable
                            details = PrePaymentDetails(
                                    payBefore = validBefore
                            ),
                            amount = ((100 - nonRefundablePrepaymentDiscount) * basePrice) / 300),
                    PaymentCommitment(
                            performer = provisionCommitment.acceptor,
                            acceptor = provisionCommitment.performer,
                            // pre-payment is refundable before departure
                            details = PrePaymentDetails(
                                    payBefore = validBefore,
                                    refundableBefore = provisionCommitment.details.departAfter
                            ),
                            amount = ((100 - prepaymentDiscount) * basePrice) / 300),
                    provisionCommitment,
                    PaymentCommitment(
                            performer = provisionCommitment.acceptor,
                            acceptor = provisionCommitment.performer,
                            details = PostPaymentDetails(
                                    payBefore = provisionCommitment.details.arriveBefore.plusMinutes(30),
                                    trustedGuarantors = trustedGuarantors
                            ),
                            amount = ((100 - guaranteeDiscount) * basePrice) / 300)
            )
    )

    private fun listTrustedGuarantors(properties: Properties, networkMap: NetworkMapCache): Set<Party> {
        val result = mutableSetOf<Party>()
        val joinedNames = properties.getProperty("TrustedGuarantors", "")
        if (joinedNames.isNotEmpty()) {
            val names = joinedNames.split("\n")
            for (name in names) {
                try {
                    val x500name = CordaX500Name.parse(name)
                    // find corresponding node
                    val nodeInfo = networkMap.getNodeByLegalName(x500name)
                    if (nodeInfo != null) {
                        if (!result.add(nodeInfo.identityFromX500Name(x500name))) {
                            LOGGER.warning("Duplicated trusted guarantor... ignoring: ${x500name}")
                        }
                    } else {
                        LOGGER.warning("Cannot find node of trusted guarantor... ignoring: ${x500name}")
                    }

                } catch (e: IllegalArgumentException) {
                    LOGGER.warning("Something went wrong: ${e.message}")
                }
            }
        }
        return result
    }
}

/**
 * There are 3 daily flights: 8:00, 14:00 and 20:00
 */
class MockAirlineProvisionDS(
        properties: Properties,
        services: ServiceHub
) : MockTripProvisionDS(properties, services) {
    override val myTransportType = TransportType.AIRLINER
    override fun initSchedule() = Schedules.StrictSchedule(myTransportType, listOf(8, 14, 20))
}

/**
 * Rented bike may be taken / returned from 9:00 to 21:00
 */
class MockBikeProvisionDS(
        properties: Properties,
        services: ServiceHub
) : MockTripProvisionDS(properties, services) {
    override val myTransportType = TransportType.BIKE
    override fun initSchedule() = Schedules.BusinessHoursSchedule(myTransportType, openAt = 9, closeAt = 21)
}

/**
 * Rented car may be taken / returned from 8:00 to 22:00
 */
class MockCarProvisionDS(
        properties: Properties,
        services: ServiceHub
) : MockTripProvisionDS(properties, services) {
    override val myTransportType = TransportType.CAR
    override fun initSchedule() = Schedules.BusinessHoursSchedule(myTransportType, openAt = 8, closeAt = 22)
}

/**
 * There are two daily ferries: at 10:00 and 22:00
 */
class MockFerryProvisionDS(
        properties: Properties,
        services: ServiceHub
) : MockTripProvisionDS(properties, services) {
    override val myTransportType = TransportType.FERRY
    override fun initSchedule() = Schedules.StrictSchedule(myTransportType, listOf(10, 22))
}

/**
 * Taxi is available 24 * 365
 */
class MockTaxiProvisionDS(
        properties: Properties,
        services: ServiceHub
) : MockTripProvisionDS(properties, services) {
    override val myTransportType = TransportType.TAXI
    override fun initSchedule() = Schedules.FreeSchedule(TransportType.TAXI)
}

/**
 * There are 4 daily trains: at 7:00, 11:00, 15:00 and 19:00
 */
class MockTrainProvisionDS(
        properties: Properties,
        services: ServiceHub
) : MockTripProvisionDS(properties, services) {
    override val myTransportType = TransportType.TRAIN
    override fun initSchedule() = Schedules.StrictSchedule(TransportType.TRAIN, listOf(7, 11, 15, 19))
}

// SCHEDULES ///////////////////////////////////////////////////////////////////

object Schedules {

    /**
     * Semi-correct for Baltic region
     */
    fun tripDistance(from: GeoPoint, to: GeoPoint) =
            (55.5f* Math.abs(to.lng - from.lng) + 111.0f* Math.abs(to.lat - from.lat)).toInt()

    fun tripDistance(details: TripProvisionDetails) =
            (55.5f* Math.abs(details.to.lng - details.from.lng) + 111.0f* Math.abs(details.to.lat - details.from.lat)).toInt()

    fun tripDuration(from: GeoPoint, to: GeoPoint, transportType: TransportType) =
            1 + tripDistance(from, to) / when (transportType) {
                TransportType.AIRLINER -> 750
                TransportType.BIKE -> 12
                TransportType.CAR -> 50
                TransportType.FERRY -> 25
                TransportType.TAXI -> 40
                TransportType.TRAIN -> 60
                else -> throw IllegalArgumentException("Unsupported transport type: ${transportType}");
            }

    fun tripDuration(details: TripProvisionDetails) =
            1 + tripDistance(details) / when (details.transportType) {
                TransportType.AIRLINER -> 750
                TransportType.BIKE -> 12
                TransportType.CAR -> 50
                TransportType.FERRY -> 25
                TransportType.TAXI -> 40
                TransportType.TRAIN -> 60
                else -> throw IllegalArgumentException("Unsupported transport type: ${details.transportType}");
            }

    fun tripPrice(details: TripProvisionDetails) =
            1 + tripDistance(details) * when (details.transportType) {
                TransportType.AIRLINER -> 3
                TransportType.BIKE -> 1
                TransportType.CAR -> 8
                TransportType.FERRY -> 4
                TransportType.TAXI -> 12
                TransportType.TRAIN -> 3
                else -> throw IllegalArgumentException("Unsupported transport type: ${details.transportType}")
            }

    /**
     * Provides (possibly empty) list
     */
    abstract class Schedule(val transportType: TransportType) {
        /**
         * Returns (possibly empty) list of [Pair]-s (departure time, arrival time) matching
         * given [TripProvisionDetails.departAfter] and [TripProvisionDetails.arriveBefore]
         */
        abstract fun departuresAndArrivals(trip: TripProvisionDetails): List<Pair<LocalDateTime, LocalDateTime>>
    }

    /**
     * The service is available 24*365 (e.g. taxi)
     */
    class FreeSchedule(transportType: TransportType) : Schedule(transportType) {
        /**
         * Offers up to 2 "marginal" solutions:
         * * departure in time -- early arrival;
         * * later departure -- arrival in time
         */
        override fun departuresAndArrivals(trip: TripProvisionDetails): List<Pair<LocalDateTime, LocalDateTime>> {
            val requestedDuration = ChronoUnit.HOURS.between(trip.departAfter, trip.arriveBefore).toInt()
            val calculatedDuration = tripDuration(trip.from, trip.to, trip.transportType)
            if (requestedDuration < calculatedDuration)
                return listOf()
            if (requestedDuration == calculatedDuration)
                return listOf(Pair(trip.departAfter, trip.arriveBefore))
            return listOf(
                    Pair(trip.departAfter, trip.departAfter.plusHours(calculatedDuration.toLong())),
                    Pair(trip.arriveBefore.minusHours(calculatedDuration.toLong()), trip.arriveBefore))
        }
    }

    /**
     * The service is strictly scheduled (e.g. airplane, train, ferry, etc.)
     */
    class StrictSchedule(transportType: TransportType, val hours: List<Int>) : Schedule(transportType) {
        /**
         * Offers all available trips within requested time frame.
         */
        override fun departuresAndArrivals(trip: TripProvisionDetails): List<Pair<LocalDateTime, LocalDateTime>> {
            val result = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
            val calculatedDuration = tripDuration(trip.from, trip.to, trip.transportType)
            var xtraDays = 0;
            var done = false
            while (!done) {
                for (hour in hours) {
                    if (hour > trip.departAfter.hour || xtraDays > 0) {
                        val departure = trip.departAfter
                                .withHour(hour).withMinute(0).withSecond(0).withNano(0)
                                .plusDays(xtraDays.toLong())
                        val arrival = departure.plusHours(calculatedDuration.toLong())
                        if (arrival.isAfter(trip.arriveBefore)) {
                            done = true
                            break
                        }
                        result.add(Pair(departure, arrival))
                    }
                }
                ++xtraDays
            }
            return result
        }
    }

    /**
     * The service is available only during business hours (e.g. car or bike rental)
     */
    class BusinessHoursSchedule(transportType: TransportType, val openAt: Int, val closeAt: Int) : Schedule(transportType) {
        /**
         * Tries to offer "optimal" departures and arrivals coordinated with the provider's business hours.
         */
        override fun departuresAndArrivals(trip: TripProvisionDetails): List<Pair<LocalDateTime, LocalDateTime>> {
            val adjustedDeparture =
                    if (trip.departAfter.hour < openAt)
                    // office is not open yet - wait for business hours
                        trip.departAfter.withHour(openAt).withMinute(0).withSecond(0).withNano(0)
                    else if (trip.departAfter.hour >= closeAt)
                    // office already closed - wait for tomorrow morning
                        trip.departAfter.withHour(openAt).withMinute(0).withSecond(0).withNano(0).plusDays(1)
                    else
                        trip.departAfter
            val adjustedArrival =
                    if (trip.arriveBefore.hour >= closeAt)
                    // office will be closed, plan to arrive earlier
                        trip.arriveBefore.withHour(closeAt).withMinute(0).withSecond(0).withNano(0)
                    else if (trip.arriveBefore.hour < openAt)
                    // office is not open yet, plan to arrive in the previous evening
                        trip.arriveBefore.withHour(closeAt).withMinute(0).withSecond(0).withNano(0).minusDays(1)
                    else
                        trip.arriveBefore
            val adjustedDuration = ChronoUnit.HOURS.between(adjustedDeparture, adjustedArrival).toInt()
            val calculatedDuration = tripDuration(trip.from, trip.to, trip.transportType)
            if (adjustedDuration < calculatedDuration)
                return listOf()
            if (adjustedDuration == calculatedDuration)
                return listOf(Pair(adjustedDeparture, adjustedArrival))
            return optimizeWithBusinessHours(adjustedDeparture, adjustedArrival, calculatedDuration)
        }

        /**
         * Brute force "optimization":
         * * we must depart during business hours
         * * we want to arrive during business hours or at least to minimize waiting time
         */
        private fun optimizeWithBusinessHours(
                adjustedDeparture: LocalDateTime,
                adjustedArrival: LocalDateTime,
                calculatedDuration: Int
        ): List<Pair<LocalDateTime, LocalDateTime>> {
            var departure = adjustedDeparture
            var arrival = adjustedDeparture.plusHours(calculatedDuration.toLong())
            val byWaitingTime = mutableMapOf<Int, MutableList<Pair<LocalDateTime, LocalDateTime>>>()
            while(!arrival.isAfter(adjustedArrival)) {
                if (departure.hour in openAt .. closeAt) {
                    val waitingTime =
                    // we arrive too early and will be waiting ...
                            if (arrival.hour < openAt) openAt - arrival.hour
                            // we arrive too later and will be waiting till tomorrow ...
                            else if (arrival.hour > closeAt) 24 - arrival.hour + openAt
                            // we do not need to wait
                            else 0
                    if (byWaitingTime.containsKey(waitingTime))
                        byWaitingTime[waitingTime]!! + Pair(departure, arrival)
                    else
                        byWaitingTime[waitingTime] = mutableListOf(Pair(departure, arrival))
                }
                departure = departure.plusHours(1)
                arrival = arrival.plusHours(1)
            }
            return byWaitingTime[byWaitingTime.keys.sorted().first()]!!
        }
    }
}
