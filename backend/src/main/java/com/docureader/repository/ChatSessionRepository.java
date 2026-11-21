package com.docureader.repository;

import com.docureader.model.ChatSession;
import com.docureader.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    List<ChatSession> findByUserOrderByCreatedAtDesc(User user);
    Optional<ChatSession> findByIdAndUser(Long id, User user);
}
