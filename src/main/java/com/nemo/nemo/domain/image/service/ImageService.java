package com.nemo.nemo.domain.image.service;

import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.config.AppProperties;
import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.album.entity.AlbumMember;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.entity.MemberStatus;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.album.repository.AlbumRepository;
import com.nemo.nemo.domain.image.dto.ImageResponse;
import com.nemo.nemo.domain.image.entity.Image;
import com.nemo.nemo.domain.image.repository.ImageRepository;
import com.nemo.nemo.domain.member.entity.Member;
import com.nemo.nemo.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ImageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic"
    );

    private final ImageRepository imageRepository;
    private final AlbumMemberRepository albumMemberRepository;
    private final AlbumRepository albumRepository;
    private final MemberRepository memberRepository;
    private final AppProperties appProperties;

    // MIME/크기/권한/Lock 검증 후 파일 시스템 저장 + DB 메타 등록
    @Transactional
    public ImageResponse uploadImage(UUID albumId, UUID userId, MultipartFile file, String excalidrawFileId) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new NemoException(ErrorCode.IMAGE_SIZE_EXCEEDED);
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new NemoException(ErrorCode.IMAGE_TYPE_NOT_SUPPORTED);
        }

        AlbumMember albumMember = albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_ACCESS_DENIED));

        if (albumMember.getRole() == AlbumRole.VIEWER) {
            throw new NemoException(ErrorCode.ALBUM_EDIT_DENIED);
        }

        Album album = albumRepository.findById(albumId)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_NOT_FOUND));

        if (album.isLocked() && albumMember.getRole() != AlbumRole.ADMIN) {
            throw new NemoException(ErrorCode.ALBUM_LOCKED);
        }

        Member uploader = memberRepository.findById(userId)
                .orElseThrow(() -> new NemoException(ErrorCode.MEMBER_NOT_FOUND));

        String extension = extractExtension(file.getOriginalFilename(), mimeType);
        String filename = UUID.randomUUID() + "." + extension;
        String relativePath = "albums/" + albumId + "/" + filename;

        Path targetPath = Paths.get(appProperties.getFile().getUploadDir()).resolve(relativePath);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath);
        } catch (IOException e) {
            throw new NemoException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        String url = appProperties.getFile().getBaseUrl() + "/" + relativePath;

        Image image = Image.create(album, uploader, relativePath, url, file.getSize(), mimeType, excalidrawFileId);
        imageRepository.save(image);

        return toResponse(image);
    }

    // 멤버 권한 확인 후 앨범 이미지 목록 최신순 반환
    public List<ImageResponse> getImages(UUID albumId, UUID userId) {
        albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_ACCESS_DENIED));

        return imageRepository.findByAlbumIdOrderByCreatedAtDesc(albumId).stream()
                .map(this::toResponse)
                .toList();
    }

    // 업로더 또는 ADMIN만 이미지 삭제 가능 (파일 + DB 동시 제거)
    @Transactional
    public void deleteImage(UUID albumId, UUID imageId, UUID userId) {
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new NemoException(ErrorCode.IMAGE_NOT_FOUND));

        AlbumMember albumMember = albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_ACCESS_DENIED));

        boolean isOwner = image.getUploader().getId().equals(userId);
        boolean isAdmin = albumMember.getRole() == AlbumRole.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new NemoException(ErrorCode.ALBUM_EDIT_DENIED);
        }

        Path filePath = Paths.get(appProperties.getFile().getUploadDir()).resolve(image.getFilePath());
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new NemoException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        imageRepository.delete(image);
    }

    private ImageResponse toResponse(Image image) {
        return new ImageResponse(
                image.getId().toString(),
                image.getUrl(),
                image.getMimeType(),
                image.getSize(),
                image.getCreatedAt() != null
                        ? image.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null
        );
    }

    private String extractExtension(String originalFilename, String mimeType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        }
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/heic" -> "heic";
            default -> "bin";
        };
    }
}
