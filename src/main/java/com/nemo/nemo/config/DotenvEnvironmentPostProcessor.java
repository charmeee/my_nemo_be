package com.nemo.nemo.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * .env 파일을 Spring Environment에 로드.
 * 시스템 환경변수보다 낮은 우선순위 (이미 설정된 환경변수는 덮어쓰지 않음).
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = Paths.get(".env");
        if (!Files.exists(envFile)) {
            return;
        }

        Map<String, Object> props = new HashMap<>();
        try {
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isBlank() || line.startsWith("#")) continue;
                int idx = line.indexOf('=');
                if (idx < 0) continue;
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                if (!environment.containsProperty(key)) {
                    props.put(key, value);
                }
            }
        } catch (IOException e) {
            // .env 파싱 실패 시 무시 (환경변수 직접 주입 방식 사용)
        }

        if (!props.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource("dotenv", props));
        }
    }
}
