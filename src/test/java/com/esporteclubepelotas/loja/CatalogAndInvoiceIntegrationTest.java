package com.esporteclubepelotas.loja;

import com.esporteclubepelotas.loja.modules.auth.dto.LoginRequestDto;
import com.esporteclubepelotas.loja.modules.auth.dto.PortalUserSyncDto;
import com.esporteclubepelotas.loja.modules.auth.service.PortalIntegrationClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogAndInvoiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PortalIntegrationClient portalIntegrationClient;

    private String loginWithRole(String portalUserId, String cpf, String role) throws Exception {
        when(portalIntegrationClient.authenticateWithPortal(any(LoginRequestDto.class)))
                .thenReturn(PortalUserSyncDto.builder()
                        .portalUserId(portalUserId)
                        .fullName("Operador " + role)
                        .email(role.toLowerCase() + "@ecpelotas.com.br")
                        .cpf(cpf)
                        .role(role)
                        .socioActive(true)
                        .build());

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"" + cpf + "\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return "Bearer " + root.get("accessToken").asText();
    }

    @Test
    @DisplayName("Deve autenticar via Portal S2S, bloquear USER comum no /admin e executar ciclo completo de Catálogo + XML NF-e + Kardex")
    void shouldCompleteCatalogNfeAndKardexFlow() throws Exception {
        String userToken = loginWithRole("portal-user-1", "11122233344", "USER");
        String ownerToken = loginWithRole("portal-owner-1", "00000000001", "OWNER");

        // USER comum não acessa painel admin de estoque/produtos
        mockMvc.perform(get("/admin/products")
                        .header("Authorization", userToken))
                .andExpect(status().isForbidden());

        // OWNER cria produto inicial com 1 variação
        String createProdPayload = """
                {
                  "name": "Camisa Oficial Pelotas 2026 Titular",
                  "description": "Manto aureo-ceruleo oficial de jogo",
                  "featured": true,
                  "variants": [
                    {
                      "sku": "ECP-2026-TIT-G",
                      "eanBarcode": "7891908000011",
                      "sizeLabel": "G",
                      "colorLabel": "Aureo-Ceruleo",
                      "costPrice": 120.00,
                      "salePrice": 249.90,
                      "memberPrice": 219.90,
                      "minStock": 3
                    }
                  ]
                }
                """;

        MvcResult prodResult = mockMvc.perform(post("/admin/products")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProdPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.variants[0].sku").value("ECP-2026-TIT-G"))
                .andReturn();

        String existingVariantId = objectMapper.readTree(prodResult.getResponse().getContentAsString())
                .get("variants").get(0).get("id").asText();

        // Vitrine pública não expõe costPrice
        mockMvc.perform(get("/public/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Camisa Oficial Pelotas 2026 Titular"))
                .andExpect(jsonPath("$.data[0].variants[0].costPrice").doesNotExist());

        // Importa XML de NF-e com 2 itens: item 1 casa automaticamente pelo EAN, item 2 é produto novo
        String sampleXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <nfeProc xmlns="http://www.portalfiscal.inf.br/nfe" versao="4.00">
                  <NFe>
                    <infNFe Id="NFe43261192242726000159550010000012341000012345">
                      <ide><nNF>1234</nNF><serie>1</serie></ide>
                      <emit><CNPJ>12345678000199</CNPJ><xNome>Fornecedor Material Esportivo LTDA</xNome></emit>
                      <det nItem="1">
                        <prod>
                          <cProd>FORN-CAM-G</cProd>
                          <cEAN>7891908000011</cEAN>
                          <xProd>Camisa Titular 2026 Tamanho G</xProd>
                          <NCM>61091000</NCM>
                          <qCom>10.0000</qCom>
                          <vUnCom>115.00</vUnCom>
                          <vProd>1150.00</vProd>
                        </prod>
                      </det>
                      <det nItem="2">
                        <prod>
                          <cProd>FORN-BONE-01</cProd>
                          <cEAN>SEM GTIN</cEAN>
                          <xProd>Bone Alcateia Aureo</xProd>
                          <NCM>65050010</NCM>
                          <qCom>5</qCom>
                          <vUnCom>35.00</vUnCom>
                          <vProd>175.00</vProd>
                        </prod>
                      </det>
                    </infNFe>
                  </NFe>
                </nfeProc>
                """;

        MockMultipartFile xmlFile = new MockMultipartFile(
                "file",
                "nfe-1234.xml",
                "application/xml",
                sampleXml.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult importRes = mockMvc.perform(multipart("/admin/invoices/import-xml")
                        .file(xmlFile)
                        .header("Authorization", ownerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.invoiceNumber").value("1234"))
                .andExpect(jsonPath("$.items[0].linkedVariantId").value(existingVariantId))
                .andExpect(jsonPath("$.allItemsLinked").value(false))
                .andReturn();

        JsonNode docNode = objectMapper.readTree(importRes.getResponse().getContentAsString());
        String docId = docNode.get("id").asText();
        String unlinkedItemId = docNode.get("items").get(1).get("id").asText();

        // Confirmar nota antes de vincular todos os itens deve falhar (400)
        mockMvc.perform(post("/admin/invoices/" + docId + "/confirm")
                        .header("Authorization", ownerToken))
                .andExpect(status().isBadRequest());

        // Cadastra novo produto diretamente a partir do item 2 da NF-e
        String createFromInvoicePayload = """
                {
                  "productName": "Bone Oficial Alcateia Aureo",
                  "sku": "ECP-BONE-AUR-UN",
                  "sizeLabel": "Unico",
                  "colorLabel": "Amarelo",
                  "salePrice": 89.90,
                  "memberPrice": 79.90
                }
                """;

        mockMvc.perform(post("/admin/invoices/" + docId + "/items/" + unlinkedItemId + "/create-product")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createFromInvoicePayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allItemsLinked").value(true));

        // Confirma a nota e efetiva no Kardex
        mockMvc.perform(post("/admin/invoices/" + docId + "/confirm")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"));

        // Verifica que estoque físico e Kardex foram atualizados
        mockMvc.perform(get("/admin/stock/movements")
                        .header("Authorization", ownerToken)
                        .param("variantId", existingVariantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].movementType").value("ENTRY_INVOICE"))
                .andExpect(jsonPath("$.data[0].quantityDelta").value(10))
                .andExpect(jsonPath("$.data[0].balanceAfter").value(10));

        // Ajuste manual negativo maior que estoque deve falhar (400)
        mockMvc.perform(post("/admin/stock/adjust")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + existingVariantId + "\",\"quantityDelta\":-99,\"reason\":\"Teste erro\"}"))
                .andExpect(status().isBadRequest());

        // Ajuste manual válido -2 deixa saldo = 8
        mockMvc.perform(post("/admin/stock/adjust")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + existingVariantId + "\",\"quantityDelta\":-2,\"reason\":\"Amostra vitrine\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/admin/stock/movements")
                        .header("Authorization", ownerToken)
                        .param("variantId", existingVariantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].movementType").value("MANUAL_OUT"))
                .andExpect(jsonPath("$.data[0].balanceAfter").value(8));

        // Reimportar XML com mesma chave chNFe deve ser barrado
        mockMvc.perform(multipart("/admin/invoices/import-xml")
                        .file(xmlFile)
                        .header("Authorization", ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Deve cadastrar colaborador exclusivo da Loja e autenticar localmente sem depender do Portal")
    void shouldCreateLocalStaffAndLoginWithoutPortal() throws Exception {
        String ownerToken = loginWithRole("portal-owner-staff", "00000000002", "OWNER");
        reset(portalIntegrationClient);

        String createStaffPayload = """
                {
                  "fullName": "Vendedor Balcao Local",
                  "email": "vendedor.local@alcateia.com.br",
                  "cpf": "99988877766",
                  "role": "SELLER",
                  "password": "senhaForte123"
                }
                """;

        mockMvc.perform(post("/admin/staff")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createStaffPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authSource").value("LOCAL"))
                .andExpect(jsonPath("$.role").value("SELLER"));

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpf\":\"vendedor.local@alcateia.com.br\",\"password\":\"senhaForte123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("SELLER"))
                .andExpect(jsonPath("$.user.authSource").value("LOCAL"))
                .andReturn();

        verifyNoInteractions(portalIntegrationClient);

        String sellerToken = "Bearer " + objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // SELLER acessa catálogo, mas não acessa notas NF-e nem gestão de equipe
        mockMvc.perform(get("/admin/products").header("Authorization", sellerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/invoices").header("Authorization", sellerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/staff").header("Authorization", sellerToken))
                .andExpect(status().isForbidden());
    }
}
