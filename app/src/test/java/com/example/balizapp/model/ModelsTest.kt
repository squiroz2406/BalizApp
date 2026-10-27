package com.example.balizapp.model

import com.example.balizapp.data.model.ParkingSource
import com.example.balizapp.data.model.ParkingStatus
import com.example.balizapp.data.model.Vehicle
import com.example.balizapp.data.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    @Test fun `tipo de vehiculo desconocido o nulo cae en auto`() {
        assertEquals(VehicleType.CAR, VehicleType.fromFirestore(null))
        assertEquals(VehicleType.CAR, VehicleType.fromFirestore("camion"))
    }

    @Test fun `tipo de vehiculo se lee del valor guardado`() =
        assertEquals(VehicleType.MOTORCYCLE, VehicleType.fromFirestore("motorcycle"))

    @Test fun `estado desconocido se considera activo`() {
        assertEquals(ParkingStatus.ACTIVE, ParkingStatus.fromFirestore(null))
        assertEquals(ParkingStatus.FINISHED, ParkingStatus.fromFirestore("finished"))
    }

    @Test fun `origen desconocido se considera manual`() {
        assertEquals(ParkingSource.MANUAL, ParkingSource.fromFirestore("otro"))
        assertEquals(ParkingSource.AGENT_SIGN, ParkingSource.fromFirestore("agent_sign"))
    }

    @Test fun `vehiculo se guarda con los nombres de campo documentados`() {
        val map = Vehicle(id = "v1", type = VehicleType.MOTORCYCLE, alias = "Moto", plate = "AB123CD").toFirestore()
        assertEquals(mapOf("type" to "motorcycle", "alias" to "Moto", "plate" to "AB123CD"), map)
    }
}
