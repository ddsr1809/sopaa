package com.sopaletras.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    @Test
    void quitaAcentosYConservaEnie() {
        assertThat(TextNormalizer.forGrid("Ñandú")).isEqualTo("ÑANDU");
        assertThat(TextNormalizer.forGrid("Pingüino")).isEqualTo("PINGUINO");
        assertThat(TextNormalizer.forGrid("Vía Láctea")).isEqualTo("VIALACTEA");
        assertThat(TextNormalizer.forGrid("año-2026!")).isEqualTo("AÑO");
        assertThat(TextNormalizer.forGrid(null)).isEmpty();
    }

    @Test
    void normalizaParaBusqueda() {
        assertThat(TextNormalizer.forSearch("  San Luis Río  Colorado ")).isEqualTo("san luis rio colorado");
        assertThat(TextNormalizer.forSearch("Peñasco")).isEqualTo("peñasco");
    }
}
