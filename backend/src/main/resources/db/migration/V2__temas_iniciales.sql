-- Temas curados iniciales. Las palabras se guardan como se muestran (con acentos);
-- el motor las normaliza al colocarlas en la cuadrícula (conserva la Ñ).

INSERT INTO topics (slug, name, description, type, category, region, icon) VALUES
 ('animales',     'Animales',              'Animales de todo el mundo',               'CURATED', 'Naturaleza',      NULL,     'pets'),
 ('frutas',       'Frutas',                'Frutas de temporada y tropicales',        'CURATED', 'Comida',          NULL,     'nutrition'),
 ('comida-mexicana','Comida mexicana',     'Platillos y antojitos',                   'CURATED', 'Comida',          'MX',     'restaurant'),
 ('paises',       'Países',                'Países de América y el mundo',            'CURATED', 'Geografía',       NULL,     'public'),
 ('deportes',     'Deportes',              'Deportes y disciplinas',                  'CURATED', 'Deportes',        NULL,     'sports_soccer'),
 ('futbol',       'Futbol',                'Todo lo que pasa en la cancha',           'CURATED', 'Deportes',        NULL,     'sports_soccer'),
 ('espacio',      'Espacio',               'Planetas, estrellas y exploración',       'CURATED', 'Ciencia',         NULL,     'rocket'),
 ('cuerpo-humano','Cuerpo humano',         'Órganos, huesos y sistemas',              'CURATED', 'Ciencia',         NULL,     'accessibility'),
 ('clima',        'Clima',                 'Fenómenos del tiempo',                    'CURATED', 'Naturaleza',      NULL,     'cloud'),
 ('profesiones',  'Profesiones',           'Oficios y profesiones',                   'CURATED', 'Cultura',         NULL,     'work'),
 ('musica',       'Instrumentos musicales','Instrumentos de cuerda, viento y percusión','CURATED','Entretenimiento', NULL,    'music_note'),
 ('tecnologia',   'Tecnología',            'Computación e internet',                  'CURATED', 'Ciencia',         NULL,     'computer'),
 ('sonora',       'Sonora',                'Lugares, comida y tradiciones de Sonora', 'CURATED', 'Geografía',       'MX-SON', 'landscape');

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Perro','Gato','Caballo','Jirafa','Elefante','León','Tigre','Delfín','Ballena','Águila',
 'Búho','Tortuga','Conejo','Ardilla','Pingüino','Cocodrilo','Serpiente','Mariposa','Abeja','Lobo',
 'Zorro','Venado','Coyote','Jaguar','Ñandú','Oso','Cebra','Camello','Pulpo','Tiburón'
]) AS w WHERE t.slug = 'animales';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Manzana','Plátano','Naranja','Mango','Papaya','Sandía','Melón','Fresa','Uva','Piña',
 'Guayaba','Kiwi','Durazno','Pera','Ciruela','Cereza','Limón','Toronja','Coco','Mandarina',
 'Frambuesa','Granada','Tuna','Pitaya','Higo','Arándano','Mamey','Lima','Zarzamora','Guanábana'
]) AS w WHERE t.slug = 'frutas';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Tacos','Tamales','Pozole','Enchiladas','Quesadilla','Tortilla','Mole','Chilaquiles','Elote','Gordita',
 'Sope','Tlayuda','Birria','Menudo','Pambazo','Guacamole','Salsa','Frijoles','Churros','Atole',
 'Horchata','Flautas','Tostada','Carnitas','Barbacoa','Chiles','Huarache','Cajeta','Nopales','Champurrado'
]) AS w WHERE t.slug = 'comida-mexicana';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'México','Canadá','Brasil','Argentina','Chile','Perú','Colombia','Ecuador','Uruguay','Paraguay',
 'Bolivia','Venezuela','Cuba','Panamá','Honduras','Guatemala','España','Francia','Italia','Alemania',
 'Portugal','Japón','China','India','Egipto','Marruecos','Australia','Rusia','Grecia','Noruega'
]) AS w WHERE t.slug = 'paises';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Futbol','Béisbol','Tenis','Natación','Atletismo','Boxeo','Ciclismo','Voleibol','Básquetbol','Golf',
 'Rugby','Esgrima','Karate','Judo','Remo','Surf','Patinaje','Gimnasia','Hockey','Ajedrez',
 'Clavados','Maratón','Triatlón','Escalada','Lucha','Tiro','Vela','Polo','Squash','Frontón'
]) AS w WHERE t.slug = 'deportes';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Gol','Portero','Delantero','Defensa','Árbitro','Penal','Córner','Balón','Cancha','Estadio',
 'Afición','Tarjeta','Fuera','Liga','Copa','Mundial','Técnico','Capitán','Remate','Pase',
 'Chilena','Tiro','Red','Silbato','Medio','Banca','Cambio','Final','Torneo','Equipo'
]) AS w WHERE t.slug = 'futbol';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Sol','Luna','Tierra','Marte','Júpiter','Saturno','Venus','Mercurio','Urano','Neptuno',
 'Cometa','Galaxia','Estrella','Órbita','Satélite','Cohete','Nebulosa','Eclipse','Asteroide','Meteoro',
 'Astronauta','Telescopio','Gravedad','Cosmos','Planeta','Vía Láctea','Constelación','Supernova','Plutón','Estación'
]) AS w WHERE t.slug = 'espacio';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Corazón','Pulmón','Cerebro','Hígado','Riñón','Estómago','Hueso','Músculo','Piel','Sangre',
 'Vena','Arteria','Nervio','Columna','Cráneo','Costilla','Rodilla','Codo','Hombro','Tobillo',
 'Oído','Nariz','Lengua','Diente','Ojo','Uña','Cabello','Mano','Pie','Garganta'
]) AS w WHERE t.slug = 'cuerpo-humano';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Lluvia','Nube','Viento','Nieve','Granizo','Trueno','Relámpago','Rayo','Niebla','Tormenta',
 'Huracán','Tornado','Sequía','Calor','Frío','Rocío','Escarcha','Brisa','Arcoíris','Humedad',
 'Ciclón','Monzón','Helada','Neblina','Llovizna','Chubasco','Ola de calor','Clima','Pronóstico','Temperatura'
]) AS w WHERE t.slug = 'clima';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Médico','Maestro','Abogado','Ingeniero','Bombero','Policía','Chef','Piloto','Enfermero','Arquitecto',
 'Carpintero','Plomero','Electricista','Panadero','Pintor','Músico','Actor','Escritor','Periodista','Dentista',
 'Veterinario','Contador','Agricultor','Pescador','Mecánico','Sastre','Juez','Científico','Programador','Diseñador'
]) AS w WHERE t.slug = 'profesiones';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Guitarra','Piano','Violín','Flauta','Batería','Trompeta','Saxofón','Arpa','Tambor','Clarinete',
 'Acordeón','Bajo','Ukulele','Maracas','Marimba','Trombón','Tuba','Oboe','Violonchelo','Contrabajo',
 'Banjo','Mandolina','Órgano','Xilófono','Pandero','Güiro','Requinto','Vihuela','Bongó','Armónica'
]) AS w WHERE t.slug = 'musica';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Internet','Teclado','Pantalla','Ratón','Servidor','Código','Algoritmo','Datos','Red','Nube',
 'Robot','Chip','Memoria','Archivo','Correo','Programa','Software','Hardware','Celular','Batería',
 'Contraseña','Wifi','Bluetooth','Píxel','Navegador','Aplicación','Sensor','Virus','Respaldo','Bit'
]) AS w WHERE t.slug = 'tecnologia';

INSERT INTO topic_words (topic_id, word)
SELECT t.id, w FROM topics t, unnest(ARRAY[
 'Hermosillo','Nogales','Guaymas','Navojoa','Caborca','Álamos','Magdalena','Cananea','Empalme','Bahía de Kino',
 'Desierto','Saguaro','Sahuaro','Coyote','Carne asada','Coyota','Bacanora','Chimichanga','Pitahaya','Mezquite',
 'Pinacate','Yaqui','Mayo','Seri','Pascola','Venado','Río Sonora','Mar de Cortés','Sierra','Agua Prieta'
]) AS w WHERE t.slug = 'sonora';
