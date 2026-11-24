package com.esporteclubepelotas.loja.modules.auth.repository;

import com.esporteclubepelotas.loja.modules.auth.entity.StoreUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreUserRepository extends JpaRepository<StoreUser, String> {
    Optional<StoreUser> findByPortalUserId(String portalUserId);
    Optional<StoreUser> findByEmailIgnoreCase(String email);
    Optional<StoreUser> findByCpf(String cpf);
    List<StoreUser> findAllByOrderByCreatedAtDesc();
}
