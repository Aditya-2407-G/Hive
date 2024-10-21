package org.vsarthi.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.vsarthi.backend.model.Users;

public interface UserRepository extends JpaRepository<Users, Long> {
    Users findByUsername(String username);
    Users findByEmail(String email);
    Users findByOauth2Id(String oauth2Id);
}
