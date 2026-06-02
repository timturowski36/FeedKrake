package de.noonoo.core.domain.port.input

interface FetchPubgDataUseCase {
    suspend fun fetchAndStore(playerNames: List<String>, platform: String, accountIds: List<String> = emptyList())
}
