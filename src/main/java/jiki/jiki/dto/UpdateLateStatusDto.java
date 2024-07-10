package jiki.jiki.dto;

import lombok.Data;

@Data
public class UpdateLateStatusDto {
    private Long promiseId;
    private boolean arrival;
}