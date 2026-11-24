package com.esporteclubepelotas.loja.modules.auth.repository;

import com.esporteclubepelotas.loja.modules.auth.entity.StoreUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoreUserRepository extends JpaRepository<StoreUser, String> {
    Optional<StoreUser> findByPortalUserId(String portalUserId);
}
