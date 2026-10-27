package com.sopaletras.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockedWordRepository extends JpaRepository<BlockedWord, Long> {
}
