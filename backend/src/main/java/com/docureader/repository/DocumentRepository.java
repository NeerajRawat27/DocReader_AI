package com.docureader.repository;

import com.docureader.model.Document;
import com.docureader.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserOrderByCreatedAtDesc(User user);
    Optional<Document> findByIdAndUser(Long id, User user);
    long countByUser(User user);
    List<Document> findByUser(User user);
}
