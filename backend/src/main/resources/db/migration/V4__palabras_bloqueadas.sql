-- Lista mínima de palabras bloqueadas para alias y sopas personalizadas.
-- Se amplía desde el panel; las palabras se guardan normalizadas (MAYÚSCULAS, sin acentos, con Ñ).
INSERT INTO blocked_words (word, category) VALUES
 ('PENDEJO','PROFANITY'), ('PENDEJA','PROFANITY'), ('CABRON','PROFANITY'), ('CHINGA','PROFANITY'),
 ('CHINGAR','PROFANITY'), ('CHINGADA','PROFANITY'), ('VERGA','PROFANITY'), ('CULERO','PROFANITY'),
 ('MIERDA','PROFANITY'), ('PUTO','PROFANITY'), ('PUTA','PROFANITY'), ('MAMADA','PROFANITY'),
 ('JOTO','HATE'), ('MARICON','HATE'), ('NAZI','HATE'), ('HITLER','HATE'),
 ('PINCHE','PROFANITY'), ('NALGAS','PROFANITY'),
 ('ADMIN','RESERVED'), ('ADMINISTRADOR','RESERVED'), ('MODERADOR','RESERVED'), ('SOPADELETRAS','RESERVED');
