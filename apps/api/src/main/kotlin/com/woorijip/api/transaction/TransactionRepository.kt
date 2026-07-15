package com.woorijip.api.transaction

import org.springframework.data.repository.CrudRepository

interface TransactionRepository : CrudRepository<Transaction, Long> {
    fun findAllByOrderByOccurredAtDescIdDesc(): List<Transaction>
}
