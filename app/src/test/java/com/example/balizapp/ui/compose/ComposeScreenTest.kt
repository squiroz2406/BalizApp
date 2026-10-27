package com.example.balizapp.ui.compose

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import android.app.Application
import com.example.balizapp.i18n.AppLocale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Base de los tests de pantallas Compose. Corren en la JVM con Robolectric (no hace falta
 * emulador): se monta la pantalla real, con su ViewModel real y repositorios simulados.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
abstract class ComposeScreenTest {
    @get:Rule val rule: AndroidComposeTestRule<*, ComponentActivity> = createAndroidComposeRule<ComponentActivity>()

    protected lateinit var app: Application

    @Before fun setUpBase() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        AppLocale.init(app)
    }

    @After fun tearDownBase() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    /** Textos tal como los ve la pantalla (mismo contexto que usa stringResource). */
    protected fun str(@StringRes id: Int, vararg args: Any): String = rule.activity.getString(id, *args)
}
