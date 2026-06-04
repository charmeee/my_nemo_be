package com.nemo.nemo.domain.page.service;

import com.nemo.nemo.common.exception.ErrorCode;
import com.nemo.nemo.common.exception.NemoException;
import com.nemo.nemo.config.AppProperties;
import com.nemo.nemo.domain.album.entity.Album;
import com.nemo.nemo.domain.album.entity.AlbumMember;
import com.nemo.nemo.domain.album.entity.AlbumRole;
import com.nemo.nemo.domain.album.entity.MemberStatus;
import com.nemo.nemo.domain.album.repository.AlbumMemberRepository;
import com.nemo.nemo.domain.album.repository.AlbumRepository;
import com.nemo.nemo.domain.page.dto.PageThumbnailResponse;
import com.nemo.nemo.domain.page.entity.AlbumPage;
import com.nemo.nemo.domain.page.repository.AlbumPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlbumPageService {

    private final AlbumPageRepository albumPageRepository;
    private final AlbumMemberRepository albumMemberRepository;
    private final AlbumRepository albumRepository;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<PageThumbnailResponse> getPages(UUID albumId, UUID userId) {
        getMemberOrThrow(albumId, userId);
        return albumPageRepository.findByAlbumIdAndDeletedAtIsNull(albumId)
                .stream()
                .map(p -> new PageThumbnailResponse(
                        p.getTlPageId().toString(),
                        p.getThumbnailUrl(),
                        p.getCreatedAt() != null ? p.getCreatedAt().toString() : null
                ))
                .toList();
    }

    @Transactional
    public void uploadThumbnail(UUID albumId, UUID tlPageId, MultipartFile file, UUID userId) {
        getMemberOrThrow(albumId, userId);

        Album album = albumRepository.findById(albumId)
                .filter(a -> a.getDeletedAt() == null)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_NOT_FOUND));

        String filename = tlPageId + "_" + System.currentTimeMillis() + getExtension(file.getOriginalFilename());
        Path dir = Paths.get(appProperties.getFile().getUploadDir(), "thumbnails", albumId.toString());

        try {
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new NemoException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        String thumbnailUrl = appProperties.getFile().getBaseUrl()
                + "/thumbnails/" + albumId + "/" + filename;

        AlbumPage page = albumPageRepository.findByTlPageIdAndAlbumIdAndDeletedAtIsNull(tlPageId, albumId)
                .orElse(null);

        if (page == null) {
            page = AlbumPage.create(tlPageId, album);
            albumPageRepository.save(page);
        }

        page.updateThumbnailUrl(thumbnailUrl);
    }

    @Transactional
    public void deletePage(UUID albumId, UUID tlPageId, UUID userId) {
        AlbumMember member = getMemberOrThrow(albumId, userId);
        if (member.getRole() == AlbumRole.VIEWER) {
            throw new NemoException(ErrorCode.ALBUM_EDIT_DENIED);
        }

        AlbumPage page = albumPageRepository.findByTlPageIdAndAlbumIdAndDeletedAtIsNull(tlPageId, albumId)
                .orElseThrow(() -> new NemoException(ErrorCode.IMAGE_NOT_FOUND));

        page.softDelete();
    }

    private AlbumMember getMemberOrThrow(UUID albumId, UUID userId) {
        return albumMemberRepository.findActiveByAlbumIdAndUserId(albumId, userId)
                .orElseThrow(() -> new NemoException(ErrorCode.ALBUM_ACCESS_DENIED));
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
