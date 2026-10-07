package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.workouts

import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.ExerciseCategory
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GarminExerciseCatalogTest : TestBase() {
    @Test
    fun testParses() {
        val catalog = GarminExerciseCatalog.INSTANCE
        assertEquals(1764, catalog.exercises().size)
        assertTrue(catalog.exercises().all { it is GarminExercise })

        val squat = catalog.exerciseFor("squat/barbell_back_squat")!!
        assertEquals("Barbell Back Squat", squat.name)
        assertEquals(ExerciseCategory.CATEGORY_SQUAT, squat.category)
        assertEquals(6, squat.code)
        assertEquals(setOf(GarminExerciseLists.STRENGTH), squat.lists)

        // The category's own generic exercise, which has no exercise_name at all
        val benchPress = catalog.exerciseFor("bench_press")!!
        assertEquals("Bench Press", benchPress.name)
        assertEquals(ExerciseCategory.CATEGORY_BENCH_PRESS, benchPress.category)
        assertNull(benchPress.code)
    }

    @Test
    fun testLists() {
        val catalog = GarminExerciseCatalog.INSTANCE
        assertEquals(1548, catalog.exercises(GarminExerciseLists.STRENGTH).size)
        assertEquals(212, catalog.exercises(GarminExerciseLists.YOGA).size)
        assertEquals(212, catalog.exercises(GarminExerciseLists.PILATES).size)
        assertEquals(292, catalog.exercises(GarminExerciseLists.MOBILITY).size)
    }

    /**
     * The category filter is not in the file: its values are the [ExerciseCategory] enum, and
     * every exercise matches its own category.
     */
    @Test
    fun testFilters() {
        val catalog = GarminExerciseCatalog.INSTANCE
        assertEquals(listOf("category", "muscle", "equipment"), catalog.filters().map { it.id })
        assertEquals(
            listOf("category", "muscle", "equipment"),
            catalog.filters(GarminExerciseLists.STRENGTH).map { it.id }
        )
        assertTrue(catalog.filters(GarminExerciseLists.YOGA).isEmpty())

        val categories = catalog.filters().first { it.id == "category" }
        assertEquals(ExerciseCategory.entries.size - 1, categories.values.size)
        assertTrue(categories.values.none { it.id == ExerciseCategory.CATEGORY_UNKNOWN.name })
        assertTrue(catalog.exercises().all { it.filters["category"] == setOf((it as GarminExercise).category.name) })

        // Every value the file's filters declare must be one some exercise really matches
        for (filter in catalog.filters().filter { it.id != "category" }) {
            val used = catalog.exercises().flatMapTo(mutableSetOf()) { it.filters[filter.id].orEmpty() }
            assertEquals(filter.id, filter.values.map { it.id }.toSet(), used)
        }
    }

    /**
     * Each sport list holds exactly the names of the picker's own list for that sport.
     * Names are compared case-insensitively, since the picker shows a few of them twice,
     * in different case, but the catalog will only have one.
     */
    @Test
    fun testListsMatchThePickerLists() {
        val catalog = GarminExerciseCatalog.INSTANCE
        for (sport in pickerLists().listFiles { file -> file.isDirectory }!!) {
            val expected = readNames(File(sport, "all.txt"))
            val actual = catalog.exercises(sport.name).mapTo(mutableSetOf()) { it.name.lowercase() }
            assertEquals(sport.name, expected, actual)
        }
    }

    /**
     * This test compares the garmin.json catalog against the txt files under app/src/test/resources/garmin/exercises
     *
     * Validate that:
     * - all exercises between the txt files and garmin.json match (no extras on either side)
     * - all exercises that should belong to a filter are tagged as such in garmin.json
     * - no filter contains extra exercises that it should not
     */
    @Test
    fun testFilterValuesMatchThePickerLists() {
        val catalog = GarminExerciseCatalog.INSTANCE
        val folders = mapOf("muscle" to "muscle_groups", "equipment" to "equipment")
        for ((filterId, folder) in folders) {
            val listed = mutableMapOf<String, MutableSet<String>>()  // value id -> names of every sport
            for (sport in pickerLists().listFiles { file -> file.isDirectory }!!) {
                val files = File(sport, folder).listFiles { file -> file.extension == "txt" } ?: continue
                for (file in files) {
                    val valueId = file.nameWithoutExtension.replace("-", "_")
                    val expected = readNames(file)
                    val tagged = catalog.exercises(sport.name)
                        .filter { valueId in it.filters[filterId].orEmpty() }
                        .mapTo(mutableSetOf()) { it.name.lowercase() }
                    assertTrue("$sport/$folder/$valueId: ${expected - tagged}", tagged.containsAll(expected))
                    listed.getOrPut(valueId) { mutableSetOf() } += expected
                }
            }
            for (value in catalog.filters().first { it.id == filterId }.values) {
                val tagged = catalog.exercises()
                    .filter { value.id in it.filters[filterId].orEmpty() }
                    .mapTo(mutableSetOf()) { it.name.lowercase() }
                assertEquals("$filterId/${value.id}", listed[value.id], tagged)
            }
        }
    }

    /**
     * An `exercise_name` is only unique within its category, so the pair has to be unique.
     */
    @Test
    fun testCategoryAndCodeAreUnique() {
        val exercises = GarminExerciseCatalog.INSTANCE.exercises().map { it as GarminExercise }
        val pairs = exercises.map { it.category to it.code }
        assertEquals(exercises.size, pairs.toSet().size)
        assertEquals(exercises.size, exercises.map { it.id }.toSet().size)
    }

    /**
     * The id is the FIT profile's own names, `category/key` in lower case, or the category alone
     * for its generic exercise. The category and the code must match this.
     */
    @Test
    fun testIdsMatchTheFitProfile() {
        val enumPackage = ExerciseCategory::class.java.`package`!!.name
        for (exercise in GarminExerciseCatalog.INSTANCE.exercises().map { it as GarminExercise }) {
            val (category, key) = exercise.id.split("/").let { it[0] to it.getOrNull(1) }
            assertEquals(exercise.id, "CATEGORY_" + category.uppercase(), exercise.category.name)
            if (key == null) {
                assertNull(exercise.id, exercise.code)
                continue
            }
            val enumName =
                category.split("_").joinToString("") { it.replaceFirstChar(Char::uppercase) } + "ExerciseName"
            val entries = Class.forName("$enumPackage.$enumName").enumConstants!!.map { it as Enum<*> }
            // A key that starts with a digit has a leading underscore in the enum, which the id drops
            val entry = entries.firstOrNull {
                it.name.equals(key, ignoreCase = true) || it.name.equals(
                    "_$key",
                    ignoreCase = true
                )
            }
            assertNotNull("${exercise.id}: no such $enumName", entry)
            assertEquals(exercise.id, entry!!.javaClass.getField("num").getInt(entry), exercise.code)
        }
    }

    @Test
    fun testUnknownIdResolvesToNull() {
        assertNull(GarminExerciseCatalog.INSTANCE.byId("no_such_exercise"))
        assertNull(GarminExerciseCatalog.INSTANCE.byId(null))
    }

    private fun pickerLists(): File = File(javaClass.getResource("/garmin/exercises")!!.toURI())

    private fun readNames(file: File): Set<String> =
        file.readLines().mapNotNullTo(mutableSetOf()) { line -> line.trim().lowercase().takeIf { it.isNotEmpty() } }
}
