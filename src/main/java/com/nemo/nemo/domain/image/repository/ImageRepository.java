package com.nemo.nemo.domain.image.repository;

import com.nemo.nemo.domain.image.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ImageRepository extends JpaRepository<Image, UUID> {

    List<Image> findByAlbumIdOrderByCreatedAtDesc(UUID albumId);

    List<Image> findByAlbumIdAndExcalidrawFileIdIn(UUID albumId, Collection<String> excalidrawFileIds);
}
