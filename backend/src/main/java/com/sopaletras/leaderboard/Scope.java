package com.sopaletras.leaderboard;

/**
 * Ámbitos del ranking. El id de cada uno:
 * GLOBAL sin id · COUNTRY = ISO 3166-1 ("MX") · STATE = ISO 3166-2 ("MX-SON") · CITY = id de la ciudad.
 */
public enum Scope {
    CITY, STATE, COUNTRY, GLOBAL
}
