package com.sopaletras.auth;

/** Verifica el ID token que la app obtiene con "Continuar con Google". */
public interface GoogleTokenVerifier {

    GoogleIdentity verify(String idToken);

    record GoogleIdentity(String subject, String email, boolean emailVerified, String name, String picture) {
    }
}
