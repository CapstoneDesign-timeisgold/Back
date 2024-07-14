//회원가입 dto

package jiki.jiki.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserCreateForm {

    @NotEmpty
    private String username;

    @NotEmpty
    private String password1;

    @NotEmpty
    private String nickname;

    @NotEmpty
    @Email
    private String email;

}


