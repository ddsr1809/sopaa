package com.sopaletras.user;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.sopaletras.common.TextNormalizer;

/**
 * Filtro de palabras bloqueadas para alias, sopas personalizadas y el relleno de la cuadrícula.
 * Palabras de 5+ letras se buscan dentro del texto; las cortas solo como palabra exacta
 * (para no bloquear "COMPUTO" por contener "PUTO").
 */
@Service
public class ModerationService {

    private static final int SUBSTRING_MIN_LENGTH = 5;

    private final BlockedWordRepository repository;
    private volatile Set<String> blocked = Set.of();

    public ModerationService(BlockedWordRepository repository) {
        this.repository = repository;
        reload();
    }

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT10M")
    public void reload() {
        blocked = repository.findAll().stream()
                .map(b -> TextNormalizer.forGrid(b.getWord()))
                .filter(w -> !w.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Palabras normalizadas que el relleno de la cuadrícula no debe formar por accidente. */
    public Set<String> blockedWords() {
        return blocked;
    }

    public boolean isBlocked(String text) {
        String norm = TextNormalizer.forGrid(text);
        if (norm.isEmpty()) {
            return false;
        }
        for (String w : blocked) {
            if (w.length() >= SUBSTRING_MIN_LENGTH ? norm.contains(w) : norm.equals(w)) {
                return true;
            }
        }
        // Las palabras cortas también se revisan como palabra suelta dentro de frases ("EL PUTO")
        for (String token : text.split("[\\s_.\\-]+")) {
            if (blocked.contains(TextNormalizer.forGrid(token))) {
                return true;
            }
        }
        return false;
    }
}
