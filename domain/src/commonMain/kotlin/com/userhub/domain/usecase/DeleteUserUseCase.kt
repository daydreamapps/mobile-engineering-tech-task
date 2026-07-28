package com.userhub.domain.usecase

import com.userhub.data.repository.UserRepository
import io.ktor.client.statement.HttpResponse

class DeleteUserUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(id: Long): HttpResponse =
        repository.deleteUser(id)
}
