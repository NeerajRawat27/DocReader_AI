package com.docureader.repository;

import com.docureader.model.Document;
import com.docureader.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    List<DocumentChunk> findByDocumentOrderByChunkIndexAsc(Document document);

    @Query("SELECT c FROM DocumentChunk c WHERE c.document.user.id = :userId")
    List<DocumentChunk> findAllByUserId(@Param("userId") Long userId);

    @Query("SELECT c FROM DocumentChunk c WHERE c.document.id IN :documentIds AND c.document.user.id = :userId")
    List<DocumentChunk> findByDocumentIdsAndUserId(@Param("documentIds") List<Long> documentIds, @Param("userId") Long userId);

    void deleteByDocument(Document document);
}
