package com.esporteclubepelotas.loja.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequestDto {
    @NotBlank(message = "Informe CPF ou E-mail")
    private String cpf;

    @NotBlank(message = "Informe a senha")
    private String password;
}
