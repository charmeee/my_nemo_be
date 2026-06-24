package com.nemo.nemo.domain.image.service;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
public class ImageOptimizer {

    static final int MAX_DIMENSION = 2048;
    static final float JPEG_QUALITY = 0.85f;

    public Result optimize(InputStream input, String mimeType) throws IOException {
        if (!isOptimizable(mimeType)) {
            return Result.passthrough(input.readAllBytes(), mimeType);
        }

        byte[] originalBytes = input.readAllBytes();
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(originalBytes));
        if (source == null) {
            return Result.passthrough(originalBytes, mimeType);
        }

        boolean needsResize = source.getWidth() > MAX_DIMENSION || source.getHeight() > MAX_DIMENSION;
        String outputFormat = chooseOutputFormat(mimeType);

        // 리사이즈 불필요 + 이미 jpeg 면 재인코딩만 했을 때 원본보다 커질 수 있어 패스
        if (!needsResize && "image/jpeg".equals(mimeType)) {
            return Result.passthrough(originalBytes, mimeType);
        }

        Thumbnails.Builder<BufferedImage> builder = Thumbnails.of(source);
        if (needsResize) {
            builder.size(MAX_DIMENSION, MAX_DIMENSION).keepAspectRatio(true);
        } else {
            builder.scale(1.0);
        }
        builder.outputFormat(outputFormat);
        if ("jpg".equals(outputFormat)) {
            builder.outputQuality(JPEG_QUALITY);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        builder.toOutputStream(out);
        byte[] optimized = out.toByteArray();

        // 안전장치: 최적화 결과가 원본보다 크면 원본 유지
        if (optimized.length >= originalBytes.length) {
            return Result.passthrough(originalBytes, mimeType);
        }
        return new Result(optimized, "image/" + ("jpg".equals(outputFormat) ? "jpeg" : outputFormat), outputFormat);
    }

    private boolean isOptimizable(String mimeType) {
        return "image/jpeg".equals(mimeType) || "image/png".equals(mimeType) || "image/webp".equals(mimeType);
    }

    private String chooseOutputFormat(String mimeType) {
        // PNG 는 PNG 로 유지(투명도 보존). JPEG/WebP 는 JPEG 로 재인코딩 (Thumbnailator + ImageIO 가 안정적으로 지원)
        return "image/png".equals(mimeType) ? "png" : "jpg";
    }

    public record Result(byte[] bytes, String mimeType, String extension) {
        public static Result passthrough(byte[] bytes, String mimeType) {
            String ext = switch (mimeType) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                case "image/heic" -> "heic";
                default -> "bin";
            };
            return new Result(bytes, mimeType, ext);
        }
    }
}
