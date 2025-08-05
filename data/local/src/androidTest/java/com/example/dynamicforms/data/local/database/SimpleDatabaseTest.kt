package com.example.dynamicforms.data.local.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dynamicforms.data.local.entity.FormEntity
import com.example.dynamicforms.data.local.entity.FormEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SimpleDatabaseTest {

    private lateinit var database: DynamicFormsDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DynamicFormsDatabase::class.java
        ).build()
    }

    @After
    @Throws(IOException::class)
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun testDatabaseCreationAndBasicOperations() = runBlocking {
        // Test database creation
        assertNotNull("Database should be created", database)
        assertNotNull("FormDao should be available", database.formDao())
        assertNotNull("FormEntryDao should be available", database.formEntryDao())

        // Test empty database
        val initialFormCount = database.formDao().getFormCount()
        assertEquals("Database should start empty", 0, initialFormCount)

        // Test form insertion
        val testForm = FormEntity(
            id = "test-form-1",
            title = "Test Form",
            fieldsJson = """[{"id":"field1","type":"text","label":"Test Field"}]""",
            sectionsJson = """[{"id":"section1","title":"Test Section","fields":["field1"]}]""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        database.formDao().insertForm(testForm)

        // Verify form was inserted
        val formCount = database.formDao().getFormCount()
        assertEquals("Form should be inserted", 1, formCount)

        val retrievedForm = database.formDao().getFormById("test-form-1").first()
        assertNotNull("Form should be retrievable", retrievedForm)
        assertEquals("Form title should match", "Test Form", retrievedForm?.title)

        // Test form entry insertion
        val testEntry = FormEntryEntity(
            id = "test-entry-1",
            formId = "test-form-1",
            fieldValuesJson = """{"field1":"test value"}""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isComplete = true,
            isDraft = false
        )

        database.formEntryDao().insertEntry(testEntry)

        // Verify entry was inserted
        val retrievedEntry = database.formEntryDao().getEntryById("test-entry-1").first()
        assertNotNull("Entry should be retrievable", retrievedEntry)
        assertEquals("Entry form ID should match", "test-form-1", retrievedEntry?.formId)

        // Test relationship
        val formEntries = database.formEntryDao().getEntriesForForm("test-form-1").first()
        assertEquals("Should have one entry for the form", 1, formEntries.size)
    }

    @Test
    fun testCascadeDelete() = runBlocking {
        // Insert form and entry
        val form = FormEntity(
            id = "cascade-test-form",
            title = "Cascade Test Form",
            fieldsJson = """[{"id":"field1","type":"text"}]""",
            sectionsJson = """[{"id":"section1","fields":["field1"]}]""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val entry = FormEntryEntity(
            id = "cascade-test-entry",
            formId = "cascade-test-form",
            fieldValuesJson = """{"field1":"value"}""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isComplete = true,
            isDraft = false
        )

        database.formDao().insertForm(form)
        database.formEntryDao().insertEntry(entry)

        // Verify both exist
        assertNotNull(database.formDao().getFormById("cascade-test-form").first())
        assertNotNull(database.formEntryDao().getEntryById("cascade-test-entry").first())

        // Delete form
        database.formDao().deleteForm("cascade-test-form")

        // Verify cascade delete worked
        assertNull("Form should be deleted", database.formDao().getFormById("cascade-test-form").first())
        assertNull("Entry should be cascade deleted", database.formEntryDao().getEntryById("cascade-test-entry").first())
    }

    @Test
    fun testDraftEntryOperations() = runBlocking {
        // Insert form
        val form = FormEntity(
            id = "draft-test-form",
            title = "Draft Test Form",
            fieldsJson = """[{"id":"field1","type":"text"}]""",
            sectionsJson = """[{"id":"section1","fields":["field1"]}]""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.formDao().insertForm(form)

        // Insert draft entry
        val draftEntry = FormEntryEntity(
            id = "draft-entry",
            formId = "draft-test-form",
            fieldValuesJson = """{"field1":"draft value"}""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isComplete = false,
            isDraft = true
        )
        database.formEntryDao().saveDraftEntry(draftEntry)

        // Test draft retrieval
        val retrievedDraft = database.formEntryDao().getDraftEntry("draft-test-form").first()
        assertNotNull("Draft should be retrievable", retrievedDraft)
        assertTrue("Entry should be marked as draft", retrievedDraft?.isDraft == true)
        assertFalse("Entry should not be complete", retrievedDraft?.isComplete == true)

        // Test that draft appears in all entries (new behavior)
        val allEntries = database.formEntryDao().getEntriesForForm("draft-test-form").first()
        assertEquals("Draft should appear in all entries", 1, allEntries.size)
        assertTrue("Entry should be a draft", allEntries[0].isDraft)

        // Test draft deletion
        database.formEntryDao().deleteDraftEntry("draft-test-form")
        val deletedDraft = database.formEntryDao().getDraftEntry("draft-test-form").first()
        assertNull("Draft should be deleted", deletedDraft)
    }

    @Test
    fun testBulkOperations() = runBlocking {
        // Test bulk form insertion
        val forms = (1..10).map { i ->
            FormEntity(
                id = "bulk-form-$i",
                title = "Bulk Form $i",
                fieldsJson = """[{"id":"field$i","type":"text"}]""",
                sectionsJson = """[{"id":"section$i","fields":["field$i"]}]""",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        database.formDao().insertForms(forms)

        val formCount = database.formDao().getFormCount()
        assertEquals("All forms should be inserted", 10, formCount)

        val allForms = database.formDao().getAllForms().first()
        assertEquals("All forms should be retrievable", 10, allForms.size)

        // Test cleanup
        database.formDao().deleteAllForms()
        val finalCount = database.formDao().getFormCount()
        assertEquals("All forms should be deleted", 0, finalCount)
    }
}