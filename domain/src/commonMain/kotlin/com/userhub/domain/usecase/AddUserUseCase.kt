package com.userhub.domain.usecase

import com.userhub.data.repository.UserRepository
import io.ktor.client.statement.HttpResponse

class AddUserUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(name: String, email: String): HttpResponse =
        repository.createUser(name, email)
}
