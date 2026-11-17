# BalizApp (React Native)

Migración de la app Android nativa (Kotlin + Jetpack Compose) a **React Native con Expo (development build) y TypeScript**.
Guarda dónde dejaste el auto o la moto, controla cuánto tiempo podés estar ahí y te guía a pie de vuelta.

Usa **el mismo proyecto de Firebase y la misma estructura de Firestore** que la app Kotlin (mismos nombres de campo y valores),
así que las dos apps leen y escriben los mismos datos y las reglas `firestore.rules` no cambian.

## Cómo ejecutarla

No funciona en Expo Go: usa módulos nativos (Firebase nativo, MapLibre, biometría), así que se compila como app Android.

**Para ver la app funcionando** (versión _release_: lleva el JavaScript empaquetado, no necesita servidor de Metro ni conexión a la PC):

```bash
npm install
cp .env.example .env                 # completar EXPO_PUBLIC_MAPTILER_KEY (ver abajo)
npm run prebuild                     # genera android/
npx expo run:android --variant release   # compila e instala en el teléfono/emulador conectado por USB (necesita Android Studio / SDK)
```

Si ya hay un teléfono conectado con la depuración USB activada, queda instalada y se abre sola. El `.env` se incorpora al compilar:
si se cambia una clave hay que volver a compilar.

**Para desarrollar** (recarga en vivo con _development build_):

```bash
npm run android             # compila e instala el development build
npm start                   # servidor de Metro (en un teléfono por USB: adb reverse tcp:8081 tcp:8081)
```

Alternativa en la nube: `npx eas build --profile development --platform android` (requiere cuenta de Expo).

### Configuración (una sola vez)

1. **Firebase / `google-services.json`**: ya está en la raíz (mismo paquete `com.example.balizapp`). Android: nada más que hacer.
   iOS: bajar `GoogleService-Info.plist` y agregar `"googleServicesFile": "./GoogleService-Info.plist"` en `app.json > ios`.
2. **Ingreso con Google**: la huella SHA-1 del keystore con el que se firma tiene que estar registrada en Firebase. La app Kotlin firmaba
   con `app/debug-team.keystore` (`F5:DE:06:5D:E0:BE:97:2E:04:EE:C9:5C:1A:66:71:9F:03:07:20:BD`). `expo prebuild` genera su propio
   `android/app/debug.keystore`: o bien se registra su SHA-1 (`cd android && ./gradlew signingReport`), o se copia
   `debug-team.keystore` encima de `android/app/debug.keystore`.
3. **Mapa**: `EXPO_PUBLIC_MAPTILER_KEY` (equivale a `MAPTILER_KEY` de `local.properties`). Sin clave se usan los mosaicos de OpenStreetMap,
   que funcionan pero **no permiten precargar el mapa sin conexión**.
4. **Firestore**: publicar `firestore.rules` (`firebase deploy --only firestore:rules`), igual que antes.

## Tests y verificación

```bash
npm test            # 103 tests de Jest (lógica pura portada de los tests Kotlin + lógica nueva)
npm run typecheck   # tsc --noEmit
```

## Equivalencias Kotlin → React Native

| Área          | App Kotlin                                 | App React Native                                                                                                               |
| ------------- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------ |
| UI            | Jetpack Compose + Material 3               | React Native + componentes propios (`src/components/ui.tsx`) con la misma paleta                                               |
| Navegación    | Navigation Compose, rutas tipadas          | React Navigation (stack + tabs), rutas tipadas en `AppNavigator.tsx`                                                           |
| Arquitectura  | MVVM, `ViewModel` + `StateFlow`            | Hooks (`useParkingForm`, `useReturnToCar`, `useData`) + lógica pura testeable (`formLogic`, `returnToCarLogic`, `homeFilters`) |
| Auth          | Firebase Auth + Credential Manager         | `@react-native-firebase/auth` + `@react-native-google-signin/google-signin`                                                    |
| Huella        | `BiometricPrompt`                          | `expo-local-authentication`                                                                                                    |
| Base de datos | Firestore con cache persistente            | `@react-native-firebase/firestore` (cache persistente nativo, offline igual que antes)                                         |
| Fotos         | JPEG comprimido dentro de Firestore        | Igual (`expo-image-picker` + `expo-image-manipulator`, base64 ↔ `Bytes`)                                                       |
| Ubicación     | `FusedLocationProviderClient` + `Geocoder` | `expo-location` (posición, geocodificación inversa)                                                                            |
| Brújula       | Magnetómetro + acelerómetro + declinación  | `expo-location` `watchHeadingAsync` (rumbo ya corregido al norte geográfico) + `expo-sensors` para detectar si hay sensor      |
| Mapa          | osmdroid + MapTiler                        | `@maplibre/maplibre-react-native` + MapTiler/OSM; paquetes offline con `OfflineManager`                                        |
| Rutas a pie   | OSRM (FOSSGIS)                             | Igual (`routeRepository.ts`), con el mismo intervalo mínimo, recálculo al desviarse y recorrido guardado (AsyncStorage)        |
| Avisos        | `AlarmManager` + `BroadcastReceiver`       | `expo-notifications` con notificaciones programadas (reprogramadas al reiniciar)                                               |
| Tema / idioma | `AppLocale` + `values-xx/`                 | `src/i18n` con los **mismos 270 textos en 5 idiomas** (convertidos del XML con `tools/xml2json.py`)                            |
| Conectividad  | `ConnectivityManager`                      | `@react-native-community/netinfo`                                                                                              |

## Diferencias a tener en cuenta

- **Alarmas exactas (Android 12+)**: `expo-notifications` usa alarmas exactas si el usuario concedió "Alarmas y recordatorios". No hay forma de consultar
  ese permiso desde JavaScript, así que Perfil muestra siempre el botón "Permitir alarmas exactas" en Android 12+.
- **Brújula**: el rumbo viene de `expo-location` (usa el mismo sensor), no del filtro pasa-bajos propio de la versión Kotlin. Si la flecha se ve más
  nerviosa o más lenta de lo esperado, conviene comparar y, de ser necesario, suavizarla en `useReturnToCar`.
- **Mapa sin conexión**: el servidor de OpenStreetMap no permite descargas masivas, por eso la precarga solo se hace con una clave de MapTiler.
- **Tests de pantallas Compose (≈110) y de reglas de Firestore**: los de pantallas no se portaron (cambia la tecnología de UI); los de reglas siguen
  valiendo tal cual (viven en `firestore-tests/` del repo original).

## Estructura

```
App.tsx                      Proveedores (idioma, tema, sesión) y guardia de login
src/auth/                    Sesión, huella, validaciones y pantallas de login/registro/verificación
src/data/                    Modelos, repositorios de Firestore, ubicación, brújula, rutas, fotos
src/notifications/           Avisos de vencimiento
src/navigation/              Tabs + stack, sincronización de avisos, toque en notificación
src/screens/                 Inicio, Mapa, Perfil, Formulario, Detalle, Volver al auto (+ lógica pura)
src/components/              UI compartida, mapa, mosaicos offline
src/i18n/                    Textos en es / en / it / pt / de
__tests__/                   Jest
```
