package jiki.jiki.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
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

    // UserCreateForm 객체를 JSON 형식의 문자열로 변환하는 메서드
    public String toJsonString() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this);
    }
}



