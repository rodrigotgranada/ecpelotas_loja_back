package com.esporteclubepelotas.loja.modules.auth.service;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.common.exception.UnauthorizedException;
import com.esporteclubepelotas.loja.modules.auth.dto.LoginRequestDto;
import com.esporteclubepelotas.loja.modules.auth.dto.PortalUserSyncDto;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

@Slf4j
@Service
public class PortalIntegrationClient {

    @Value("${app.portal.api-url:http://localhost:7998}")
    private String portalApiUrl;

    @Value("${app.portal.internal-api-key:}")
    private String portalInternalApiKey;

    private final RestTemplate restTemplate;

    public PortalIntegrationClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    public PortalUserSyncDto authenticateWithPortal(LoginRequestDto request) {
        if (portalApiUrl == null || portalApiUrl.isBlank()
                || portalInternalApiKey == null || portalInternalApiKey.isBlank()) {
            throw new BusinessException(
                    "Integração S2S com o Portal não configurada (PORTAL_API_URL / PORTAL_INTERNAL_API_KEY).",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        String url = portalApiUrl.trim().replaceAll("/+$", "") + "/integrations/store/authenticate";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", portalInternalApiKey.trim());

        try {
            ResponseEntity<PortalUserSyncDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    PortalUserSyncDto.class
            );
            if (response.getBody() == null || response.getBody().getPortalUserId() == null) {
                throw new UnauthorizedException("Falha ao validar usuário no Portal.");
            }
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                throw new UnauthorizedException("Credenciais inválidas ou conta bloqueada no Portal.");
            }
            log.error("Erro HTTP do Portal S2S ({}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new BusinessException("Erro ao validar credenciais no Portal.", HttpStatus.BAD_GATEWAY);
        } catch (ResourceAccessException ex) {
            log.error("Portal S2S inacessível em {}: {}", url, ex.getMessage());
            throw new BusinessException("Portal indisponível no momento para autenticação.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public Optional<MemberStatusDto> checkMemberStatus(String cpf) {
        if (cpf == null || cpf.isBlank()
                || portalApiUrl == null || portalApiUrl.isBlank()
                || portalInternalApiKey == null || portalInternalApiKey.isBlank()) {
            return Optional.empty();
        }

        String cleanCpf = cpf.replaceAll("[^0-9]", "");
        if (cleanCpf.length() != 11) {
            return Optional.empty();
        }

        String baseUrl = portalApiUrl.trim().replaceAll("/+$", "");
        String url = UriComponentsBuilder.fromUriString(baseUrl + "/integrations/store/member-status")
                .queryParam("cpf", cleanCpf)
                .toUriString();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", portalInternalApiKey.trim());
            ResponseEntity<MemberStatusDto> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    MemberStatusDto.class
            );
            return Optional.ofNullable(response.getBody());
        } catch (RestClientException ex) {
            log.warn("Consulta de Sócio no Portal falhou (usando cache local se disponível): {}", ex.getMessage());
            return Optional.empty();
        }
    }

    @Data
    public static class MemberStatusDto {
        private boolean found;
        private String portalUserId;
        private String name;
        private boolean isSocioActive;
        private String socioPlanName;
        private String socioStatus;
    }
}
