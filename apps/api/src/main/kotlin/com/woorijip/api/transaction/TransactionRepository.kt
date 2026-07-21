package com.woorijip.api.transaction

import org.springframework.data.repository.CrudRepository

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
}
