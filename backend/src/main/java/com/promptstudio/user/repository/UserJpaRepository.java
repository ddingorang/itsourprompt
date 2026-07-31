package com.promptstudio.user.repository;

import com.promptstudio.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<User, Long>, UserRepository {
}
