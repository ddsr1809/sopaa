package com.sopaletras.session;

import org.springframework.stereotype.Component;

/**
 * Verificación de que la petición viene de la app original en un dispositivo no alterado:
 * Play Integrity en Android y App Attest / DeviceCheck en iOS.
 * <p>
 * La implementación por defecto no verifica nada ({@code UNKNOWN}); con
 * {@code app.ranking.require-integrity=true} solo cuentan para el ranking las partidas con {@code PASS}.
 * Para activarla, reemplaza este bean por uno que llame a la API de Google / Apple.
 */
public interface IntegrityVerifier {

    enum Verdict { PASS, FAIL, UNKNOWN }

    Verdict verify(String integrityToken, String deviceId);

    @Component
    class Noop implements IntegrityVerifier {
        @Override
        public Verdict verify(String integrityToken, String deviceId) {
            return Verdict.UNKNOWN;
        }
    }
}
