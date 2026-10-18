package jiki.jiki.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSignupDto {

    @NotBlank
    @Size(max = 255)
    private String username;

    @NotBlank
    private String password1;

    @NotBlank
    @Size(max = 255)
    private String nickname;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

}


