package com.example.tiendamascotas.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun observeUserDirectory(excludeUid: String): Flow<List<UserPublic>>
    fun observeUserPublic(uid: String): Flow<UserPublic?>
    fun searchUsersPrefix(queryLower: String, limit: Long = 50): Flow<List<UserPublic>>
}
