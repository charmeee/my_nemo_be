package com.nemo.nemo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cookie cookie = new Cookie();
    private OAuth oauth = new OAuth();
    private File file = new File();

    @Getter
    @Setter
    public static class Cookie {
        private boolean secure = false;
        private String sameSite = "Strict";
        private String domain = "localhost";
    }

    @Getter
    @Setter
    public static class OAuth {
        private String frontendRedirectUri = "http://localhost:5173/auth/callback";
    }

    @Getter
    @Setter
    public static class File {
        private String uploadDir = "./data/uploads";
        private String baseUrl = "http://localhost:8080/files";
    }
}
