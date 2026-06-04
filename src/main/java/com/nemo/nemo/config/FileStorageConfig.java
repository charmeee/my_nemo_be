package com.nemo.nemo.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FileStorageConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(appProperties.getFile().getUploadDir()));
            log.info("파일 업로드 디렉토리 초기화: {}", appProperties.getFile().getUploadDir());
        } catch (IOException e) {
            throw new RuntimeException("파일 업로드 디렉토리 생성 실패", e);
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadDir = Paths.get(appProperties.getFile().getUploadDir()).toAbsolutePath().toString();
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}
