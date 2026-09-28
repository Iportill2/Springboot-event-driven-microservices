package com.ecommerce.user.repository;

import com.ecommerce.user.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByVerificationTokenHash(String verificationTokenHash);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}