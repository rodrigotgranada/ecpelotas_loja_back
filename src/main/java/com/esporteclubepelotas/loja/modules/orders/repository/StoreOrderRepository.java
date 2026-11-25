package com.esporteclubepelotas.loja.modules.orders.repository;

import com.esporteclubepelotas.loja.modules.orders.entity.StoreOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, String> {

    @EntityGraph(attributePaths = {"items"})
    Optional<StoreOrder> findWithItemsByOrderCode(String orderCode);

    @EntityGraph(attributePaths = {"items"})
    @Query("""
        SELECT o FROM StoreOrder o
        WHERE (:storeUserId IS NOT NULL AND o.storeUserId = :storeUserId)
           OR (:portalUserId IS NOT NULL AND o.portalUserId = :portalUserId)
           OR (:cpf IS NOT NULL AND o.customerCpf = :cpf)
        ORDER BY o.createdAt DESC
        """)
    List<StoreOrder> findCustomerOrders(
            @Param("storeUserId") String storeUserId,
            @Param("portalUserId") String portalUserId,
            @Param("cpf") String cpf
    );

    @Query("""
        SELECT COUNT(o) FROM StoreOrder o
        WHERE o.status IN ('PENDING_WHATSAPP', 'NEGOTIATING')
          AND (
              (:storeUserId IS NOT NULL AND o.storeUserId = :storeUserId)
              OR (:portalUserId IS NOT NULL AND o.portalUserId = :portalUserId)
          )
        """)
    long countActiveReservationsForUser(
            @Param("storeUserId") String storeUserId,
            @Param("portalUserId") String portalUserId
    );

    @EntityGraph(attributePaths = {"items"})
    List<StoreOrder> findByStatusAndExpiresAtBefore(String status, Instant expiresAt);

    boolean existsByOrderCode(String orderCode);

    Page<StoreOrder> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
}
