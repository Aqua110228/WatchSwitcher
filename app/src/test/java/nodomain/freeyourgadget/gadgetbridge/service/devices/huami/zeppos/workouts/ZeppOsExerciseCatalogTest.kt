package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.workouts

import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeppOsExerciseCatalogTest : TestBase() {
    @Test
    fun testParses() {
        val catalog = ZeppOsExerciseCatalog.INSTANCE
        assertEquals(1133, catalog.exercises().size)
        assertEquals(1133, catalog.exercises(ZeppOsExerciseLists.STRENGTH).size)
        assertTrue(catalog.exercises().all { it is ZeppOsExercise })

        val benchPress = catalog.exerciseFor("bench_press")!!
        assertEquals(6, benchPress.actionType)
        assertEquals("Bench Press", benchPress.actionName)
        assertEquals(listOf(5, 6), benchPress.mainPositions)
        assertEquals(setOf("chest", "front_delts", "triceps"), benchPress.filters["muscle"])
    }

    @Test
    fun testFilters() {
        val catalog = ZeppOsExerciseCatalog.INSTANCE
        assertEquals(listOf("muscle"), catalog.filters().map { it.id })
        assertEquals(22, catalog.filters().single().values.size)
        val used = catalog.exercises().flatMapTo(mutableSetOf()) { it.filters["muscle"].orEmpty() }
        assertEquals(catalog.filters().single().values.map { it.id }.toSet(), used)
    }

    /**
     * An exercise with no position at all goes in "Others".
     */
    @Test
    fun testEveryExerciseHasAMuscle() {
        val exercises = ZeppOsExerciseCatalog.INSTANCE.exercises()
        assertTrue(exercises.all { it.filters["muscle"].orEmpty().isNotEmpty() })
        assertEquals(12, exercises.count { it.filters["muscle"] == setOf("others") })
    }

    /**
     * All actionTypes should be unique.
     */
    @Test
    fun testActionTypesAreUnique() {
        val exercises = ZeppOsExerciseCatalog.INSTANCE.exercises().map { it as ZeppOsExercise }
        assertEquals(exercises.size, exercises.map { it.actionType }.toSet().size)
    }
}
