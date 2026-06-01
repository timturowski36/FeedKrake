package de.noonoo.domain.port.input

interface FetchPubgDataUseCase {
    suspend fun fetchAndStore(playerNames: List<String>, platform: String, accountIds: List<String> = emptyList())
}
