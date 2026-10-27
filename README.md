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

| Qué | Dónde | Cómo correrlo |
| --- | --- | --- |
| ViewModels, lógica de rutas, modelos, parseo de la IA | `app/src/test/java/.../{data,model,ui/screens}` | `./gradlew testDebugUnitTest` |
| Pantallas Compose (Autenticación, Inicio, Perfil, Detalle, Formulario, Volver al auto, Mapa) | `app/src/test/java/.../ui/compose` | `./gradlew testDebugUnitTest` |
| Reglas de seguridad de Firestore | `firestore-tests/` | `cd firestore-tests && npm ci && npm test` |

- Los tests de pantallas usan Robolectric: montan la pantalla real con su ViewModel real y repositorios simulados (MockK), así que
  no necesitan emulador ni teléfono. Los ViewModels reciben sus dependencias por constructor (con valores por defecto, para que la
  app los cree igual que siempre).
- **Límite conocido:** un campo de texto dentro de un `Dialog` deja a Compose sin quedar nunca inactivo en Robolectric y el test se
  cuelga. Por eso no se prueba por pantalla el diálogo de alta/edición de vehículo; su lógica está cubierta en `ProfileViewModelTest`.
  Tampoco se prueban cámara, galería, GPS ni brújula reales: hay que mirarlos en un teléfono.
- Los tests de reglas necesitan Node 20+ y Java 21+ (el emulador de Firestore se descarga la primera vez). Se corren con un
  proyecto de demostración (`demo-balizapp`), sin tocar el de verdad.
- GitHub Actions corre todo esto en cada push a `main` y en cada pull request (`.github/workflows/tests.yml`).

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

Para el offline se usó el cache persistente de Firestore en lugar de Room (no hace falta una base aparte).
La carga de fotos en "Nuevo estacionamiento" muestra solo los botones Cámara y Galería hasta que haya una foto.

## Cambios recientes y por qué se tomaron

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

