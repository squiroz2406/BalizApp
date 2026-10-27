package com.example.balizapp.data

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** org.json es una versión vacía en un test JVM común: Robolectric trae la real. */
@RunWith(RobolectricTestRunner::class)
class SignAnalyzerParsingTest {
    private fun parse(json: String) = parseSignAnalysis(json)!!

    @Test fun `un cartel completo se interpreta campo por campo`() {
        val analysis = parse(
            """{"readable":true,"ruleSummary":"Medido, max. 2 h","verdict":"allowed","maxMinutes":120,
               "allowedUntil":"18:00","explanation":"Es dia habil","confidence":"high"}"""
        )
        assertEquals(
            SignAnalysis("Medido, max. 2 h", ParkingVerdict.ALLOWED, 120, "18:00", "Es dia habil", "high"),
            analysis,
        )
    }

    @Test fun `si no es legible devuelve null`() {
        assertNull(parseSignAnalysis("""{"readable":false,"verdict":"allowed"}"""))
    }

    @Test fun `sin el campo readable se considera ilegible`() {
        assertNull(parseSignAnalysis("""{"verdict":"allowed","ruleSummary":"algo"}"""))
    }

    @Test fun `el veredicto forbidden se interpreta`() =
        assertEquals(ParkingVerdict.FORBIDDEN, parse("""{"readable":true,"verdict":"forbidden"}""").verdict)

    @Test fun `un veredicto desconocido o ausente es UNKNOWN`() {
        assertEquals(ParkingVerdict.UNKNOWN, parse("""{"readable":true,"verdict":"quizas"}""").verdict)
        assertEquals(ParkingVerdict.UNKNOWN, parse("""{"readable":true}""").verdict)
    }

    @Test fun `maximo de minutos nulo, cero o negativo se descarta`() {
        assertNull(parse("""{"readable":true,"maxMinutes":null}""").maxMinutes)
        assertNull(parse("""{"readable":true,"maxMinutes":0}""").maxMinutes)
        assertNull(parse("""{"readable":true,"maxMinutes":-30}""").maxMinutes)
        assertNull(parse("""{"readable":true}""").maxMinutes)
    }

    @Test fun `maximo de minutos positivo se conserva`() =
        assertEquals(45, parse("""{"readable":true,"maxMinutes":45}""").maxMinutes)

    @Test fun `la hora limite acepta H colon MM y HH colon MM`() {
        assertEquals("8:00", parse("""{"readable":true,"allowedUntil":"8:00"}""").allowedUntil)
        assertEquals("18:30", parse("""{"readable":true,"allowedUntil":"18:30"}""").allowedUntil)
    }

    @Test fun `una hora limite con otro formato se descarta`() {
        listOf("18h", "18:30 hs", "tarde", "1830", "18:3").forEach {
            assertNull("'$it' debe descartarse", parse("""{"readable":true,"allowedUntil":"$it"}""").allowedUntil)
        }
    }

    @Test fun `los textos vacios, en blanco o nulos quedan en null y se recortan`() {
        val analysis = parse(
            """{"readable":true,"ruleSummary":"   ","explanation":null,"allowedUntil":""}"""
        )
        assertNull(analysis.ruleSummary)
        assertNull(analysis.explanation)
        assertNull(analysis.allowedUntil)
        assertEquals("regla", parse("""{"readable":true,"ruleSummary":"  regla  "}""").ruleSummary)
    }

    @Test fun `sin confianza informada se asume low`() =
        assertEquals("low", parse("""{"readable":true}""").confidence)

    @Test fun `un JSON invalido lanza una excepcion`() {
        assertThrows(JSONException::class.java) { parseSignAnalysis("esto no es json") }
    }

    // --- Descripción del lugar ---

    @Test fun `la descripcion se recorta y se le quitan las comillas`() =
        assertEquals("Frente a la farmacia", cleanPlaceDescription("  \"Frente a la farmacia\"  "))

    @Test fun `una descripcion nula o vacia es null`() {
        assertNull(cleanPlaceDescription(null))
        assertNull(cleanPlaceDescription("   "))
        assertNull(cleanPlaceDescription("\"\""))
    }

    @Test fun `una descripcion normal queda igual`() =
        assertEquals("Junto a un arbol", cleanPlaceDescription("Junto a un arbol"))
}
