package com.sopaletras.user;

import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.sopaletras.common.ApiException;
import com.sopaletras.common.TextNormalizer;

/**
 * Reglas del alias público: 3 a 20 caracteres (letras, números, punto y guion bajo),
 * sin palabras bloqueadas y único sin importar mayúsculas ni acentos.
 */
@Component
public class AliasPolicy {

    private static final Pattern ALLOWED = Pattern.compile("^[\\p{L}0-9_.]{3,20}$");

    private final ModerationService moderation;
    private final UserRepository users;

    public AliasPolicy(ModerationService moderation, UserRepository users) {
        this.moderation = moderation;
        this.users = users;
    }

    public static String key(String alias) {
        return TextNormalizer.forSearch(alias).replace(" ", "");
    }

    /** Valida y devuelve la clave normalizada; lanza error si no se puede usar. */
    public String validate(String alias, User current) {
        String trimmed = alias == null ? "" : alias.trim();
        if (!ALLOWED.matcher(trimmed).matches()) {
            throw ApiException.badRequest("ALIAS_INVALID",
                    "El alias debe tener de 3 a 20 caracteres: letras, números, punto o guion bajo.");
        }
        String key = key(trimmed);
        if (key.length() < 3) {
            throw ApiException.badRequest("ALIAS_INVALID", "El alias debe tener al menos 3 letras o números.");
        }
        if (moderation.isBlocked(trimmed)) {
            throw ApiException.badRequest("ALIAS_BLOCKED", "Ese alias no está permitido.");
        }
        boolean sameAsCurrent = current != null && key.equals(current.getAliasKey());
        if (!sameAsCurrent && users.existsByAliasKey(key)) {
            throw ApiException.conflict("ALIAS_TAKEN", "Ese alias ya está en uso.");
        }
        return key;
    }
}
