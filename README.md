# BalizApp

App Android nativa que guarda dónde dejaste tu auto o moto, controla cuánto tiempo podés estar ahí y te guía a pie de vuelta al vehículo. Un agente de IA lee la foto del cartel de la calle y propone el vencimiento y el aviso, que el usuario confirma antes de guardar.

Trabajo práctico de Aplicaciones Móviles — Ingeniería en Informática, UNAJ.

## Grupo

- Sofía Quiroz
- David Bourlot

## Tecnologías

| Área | Tecnología |
| --- | --- |
| Lenguaje y UI | Kotlin, Jetpack Compose, Material 3 |
| Navegación | Navigation Compose con rutas tipadas |
| Arquitectura | MVVM + repositorios, StateFlow |
| Autenticación | Firebase Authentication (email y contraseña, Google con verificación de huella mediante BiometricPrompt) |
| Base de datos | Cloud Firestore con cache persistente en disco (funciona sin conexión) |
| Imágenes | JPEG comprimido dentro de Firestore (sin Storage: no requiere plan Blaze) |
| Mapa y ubicación | OpenStreetMap (osmdroid), FusedLocationProviderClient; rutas a pie con OSRM (servidor FOSSGIS, sin clave); mosaicos y último recorrido guardados en disco para uso offline |
| Sensores | Magnetómetro + acelerómetro (brújula con filtro y corrección por declinación magnética) |
| Notificaciones | Locales con AlarmManager (exactas, con respaldo inexacto), reprogramadas al reiniciar |
| Tema | Claro, oscuro o el del sistema (se elige en Perfil); el mapa también se oscurece |
| Idioma | Español (por defecto), inglés, italiano, portugués y alemán (se elige en Perfil) |
| Agente de IA | Firebase AI Logic (Gemini 2.5 Flash): lee el cartel, dice si se puede estacionar ahora y describe el lugar |

## Cómo ejecutarlo

1. Clonar el repositorio y abrirlo con una versión reciente de Android Studio (el proyecto usa AGP 9).
2. **`google-services.json`** ya está en el repositorio (`app/google-services.json`), así que no hay que descargarlo.
   Solo hay que volver a bajarlo desde la consola de Firebase (Configuración del proyecto → Tus apps → Android
   `com.example.balizapp`) si se cambia de proyecto de Firebase. Las claves de Firebase para Android no son secretas.
3. **Ingreso con Google:** el build de depuración firma con `app/debug-team.keystore`, una clave compartida por
   todo el grupo, así la huella es la misma en cualquier computadora. Su SHA-1 tiene que estar registrada una vez en
   la consola de Firebase (Configuración del proyecto → Tus apps → Android → Agregar huella digital):
   `F5:DE:06:5D:E0:BE:97:2E:04:EE:C9:5C:1A:66:71:9F:03:07:20:BD`.
   Se puede verificar con `./gradlew signingReport` (variante `debug`).
4. **Mapas:** se usa OpenStreetMap (biblioteca osmdroid), que no necesita clave de API ni cuenta de facturación.
5. En la consola de Firebase, activar:
   - Authentication → proveedores **Correo electrónico/contraseña** y **Google**.
   - **Firebase AI Logic** (Build → AI Logic → Gemini Developer API), para las funciones de IA.
   - **Firestore Database**. No hace falta Storage ni el plan Blaze: las fotos se guardan comprimidas en Firestore.
6. Publicar las reglas de seguridad: copiar `firestore.rules` en Firestore → Reglas, o con Firebase CLI:
   `firebase deploy --only firestore:rules`.
7. Ejecutar la configuración `app` en un emulador o teléfono con Android 8.0 (API 26) o superior.

> Avisos: en Android 13+ la app pide permiso de notificaciones; en Android 12+ conviene permitir
> "Alarmas y recordatorios" (Perfil → Activar) para que el aviso llegue en el minuto exacto.
> Perfil tiene un botón para enviar un aviso de prueba.

> Para la brújula conviene un teléfono físico: los emuladores no siempre simulan el magnetómetro.

## Estructura

```
app/src/main/java/com/example/balizapp/
├─ auth/          Login, registro, verificación (repositorio + ViewModel + pantallas)
├─ data/          Repositorios de Firestore, ubicación, rutas, fotos, conectividad y modelos (data/model)
├─ i18n/          Idioma de la app: guardado, aplicación y acceso a textos (AppLocale)
├─ navigation/    Rutas tipadas, NavHost y barra inferior
├─ notifications/ Alarmas y notificaciones de vencimiento
├─ ui/screens/    Una pantalla por archivo
├─ ui/components/ Piezas de UI reutilizables
└─ ui/theme/      Colores, tipografía y tema
app/src/main/res/values*/strings_*.xml   Textos por pantalla; values/ es español y values-en/it/pt/de las traducciones
firestore.rules   Reglas de Firestore (incluye las fotos)
firestore-tests/  Tests de las reglas de Firestore con el emulador (Node)
app/src/test/     Tests unitarios y de pantallas Compose (corren en la JVM, sin emulador)
```

## Tests

Resumen de cómo correrlos. El detalle (qué se probó, resultados, decisiones y límites) está en
[Cambios recientes → Testing](#testing-unitarios-de-pantallas-y-de-reglas-de-firestore).

| Qué | Dónde | Cómo correrlo |
| --- | --- | --- |
| Lógica, modelos, parseo de la IA y ViewModels | `app/src/test/java/.../{data,model,ui}` | `./gradlew testDebugUnitTest` |
| Pantallas Compose (Autenticación, Inicio, Perfil, Detalle, Formulario, Volver al auto, Mapa) | `app/src/test/java/.../ui/compose` | `./gradlew testDebugUnitTest` |
| Reglas de seguridad de Firestore | `firestore-tests/` | `cd firestore-tests && npm ci && npm test` |

- No hace falta emulador ni teléfono: los de Android corren en la JVM (Robolectric) y los de reglas contra el emulador de Firestore,
  que `npm test` levanta y apaga solo.
- Los tests de reglas necesitan Node 20+ y Java 21+ (el emulador se descarga la primera vez) y usan un proyecto de demostración
  (`demo-balizapp`): no tocan el de verdad.
- GitHub Actions corre todo en cada push a `main` y en cada pull request (`.github/workflows/tests.yml`).

## Estado

| Etapa | Contenido | Estado |
| --- | --- | --- |
| 0 | Orden del repositorio, reglas y README | Hecha |
| 1 | Navegación, barra inferior, pantallas base y tema | Hecha |
| 2 | Firestore: modelos, repositorios, vehículos y ajustes | Hecha |
| 3 | Lista, alta y edición, detalle (RF2–RF4) | Hecha |
| 4 | Ubicación y mapa (RF5) | Hecha |
| 5 | Cámara y fotos (RF6) | Hecha |
| 6 | Avisos de vencimiento (RF7) | Hecha |
| 7 | Volver al auto: brújula, distancia y recorrido por calles | Hecha |
| 8 | Modo offline: cache de Firestore, aviso de conexión/sincronización, recorrido y mapa en disco | Hecha |
| 9 | Ingreso con Google + huella, íconos de auto/moto, modo oscuro | Hecha |
| 10 | Agente de IA: lectura del cartel (regla y vencimiento), veredicto "¿se puede estacionar ahora?" y descripción del lugar | Hecha |
| 11 | Testing: unitarios, de ViewModels, de pantallas Compose y de reglas de Firestore, con CI | Hecha (ver "Testing") |

Para el offline se usó el cache persistente de Firestore en lugar de Room (no hace falta una base aparte).
La carga de fotos en "Nuevo estacionamiento" muestra solo los botones Cámara y Galería hasta que haya una foto.

## Cambios recientes y por qué se tomaron

### Testing: unitarios, de pantallas y de reglas de Firestore

#### Qué se hizo y resultados

Antes de esta etapa la app solo tenía los tests de ejemplo que genera Android Studio. Ahora hay **219 tests de Android y 27 de reglas
de Firestore, todos en verde (0 fallos, 0 omitidos)**. La suite de Android tarda alrededor de uno o dos minutos en esta computadora.

| Área | Archivo(s) | Tests | Qué cubren |
| --- | --- | ---: | --- |
| Formato de tiempo | `TimeFormatTest` | 5 | Duraciones: minutos, horas exactas, relleno con cero, valores negativos. |
| Modelos | `ModelsTest` | 5 | Conversión de enums desde Firestore (valores desconocidos o nulos) y nombres de campo guardados. |
| Rutas | `RouteMathTest` | 9 | Distancia, rumbo, punto más cercano, metros restantes y próximo objetivo de la flecha. |
| Caché de rutas | `RouteCacheTest` | 6 | Guardar/cargar, descartar si el vehículo se movió más de 10 m, archivo corrupto, limpieza a los 30 días. |
| Lectura de carteles (IA) | `SignAnalyzerParsingTest` | 15 | Interpretación del JSON de Gemini: veredictos, minutos máximos, formato de hora, textos vacíos, JSON inválido. |
| ViewModels | `AuthViewModelTest` | 13 | Validaciones de login y registro, estado de la sesión, errores de Firebase. |
| | `HomeViewModelTest` | 7 | Estado de la lista y qué avisos de vencimiento se programan. |
| | `MapViewModelTest` | 6 | Marcadores, permiso y posición. |
| | `ProfileViewModelTest` | 8 | Alta y borrado de vehículos, minutos de aviso, errores. |
| | `ParkingDetailViewModelTest` | 9 | Detalle, «Ya lo retiré», borrado, fotos. |
| | `ReturnToCarViewModelTest` | 19 | Distancia, rumbo, brújula, ruta por calles, ruta guardada sin conexión, intervalos entre pedidos (con reloj controlado). |
| | `ParkingFormViewModelTest` | 42 | Alta y edición, ubicación, vencimientos, validaciones, fotos y lectura del cartel con IA. |
| Pantallas Compose | `AuthGateTest` | 14 | Login, registro, recuperar contraseña, verificación de correo. |
| | `HomeScreenTest` | 12 | Estado vacío, tarjetas, historial y filtros combinados. |
| | `ProfileScreenTest` | 5 | Datos, vehículos, minutos de aviso, cerrar sesión, tema. |
| | `ParkingDetailScreenTest` | 8 | Datos del registro y confirmaciones de retirar y borrar. |
| | `ParkingFormScreenTest` | 16 | Formulario completo, edición y botones de IA. |
| | `ReturnToCarScreenTest` | 16 | Guía hacia el auto en modo directo y por calles. |
| | `MapScreenTest` | 4 | Permiso de ubicación y botón de centrar. |
| **Total Android** | | **219** | |
| Reglas de Firestore | `firestore-tests/rules.test.mjs` | 27 | Quién puede leer y escribir cada colección, validaciones de estado y fecha, límite de 900 KB de las fotos, y denegado por defecto. |

**Cómo se comprobó que los tests sirven.** Que un test pase no prueba que detecte errores. Después de escribir cada bloque se rompió a
propósito el código (o las reglas) y se verificó que los tests fallaran: por ejemplo, cambiar el umbral de llegada, el filtro
«Vencidos», el recorte de la nota, la validación del correo, el intervalo mínimo entre pedidos de ruta, o dejar las reglas abiertas
a cualquier usuario con sesión (fallaron 7 de los 27 tests de reglas). Cada rotura se revirtió con el archivo original.

#### Un error real encontrado

Escribir los tests del formulario destapó un error que ya existía: al **editar un estacionamiento que no existe** (por ejemplo, ya
borrado desde otro dispositivo), la pantalla se quedaba con el indicador de carga y nunca mostraba el mensaje de error. El
colector de vehículos recalculaba `loading = s.isEdit && original == null`, que volvía a dar `true` después del fallo. Se corrigió en
`ParkingFormViewModel` (`loading = s.loading && s.isEdit && original == null`: la bandera solo puede bajar) y quedó un test que lo
cubre. Está en su propio commit, separado del resto.

#### Decisiones y por qué se tomaron

- **Pirámide de tests: mucha lógica y ViewModels, pantallas por encima, reglas aparte.** Los tests de lógica son rápidos y baratos, así
  que ahí se puso la mayor parte; las pantallas se prueban con menos casos para verificar que el cableado funciona; las reglas de
  seguridad se prueban por separado porque son lo más delicado (protegen los datos de cada usuario) y no se ven desde la app.
- **Robolectric en lugar de tests instrumentados para las pantallas.** Corren en la JVM, sin emulador ni teléfono, así que se pueden
  ejecutar en cualquier computadora y en GitHub Actions sin configurar nada. El costo es que no prueban un dispositivo real
  (ver «Límites»). Se prefirió tener tests que se corren siempre antes que tests más fieles que casi nadie ejecuta.
- **Los ViewModels reciben sus dependencias por constructor, con valores por defecto.** Antes cada uno creaba sus repositorios adentro
  (`ParkingRepository()`), lo que los ataba a Firebase real y los hacía imposibles de probar. Ahora, por ejemplo,
  `HomeViewModel(app, parkings = ParkingRepository(), ...)`: la app los crea igual que siempre y los tests pasan repositorios simulados.
  Se aplicó a los seis ViewModels con pantalla (`Auth`, `Home`, `Map`, `ParkingDetail`, `ReturnToCar`, `ParkingForm`); `ProfileViewModel`
  ya estaba hecho así.
- **`@JvmOverloads` en esos constructores.** Compose crea los ViewModels con una fábrica por defecto que busca por reflexión un
  constructor exacto (`(Application)` o `(SavedStateHandle)`). Los valores por defecto de Kotlin no generan ese constructor, así que
  sin la anotación la app se habría caído al abrir cada pantalla. Se revisó que cada ViewModel se cree en un solo lugar
  (`MainActivity` o `AppNavHost`).
- **Un commit por ViewModel refactorizado.** Es el único cambio que toca código de producción, así que si alguno diera problemas en un
  teléfono se puede revertir ese commit sin perder los demás ni los tests.
- **Se extrajo el parseo de `SignAnalyzer` a funciones puras** (`parseSignAnalysis`, `cleanPlaceDescription`). Estaba mezclado con la
  llamada a Gemini y no se podía probar sin red. El código se movió sin cambiar su lógica; la llamada a Gemini no se prueba.
- **El reloj se inyecta en `ReturnToCarViewModel`** (`clock: () -> Long`, por defecto la hora del sistema). Sin eso no se podían probar
  los intervalos entre pedidos de ruta (15 s) ni el límite de 3 s entre toques de «Recalcular», que dependen del tiempo real.
- **Nunca se llama a servicios reales en los tests.** Ni Gemini (cuota y costo), ni Firestore real (red y datos), ni GPS, cámara o
  brújula. Todo se reemplaza con MockK. Las reglas de seguridad sí se prueban con Firestore, pero contra el **emulador** y con un
  proyecto de demostración, así que no hay riesgo para los datos reales.
- **Los tests de pantallas buscan textos con los recursos de la app** (`str(R.string...)`) y no con texto escrito a mano. Así no se
  rompen si se cambia una redacción y los resultados no dependen del idioma.
- **Configuración de Robolectric** (`app/src/test/resources/robolectric.properties`):
  - `sdk=36`: Robolectric todavía no soporta el `targetSdk 37` de la app.
  - `application=android.app.Application`: evita que cada test arranque `BalizApplication`, que inicializa Firebase.
  - `qualifiers=es-rAR-w411dp-h891dp-xxhdpi`: idioma español (el de la app; por defecto Robolectric usa inglés) y tamaño de un teléfono
    común (con la pantalla chica por defecto, los textos se medían colapsados y los tests de pantalla fallaban).
  - Memoria de los tests subida a 2 GB (`maxHeapSize`): Robolectric con Compose necesita más que el valor por defecto.
  - Solo `ParkingFormScreenTest` usa el modo gráfico nativo: es el único que necesita crear una imagen real (las fotos).
- **Herramientas elegidas:** MockK (se lleva bien con Kotlin, clases finales y funciones `suspend`), Turbine (para probar `StateFlow`
  sin esperas arbitrarias) y `kotlinx-coroutines-test`. Para las reglas, el ejecutor de tests integrado de Node (sin framework extra) y
  `@firebase/rules-unit-testing`.
- **Cada test de reglas cubre el caso permitido y el denegado**, incluidos los bordes (una foto de 900 KB menos un byte pasa; de
  900 KB justos no), porque una regla demasiado abierta no da ningún error visible.

#### Integración continua

`.github/workflows/tests.yml` tiene dos trabajos que corren en cada push a `main` y en cada pull request:

1. **`unit-tests`:** JDK 21, herramientas de Android y `./gradlew testDebugUnitTest assembleDebug` (corre los 219 tests y verifica que
   la app compile). Si falla, sube los reportes como artefacto.
2. **`firestore-rules`:** Node 22 y Java 21, `npm ci` y `npm test` en `firestore-tests/`.

> El workflow **todavía no se corrió en GitHub**: el primer pull request lo va a poner a prueba. Lo más probable que falle es la
> descarga de la plataforma Android 37 en el servidor; el error sería claro y se corrige en el paso de instalación del SDK.

#### Límites conocidos y qué no se probó

- **Diálogo de alta y edición de vehículo (Perfil):** un campo de texto dentro de un `Dialog` deja a Compose sin quedar nunca inactivo
  en Robolectric y el test se cuelga. Se probaron varias variantes (reloj manual, modo gráfico nativo, un solo campo) sin resolverlo.
  Por eso no hay test de pantalla para ese diálogo; su lógica (validación, patente en mayúsculas, borrado con confirmación) sí está
  cubierta en `ProfileViewModelTest`. Cubrirlo por pantalla requeriría un test instrumentado en un emulador.
- **Hardware y servicios reales:** cámara, galería, GPS, brújula, notificaciones y alarmas, y Gemini están simulados. Hay que
  verificarlos a mano en un teléfono.
- **Mapa:** se prueba que la pantalla se arme y reaccione a permiso y posición, pero no se pueden simular toques sobre los marcadores
  de osmdroid.
- **Sin tests directos:** los repositorios de Firestore (`ParkingRepository`, `UserRepository`, etc.; sus consultas solo se ejercitan
  simuladas), `ReminderScheduler`/`ReminderReceiver`, `ConnectivityObserver`, la navegación (`AppNavHost`) y el tema. Las reglas de
  seguridad sí se prueban contra el emulador.
- **Validación de la hora límite del cartel:** el parseo solo revisa la forma `H:MM`, así que acepta una hora imposible como `25:99`;
  después `ParkingFormViewModel` la descarta (con `runCatching`) y el usuario no ve ningún efecto. No hay un test específico para ese caso.

#### Antes de entregar o mergear

Los tests no reemplazan una prueba manual: el cambio de cómo se crean los ViewModels solo se ve de verdad al abrir cada pantalla en
un teléfono. Conviene recorrer: iniciar sesión y cerrar sesión, Inicio, Mapa, Perfil, crear y editar un estacionamiento con foto,
leer un cartel con IA, «Volver al auto» en ambos modos y el aviso de vencimiento.

#### Posibles mejoras

- Tests instrumentados (en emulador) para el diálogo de vehículo y para ejercitar cámara y permisos reales.
- Probar los repositorios contra el emulador de Firestore, que ya está disponible en `firestore-tests/`.
- Medir cobertura (por ejemplo, con JaCoCo) para ver qué falta.

### Lectura de carteles con IA

- **Seguimos con Gemini (Firebase AI Logic) y no pasamos a Claude.** La suscripción Claude Max es para usar Claude en claude.ai y
  Claude Code; no incluye acceso a la API ni se puede usar desde una app propia. Usar Claude requeriría una API key de pago
  aparte y un backend intermedio (una clave dentro del APK puede extraerse). Gemini ya está integrado con Firebase, sin servidor propio.
- **Modelo `gemini-2.5-flash`** (antes `gemini-2.5-flash-lite` y, antes, `gemini-3.8-flash`). El modelo "lite" es el más chico de
  la familia y lee peor texto en fotos, que era justo el problema (carteles que no se leían). Flash es el escalón siguiente con buena
  lectura de imágenes y costo bajo.
- **Fotos de hasta 2048 px de lado** (antes 1280). Se comprueba que la misma imagen que se guarda en Firestore es la que se manda a
  la IA, y a 1280 px el texto chico de un cartel (horarios, "máx. 2 h") quedaba borroso. Se sigue comprimiendo a menos de 700 KB
  para respetar el límite de 1 MiB por documento de Firestore; si una foto muy detallada no entra, la calidad baja hasta 30.
- **Errores de cuota explicados al usuario.** Cuando Google responde con límite de uso (429 / quota) la app muestra un mensaje corto
  y sugiere completar los datos a mano, en lugar de mostrar el texto crudo del error.

### Filtros en Inicio

- **Dos filtros combinables:** por vehículo ("Todos los vehículos" + uno por cada vehículo) y por estado (Todos, Actuales, Vencidos,
  Finalizados). Permiten encontrar rápido un estacionamiento cuando hay varios vehículos o un historial largo.
- **"Vencido" = estacionamiento activo cuyo vencimiento ya pasó.** La app no guarda un estado "vencido" aparte, así que se calcula con
  la hora actual; por eso un estacionamiento pasa de Actuales a Vencidos solo, sin recargar. "Finalizados" es el historial.
- **Se filtra en la pantalla**, sobre la lista que ya se descarga: no hay consultas nuevas a Firestore ni índices adicionales, y
  funciona sin conexión. El filtro se conserva al rotar la pantalla.

### Idiomas (español, inglés, italiano, portugués y alemán)

- **Selector en Perfil → Idioma.** Español queda como idioma por defecto (el que ya tenía la app); no se sigue el idioma del teléfono.
- **Implementación propia (`i18n/AppLocale.kt`) en lugar de las locales por app de AppCompat.** La app usa `FragmentActivity` (necesaria
  para BiometricPrompt) y el tema no es de AppCompat; las locales por app de Android solo cubren por completo el proceso desde Android 13
  y la app admite desde Android 8. Guardar el idioma en SharedPreferences y envolver el contexto de la Application y de la Activity
  funciona igual en todas las versiones y también para textos fuera de pantalla (ViewModels, notificaciones).
- **Cambio inmediato:** al elegir un idioma se recrea la pantalla; no hace falta reiniciar la app.
- **Todos los textos pasaron a recursos** (`res/values*/strings_*.xml`, un archivo por área con prefijo en las claves), en lugar de un
  único `strings.xml`, para poder repartir y revisar la traducción por partes sin conflictos. Los textos que antes estaban en enums
  (tipo de vehículo, tema, destinos de la barra inferior) guardan ahora el id del recurso para poder cambiar en tiempo de ejecución.
- **Fuera de las pantallas** (ViewModels, repositorios, notificaciones) se usa `AppLocale.str(...)`, que no necesita un `Context` y
  siempre resuelve en el idioma elegido; en Compose se usa `stringResource`.
- **Fechas, distancias y dirección** también siguen el idioma: los formateadores toman la locale elegida y el geocoder devuelve la
  dirección en ese idioma.
- **La IA responde en el idioma elegido.** Los prompts siguen en español porque los carteles son argentinos, pero se le pide que
  escriba la regla, la explicación y la descripción del lugar en el idioma de la app.
- **Las traducciones no fueron revisadas por hablantes nativos**; conviene una revisión, sobre todo en alemán e italiano. Los datos ya
  guardados (por ejemplo, un resumen de regla leído antes del cambio) no se traducen.

