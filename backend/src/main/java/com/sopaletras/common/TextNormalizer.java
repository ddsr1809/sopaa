package com.sopaletras.common;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normaliza texto en español para la cuadrícula: MAYÚSCULAS, sin acentos ni diéresis,
 * sin espacios ni signos, y conservando la Ñ como letra propia.
 * <pre>
 *   "Vía Láctea"  → "VIALACTEA"
 *   "Ñandú"       → "ÑANDU"
 *   "Pingüino"    → "PINGUINO"
 * </pre>
 */
public final class TextNormalizer {

    private static final Locale ES = Locale.forLanguageTag("es-MX");

    private TextNormalizer() {
    }

    /** Solo letras A-Z y Ñ. */
    public static String forGrid(String text) {
        if (text == null) {
            return "";
        }
        String upper = text.toUpperCase(ES);
        StringBuilder out = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i);
            if (ch == 'Ñ') {
                out.append('Ñ');
                continue;
            }
            String base = Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD);
            char first = base.charAt(0);
            if (first >= 'A' && first <= 'Z') {
                out.append(first);
            }
        }
        return out.toString();
    }

    /** Minúsculas sin acentos y con espacios simples, para búsquedas ("San Luis Río" → "san luis rio"). */
    public static String forSearch(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(ES);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (ch == 'ñ') {
                out.append('ñ');
                continue;
            }
            String base = Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD);
            char first = base.charAt(0);
            if (Character.isLetterOrDigit(first)) {
                out.append(first);
            } else if (Character.isWhitespace(first) || first == '-' || first == ',') {
                out.append(' ');
            }
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }
}
