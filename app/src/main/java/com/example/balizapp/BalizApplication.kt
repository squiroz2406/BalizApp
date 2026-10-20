package com.example.balizapp

import android.app.Application
import android.content.Context
import com.example.balizapp.i18n.AppLocale
import com.example.balizapp.ui.components.configureOsmdroid
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/** Configura, antes de que nadie los use, el cache local de Firestore y el de los mosaicos del mapa. */
class BalizApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLocale.init(this)
        // Cache en disco de Firestore: permite leer y escribir sin conexión. Las escrituras
        // pendientes nunca se descartan, aunque se alcance el límite; solo se purgan datos ya sincronizados.
        FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder().setSizeBytes(FIRESTORE_CACHE_BYTES).build()
            )
            .build()
        configureOsmdroid(this)
    }

    private companion object {
        const val FIRESTORE_CACHE_BYTES = 200L * 1024 * 1024
    }
}
