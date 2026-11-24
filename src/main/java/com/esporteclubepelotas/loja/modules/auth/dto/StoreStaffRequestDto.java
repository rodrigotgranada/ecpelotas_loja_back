package com.esporteclubepelotas.loja.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreStaffRequestDto {

    @NotBlank(message = "Nome completo é obrigatório")
    @Size(max = 150)
    private String fullName;

    private String email;
    private String cpf;

    @NotBlank(message = "Perfil operacional é obrigatório")
    private String role;

    private String password;
    private Boolean active;
}
