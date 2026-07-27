package com.woorijip.api.transaction

import org.springframework.data.repository.CrudRepository
import java.time.OffsetDateTime

interface TransactionRepository : CrudRepository<Transaction, Long> {
    fun findAllByHouseholdIdOrderByOccurredAtDescIdDesc(householdId: Long): List<Transaction>

    fun findAllByHouseholdIdAndPayerIdOrderByOccurredAtDescIdDesc(
        householdId: Long,
        payerId: Long,
    ): List<Transaction>

    fun findAllByHouseholdIdAndPayerIdNotOrderByOccurredAtDescIdDesc(
        householdId: Long,
        payerId: Long,
    ): List<Transaction>

    fun findAllByHouseholdIdAndPayerIdAndCardIssuerAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
        householdId: Long,
        payerId: Long,
        cardIssuer: CardIssuer,
        occurredAtFrom: OffsetDateTime,
        occurredAtTo: OffsetDateTime,
    ): List<Transaction>
}
