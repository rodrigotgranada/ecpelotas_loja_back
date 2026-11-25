package com.esporteclubepelotas.loja.modules.orders.repository;

import com.esporteclubepelotas.loja.modules.orders.entity.StoreOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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

    @Query(
        value = """
            SELECT o.id FROM orders o
            WHERE (:status IS NULL OR o.status = :status)
              AND (
                  :search IS NULL
                  OR LOWER(o.order_code) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(o.customer_name) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(COALESCE(o.customer_cpf, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(COALESCE(o.customer_whatsapp, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """,
        countQuery = """
            SELECT COUNT(*) FROM orders o
            WHERE (:status IS NULL OR o.status = :status)
              AND (
                  :search IS NULL
                  OR LOWER(o.order_code) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(o.customer_name) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(COALESCE(o.customer_cpf, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                  OR LOWER(COALESCE(o.customer_whatsapp, '')) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """,
        nativeQuery = true
    )
    Page<String> findAdminOrderIds(
            @Param("status") String status,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"items"})
    List<StoreOrder> findAllByIdIn(Collection<String> ids);

    @EntityGraph(attributePaths = {"items"})
    List<StoreOrder> findByStatusAndExpiresAtBefore(String status, Instant expiresAt);

    boolean existsByOrderCode(String orderCode);
}
