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
| Autenticación | Firebase Authentication (email y contraseña, Google) |
| Base de datos | Cloud Firestore |
| Imágenes | JPEG comprimido dentro de Firestore (sin Storage: no requiere plan Blaze) |
| Mapa y ubicación | OpenStreetMap (osmdroid), FusedLocationProviderClient; rutas a pie con OSRM (servidor FOSSGIS, sin clave) |
| Sensores | Magnetómetro + acelerómetro (brújula con filtro y corrección por declinación magnética) |
| Notificaciones | Locales con AlarmManager (exactas, con respaldo inexacto), reprogramadas al reiniciar |
| Agente de IA | Firebase AI Logic (Gemini) |

## Cómo ejecutarlo

1. Clonar el repositorio y abrirlo con una versión reciente de Android Studio (el proyecto usa AGP 9).
2. **`google-services.json`** no está en el repositorio. Descargarlo desde la consola de Firebase
   (Configuración del proyecto → Tus apps → Android `com.example.balizapp`) y copiarlo en `app/`.
3. **Ingreso con Google:** el build de depuración firma con `app/debug-team.keystore`, una clave compartida por
   todo el grupo, así la huella es la misma en cualquier computadora. Su SHA-1 tiene que estar registrada una vez en
   la consola de Firebase (Configuración del proyecto → Tus apps → Android → Agregar huella digital):
   `F5:DE:06:5D:E0:BE:97:2E:04:EE:C9:5C:1A:66:71:9F:03:07:20:BD`.
   Se puede verificar con `./gradlew signingReport` (variante `debug`).
4. **Mapas:** se usa OpenStreetMap (biblioteca osmdroid), que no necesita clave de API ni cuenta de facturación.
5. En la consola de Firebase, activar:
   - Authentication → proveedores **Correo electrónico/contraseña** y **Google**.
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
├─ data/          Repositorios de Firestore y modelos (data/model)
├─ navigation/    Rutas tipadas, NavHost y barra inferior
├─ notifications/ Alarmas y notificaciones de vencimiento
├─ ui/screens/    Una pantalla por archivo
├─ ui/components/ Piezas de UI reutilizables
└─ ui/theme/      Colores, tipografía y tema
firestore.rules   Reglas de Firestore (incluye las fotos)
```

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
| 8 | Agente de IA | Pendiente |
| 9 | Cache offline con Room (opcional) | Pendiente |
