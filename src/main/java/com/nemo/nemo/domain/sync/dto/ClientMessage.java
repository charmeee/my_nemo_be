package com.nemo.nemo.domain.sync.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
public class ClientMessage {

    private String type;
    private Integer protocolVersion;
    private String connectRequestId;
    private Long lastServerClock;
    private Long clientClock;
    private Map<String, Object> diff;
    private Map<String, Object> presence;
    private Map<String, Object> schema;
}
