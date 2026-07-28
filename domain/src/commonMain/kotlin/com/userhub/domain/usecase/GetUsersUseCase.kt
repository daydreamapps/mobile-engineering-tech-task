package com.userhub.domain.usecase

import com.userhub.data.repository.UserRepository
import com.userhub.data.repository.UsersResult

class GetUsersUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(): UsersResult = repository.getUsers()
}
