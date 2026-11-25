package com.esporteclubepelotas.loja.modules.orders.service;

import com.esporteclubepelotas.loja.modules.auth.entity.StoreUser;
import com.esporteclubepelotas.loja.modules.auth.repository.StoreUserRepository;
import com.esporteclubepelotas.loja.modules.auth.service.PortalIntegrationClient;
import com.esporteclubepelotas.loja.modules.orders.dto.MemberLookupDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberLookupService {

    private final PortalIntegrationClient portalIntegrationClient;
    private final StoreUserRepository storeUserRepository;

    @Transactional
    public MemberLookupDto lookupByCpf(String rawCpf) {
        String cleanCpf = rawCpf != null ? rawCpf.replaceAll("[^0-9]", "") : "";
        if (cleanCpf.length() != 11) {
            return MemberLookupDto.builder()
                    .found(false)
                    .cpf(cleanCpf)
                    .isSocioActive(false)
                    .build();
        }

        Optional<StoreUser> cachedUser = storeUserRepository.findByCpf(cleanCpf);
        Optional<PortalIntegrationClient.MemberStatusDto> liveStatus = portalIntegrationClient.checkMemberStatus(cleanCpf);

        if (liveStatus.isPresent()) {
            PortalIntegrationClient.MemberStatusDto dto = liveStatus.get();
            if (!dto.isFound()) {
                return cachedUser.map(u -> MemberLookupDto.builder()
                        .found(true)
                        .storeUserId(u.getId())
                        .portalUserId(u.getPortalUserId())
                        .cpf(cleanCpf)
                        .name(u.getFullName())
                        .isSocioActive(u.isSocioActive())
                        .socioPlanName(u.getSocioPlanName())
                        .source("STORE_CACHE")
                        .build())
                        .orElse(MemberLookupDto.builder().found(false).cpf(cleanCpf).isSocioActive(false).build());
            }

            if (cachedUser.isPresent()) {
                StoreUser u = cachedUser.get();
                u.setSocioActive(dto.isSocioActive());
                u.setSocioPlanName(dto.getSocioPlanName());
                storeUserRepository.save(u);
            }

            return MemberLookupDto.builder()
                    .found(true)
                    .portalUserId(dto.getPortalUserId())
                    .storeUserId(cachedUser.map(StoreUser::getId).orElse(null))
                    .cpf(cleanCpf)
                    .name(dto.getName())
                    .isSocioActive(dto.isSocioActive())
                    .socioPlanName(dto.getSocioPlanName())
                    .source("PORTAL_S2S")
                    .build();
        }

        return cachedUser.map(u -> MemberLookupDto.builder()
                .found(true)
                .storeUserId(u.getId())
                .portalUserId(u.getPortalUserId())
                .cpf(cleanCpf)
                .name(u.getFullName())
                .isSocioActive(u.isSocioActive())
                .socioPlanName(u.getSocioPlanName())
                .source("OFFLINE_CACHE")
                .build())
                .orElse(MemberLookupDto.builder().found(false).cpf(cleanCpf).isSocioActive(false).source("OFFLINE").build());
    }
}
