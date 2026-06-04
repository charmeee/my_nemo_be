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

    private static final int TLSYNC_PROTOCOL_VERSION = 8;

    private String type;
    private Long serverClock;
    private Long clientClock;
    private String hydrationType;
    private Integer protocolVersion;
    private String connectRequestId;
    private String action;        // push_result: "commit" | "rebase"
    private Map<String, Object> diff;
    private Map<String, Object> schema;
    private Boolean isReadonly;
    private String error;
    private String reason;

    public static ServerMessage connect(long serverClock, Map<String, Object> diff,
                                        String connectRequestId, Map<String, Object> clientSchema) {
        ServerMessage msg = new ServerMessage();
        msg.type = "connect";
        msg.protocolVersion = TLSYNC_PROTOCOL_VERSION;
        msg.serverClock = serverClock;
        msg.hydrationType = "wipe_all";
        msg.diff = diff;
        msg.connectRequestId = connectRequestId;
        msg.schema = clientSchema; // echo back client schema to avoid migration
        msg.isReadonly = false;
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

    public static ServerMessage pushResult(long clientClock, long serverClock) {
        ServerMessage msg = new ServerMessage();
        msg.type = "push_result";
        msg.clientClock = clientClock;
        msg.serverClock = serverClock;
        msg.action = "commit";
        return msg;
    }

    public static ServerMessage forceClose(String reason) {
        ServerMessage msg = new ServerMessage();
        msg.type = "force-close";
        msg.reason = reason;
        return msg;
    }
}
