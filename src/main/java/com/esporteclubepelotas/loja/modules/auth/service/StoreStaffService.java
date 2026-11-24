package com.esporteclubepelotas.loja.modules.auth.service;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.common.exception.ForbiddenException;
import com.esporteclubepelotas.loja.common.exception.ResourceNotFoundException;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.modules.auth.dto.StoreStaffRequestDto;
import com.esporteclubepelotas.loja.modules.auth.dto.StoreUserDto;
import com.esporteclubepelotas.loja.modules.auth.entity.StoreUser;
import com.esporteclubepelotas.loja.modules.auth.repository.StoreUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoreStaffService {

    private static final Set<String> ASSIGNABLE_ROLES = Set.of(
            "ADMIN", "STORE_MANAGER", "SELLER", "STOCK_CLERK", "USER"
    );

    private static final Set<String> MANAGER_ASSIGNABLE_ROLES = Set.of(
            "SELLER", "STOCK_CLERK", "USER"
    );

    private final StoreUserRepository storeUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<StoreUserDto> listStaff() {
        return storeUserRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(StoreUserDto::fromEntity)
                .toList();
    }

    @Transactional
    public StoreUserDto createLocalStaff(StoreStaffRequestDto request, UserPrincipal actor) {
        String normalizedRole = normalizeAndValidateRole(request.getRole(), actor);
        String email = normalizeEmail(request.getEmail());
        String cpf = normalizeCpf(request.getCpf());

        if (email == null && cpf == null) {
            throw new BusinessException("Informe pelo menos um E-mail ou CPF para o login do colaborador.");
        }
        if (request.getPassword() == null || request.getPassword().trim().length() < 6) {
            throw new BusinessException("Defina uma senha inicial com pelo menos 6 caracteres.");
        }

        ensureUniqueIdentifiers(email, cpf, null);

        StoreUser user = StoreUser.builder()
                .fullName(request.getFullName().trim())
                .email(email)
                .cpf(cpf)
                .passwordHash(passwordEncoder.encode(request.getPassword().trim()))
                .authSource("LOCAL")
                .role(normalizedRole)
                .active(request.getActive() == null || request.getActive())
                .tokenVersion(1)
                .build();

        return StoreUserDto.fromEntity(storeUserRepository.save(user));
    }

    @Transactional
    public StoreUserDto updateStaff(String id, StoreStaffRequestDto request, UserPrincipal actor) {
        StoreUser user = storeUserRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Colaborador não encontrado."));

        if ("OWNER".equalsIgnoreCase(user.getRole()) && !"OWNER".equalsIgnoreCase(actor.getRole())) {
            throw new ForbiddenException("Apenas OWNER pode editar outro usuário OWNER.");
        }

        String nextRole = normalizeAndValidateRole(request.getRole(), actor);
        String email = normalizeEmail(request.getEmail());
        String cpf = normalizeCpf(request.getCpf());

        if ("LOCAL".equalsIgnoreCase(user.getAuthSource()) && email == null && cpf == null) {
            throw new BusinessException("Colaborador local precisa manter E-mail ou CPF cadastrado.");
        }

        ensureUniqueIdentifiers(email, cpf, id);

        boolean nextActive = request.getActive() != null ? request.getActive() : user.isActive();
        if (user.getId().equals(actor.getId()) && !nextActive) {
            throw new BusinessException("Você não pode desativar seu próprio usuário logado.");
        }

        boolean invalidateSessions = !nextActive || !nextRole.equalsIgnoreCase(user.getRole());

        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setCpf(cpf);
        user.setRole(nextRole);
        user.setActive(nextActive);

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().trim().length() < 6) {
                throw new BusinessException("A nova senha deve ter pelo menos 6 caracteres.");
            }
            user.setPasswordHash(passwordEncoder.encode(request.getPassword().trim()));
            user.setAuthSource("LOCAL");
            invalidateSessions = true;
        }

        if (invalidateSessions) {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }

        return StoreUserDto.fromEntity(storeUserRepository.save(user));
    }

    private String normalizeAndValidateRole(String rawRole, UserPrincipal actor) {
        String normalized = rawRole != null ? rawRole.trim().toUpperCase() : "";
        if (!ASSIGNABLE_ROLES.contains(normalized)) {
            throw new BusinessException("Perfil inválido para a Loja: " + rawRole);
        }
        String actorRole = actor.getRole() != null ? actor.getRole().toUpperCase() : "";
        if ("STORE_MANAGER".equals(actorRole) && !MANAGER_ASSIGNABLE_ROLES.contains(normalized)) {
            throw new ForbiddenException("Gerente de Loja só pode atribuir perfis de Vendedor ou Estoquista.");
        }
        return normalized;
    }

    private void ensureUniqueIdentifiers(String email, String cpf, String currentId) {
        if (email != null) {
            storeUserRepository.findByEmailIgnoreCase(email).ifPresent(found -> {
                if (!found.getId().equals(currentId)) {
                    throw new BusinessException("Já existe um usuário na Loja com este e-mail.");
                }
            });
        }
        if (cpf != null) {
            storeUserRepository.findByCpf(cpf).ifPresent(found -> {
                if (!found.getId().equals(currentId)) {
                    throw new BusinessException("Já existe um usuário na Loja com este CPF.");
                }
            });
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return email.trim().toLowerCase();
    }

    private String normalizeCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) return null;
        String digits = cpf.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }
}
