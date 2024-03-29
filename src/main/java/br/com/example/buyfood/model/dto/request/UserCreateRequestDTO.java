package br.com.example.buyfood.model.dto.request;

import br.com.example.buyfood.enums.Role;
import java.time.LocalDate;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.br.CNPJ;
import org.hibernate.validator.constraints.br.CPF;

@Getter
@Setter
public class UserCreateRequestDTO {

  @CPF private String cpf;
  @CNPJ private String cnpj;
  @NotBlank private String firstName;
  @NotBlank private String lastName;
  private String nickName;
  @NotBlank private String phone;
  private LocalDate birthDate;
  @NotBlank @Email private String email;
  @NotBlank private String password;
  @NotBlank private Role role;
}
