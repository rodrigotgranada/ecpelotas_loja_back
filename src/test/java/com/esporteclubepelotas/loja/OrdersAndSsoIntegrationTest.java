package com.esporteclubepelotas.loja;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.modules.auth.dto.LoginRequestDto;
import com.esporteclubepelotas.loja.modules.auth.dto.PortalUserSyncDto;
import com.esporteclubepelotas.loja.modules.auth.service.PortalIntegrationClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrdersAndSsoIntegrationTest {

    private static final String TEST_S2S_KEY = "test-store-s2s-key-1908";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PortalIntegrationClient portalIntegrationClient;

    @Test
    @DisplayName("Deve validar SSO, fallback de login offline, reserva WhatsApp com desconto Sócio e cancelamento S2S")
    void shouldHandleSsoOfflineFallbackReservationAndS2sCancel() throws Exception {
        // 1. Login Admin via Portal S2S e cria produto com 2 unidades em estoque via ajuste manual
        when(portalIntegrationClient.authenticateWithPortal(any(LoginRequestDto.class)))
                .thenReturn(PortalUserSyncDto.builder()
                        .portalUserId("portal-admin-orders")
                        .fullName("Admin Pedidos")
                        .email("admin.orders@ecpelotas.com.br")
                        .cpf("00000000099")
                        .role("ADMIN")
                        .socioActive(true)
                        .build());

        String adminLoginJson = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"00000000099\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String adminToken = "Bearer " + objectMapper.readTree(adminLoginJson).get("accessToken").asText();

        // Simula Portal offline logo após o primeiro login: admin ainda consegue autenticar via portalVerifierHash em cache!
        when(portalIntegrationClient.authenticateWithPortal(any(LoginRequestDto.class)))
                .thenThrow(new BusinessException("Portal offline", HttpStatus.SERVICE_UNAVAILABLE));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"00000000099\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.cpf").value("00000000099"));

        String prodJson = mockMvc.perform(post("/admin/products")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Camisa Goleiro 2026",
                                  "variants": [
                                    {
                                      "sku": "ECP-GOL-2026-M",
                                      "sizeLabel": "M",
                                      "costPrice": 100.00,
                                      "salePrice": 200.00,
                                      "memberPrice": 170.00
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String variantId = objectMapper.readTree(prodJson).get("variants").get(0).get("id").asText();

        mockMvc.perform(post("/admin/stock/adjust")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + variantId + "\",\"quantityDelta\":2,\"reason\":\"Carga inicial\"}"))
                .andExpect(status().isCreated());

        // 2. Torcedor Sócio entra via token SSO emitido pelo Portal
        byte[] keyHash = MessageDigest.getInstance("SHA-256").digest(TEST_S2S_KEY.getBytes(StandardCharsets.UTF_8));
        SecretKey key = Keys.hmacShaKeyFor(keyHash);
        String ssoToken = Jwts.builder()
                .subject("portal-socio-100")
                .claim("type", "STORE_SSO")
                .claim("fullName", "Torcedor Sócio")
                .claim("email", "socio100@ecpelotas.com.br")
                .claim("cpf", "12345678901")
                .claim("role", "SOCIO")
                .claim("isSocioActive", true)
                .claim("socioStatus", "ACTIVE")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        MvcResult ssoResult = mockMvc.perform(post("/auth/sso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + ssoToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.socioActive").value(true))
                .andReturn();

        String socioToken = "Bearer " + objectMapper.readTree(ssoResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        when(portalIntegrationClient.checkMemberStatus("12345678901")).thenReturn(Optional.empty());

        // 3. Reserva de 2 unidades excede? Não, exatas 2 unidades -> aplica preço sócio 170 * 2 = 340
        MvcResult reserveResult = mockMvc.perform(post("/orders/whatsapp-reservation")
                        .header("Authorization", socioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerWhatsapp\":\"53999998888\",\"items\":[{\"variantId\":\"" + variantId + "\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_WHATSAPP"))
                .andExpect(jsonPath("$.totalAmount").value(340.00))
                .andExpect(jsonPath("$.whatsappUrl").isNotEmpty())
                .andReturn();

        JsonNode orderNode = objectMapper.readTree(reserveResult.getResponse().getContentAsString());
        String cleanOrderCode = orderNode.get("orderCode").asText().replace("#", "");

        // 4. Tentar reservar mais 1 unidade (estoque disponível agora é 0 -> esgotado!)
        mockMvc.perform(post("/orders/whatsapp-reservation")
                        .header("Authorization", socioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerWhatsapp\":\"53999998888\",\"items\":[{\"variantId\":\"" + variantId + "\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());

        // 5. Consulta S2S via Portal e cancelamento S2S
        mockMvc.perform(get("/integrations/portal/orders")
                        .header("X-Internal-Api-Key", TEST_S2S_KEY)
                        .param("portalUserId", "portal-socio-100")
                        .param("cpf", "12345678901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING_WHATSAPP"));

        mockMvc.perform(post("/integrations/portal/orders/" + cleanOrderCode + "/cancel")
                        .header("X-Internal-Api-Key", TEST_S2S_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"portalUserId\":\"portal-socio-100\",\"reason\":\"Desisti no Portal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 6. Nova reserva + Negociação no PDV (2 -> 1 unidade) + Confirmação com 2 pagamentos (Dinheiro + Pix)
        MvcResult reserve2 = mockMvc.perform(post("/orders/whatsapp-reservation")
                        .header("Authorization", socioToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerWhatsapp\":\"53999998888\",\"items\":[{\"variantId\":\"" + variantId + "\",\"quantity\":2}]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String code2 = objectMapper.readTree(reserve2.getResponse().getContentAsString())
                .get("orderCode").asText().replace("#", "");

        mockMvc.perform(put("/admin/orders/" + code2)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"variantId\":\"" + variantId + "\",\"quantity\":1}],\"discountAmount\":20.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEGOTIATING"))
                .andExpect(jsonPath("$.totalAmount").value(150.00));

        // Pagamento errado (soma != 150) deve ser recusado com 400
        mockMvc.perform(post("/admin/orders/" + code2 + "/confirm")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"paymentMethod\":\"PIX\",\"amount\":100.00}]}"))
                .andExpect(status().isBadRequest());

        // Pagamento dividido R$ 50 Dinheiro + R$ 100 Pix -> baixa físico de 2 -> 1 e registra SALE_OUTBOUND no Kardex
        mockMvc.perform(post("/admin/orders/" + code2 + "/confirm")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payments\":[{\"paymentMethod\":\"CASH\",\"amount\":50.00},{\"paymentMethod\":\"PIX\",\"amount\":100.00}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED_PAID"))
                .andExpect(jsonPath("$.payments.length()").value(2));

        // 7. Venda direta no Balcão ANÔNIMA da última unidade (1 -> 0) dividida Crédito + Pix
        mockMvc.perform(post("/admin/orders/counter-sale")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [{"variantId": "%s", "quantity": 1}],
                                  "payments": [
                                    {"paymentMethod": "CREDIT", "amount": 120.00},
                                    {"paymentMethod": "PIX", "amount": 80.00}
                                  ]
                                }
                                """.formatted(variantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.channel").value("STORE_COUNTER"))
                .andExpect(jsonPath("$.customerType").value("WALK_IN_ANONYMOUS"))
                .andExpect(jsonPath("$.status").value("CONFIRMED_PAID"));

        // 8. Admin atualiza WhatsApp oficial em /admin/store-settings
        mockMvc.perform(put("/admin/store-settings")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "storeName": "Loja Oficial Alcateia",
                                  "whatsappNumber": "(53) 99123-4567",
                                  "openHour": "09:00",
                                  "closeHour": "19:00",
                                  "workingDays": "MON,TUE,WED,THU,FRI,SAT",
                                  "reservationTtlMinutes": 180,
                                  "maxOpenReservationsPerUser": 3
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsappNumber").value("53991234567"));
    }
}
