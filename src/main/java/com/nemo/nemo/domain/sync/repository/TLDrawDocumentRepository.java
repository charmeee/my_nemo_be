package com.nemo.nemo.domain.sync.repository;

import com.nemo.nemo.domain.sync.entity.TLDrawDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TLDrawDocumentRepository extends JpaRepository<TLDrawDocument, UUID> {
}
