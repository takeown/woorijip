package com.woorijip.api.identity

import org.springframework.data.repository.CrudRepository

interface AppUserRepository : CrudRepository<AppUser, Long>
