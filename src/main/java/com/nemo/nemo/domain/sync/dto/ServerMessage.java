package com.nemo.nemo.domain.sync.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerMessage {

    private String type;
    private Long serverClock;
    private String hydrationType;
    private Map<String, Object> diff;
    private String error;
    private String reason;

    public static ServerMessage connect(long serverClock, String hydrationType, Map<String, Object> diff) {
        ServerMessage msg = new ServerMessage();
        msg.type = "connect";
        msg.serverClock = serverClock;
        msg.hydrationType = hydrationType;
        msg.diff = diff;
        return msg;
    }

    public static ServerMessage patch(long serverClock, Map<String, Object> diff) {
        ServerMessage msg = new ServerMessage();
        msg.type = "patch";
        msg.serverClock = serverClock;
        msg.diff = diff;
        return msg;
    }

    public static ServerMessage pong() {
        ServerMessage msg = new ServerMessage();
        msg.type = "pong";
        return msg;
    }

    public static ServerMessage error(String errorMsg) {
        ServerMessage msg = new ServerMessage();
        msg.type = "error";
        msg.error = errorMsg;
        return msg;
    }

    public static ServerMessage forceClose(String reason) {
        ServerMessage msg = new ServerMessage();
        msg.type = "force-close";
        msg.reason = reason;
        return msg;
    }
}
