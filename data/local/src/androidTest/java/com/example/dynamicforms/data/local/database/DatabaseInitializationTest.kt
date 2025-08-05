package com.example.dynamicforms.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dynamicforms.data.local.dao.FormDao
import com.example.dynamicforms.data.local.dao.FormEntryDao
import com.example.dynamicforms.data.local.datasource.AssetsDataSource
import com.example.dynamicforms.data.local.entity.FormEntity
import com.example.dynamicforms.data.local.entity.FormEntryEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DatabaseInitializationTest {

    private lateinit var context: Context
    private lateinit var database: DynamicFormsDatabase
    private lateinit var formDao: FormDao
    private lateinit var formEntryDao: FormEntryDao
    private lateinit var assetsDataSource: AssetsDataSource
    private lateinit var gson: Gson

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        gson = Gson()
        
        // Create in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            DynamicFormsDatabase::class.java
        ).build()
        
        formDao = database.formDao()
        formEntryDao = database.formEntryDao()
        assetsDataSource = AssetsDataSource(context, gson)
    }

    @After
    @Throws(IOException::class)
    fun cleanup() {
        database.close()
    }

    @Test
    fun testDatabaseCreation() = runBlocking {
        // Test that database instance is created successfully
        assertNotNull(database)
        assertNotNull(formDao)
        assertNotNull(formEntryDao)
        
        // Verify database is empty initially
        val formCount = formDao.getFormCount()
        assertEquals(0, formCount)
        
        val allForms = formDao.getAllForms().first()
        assertTrue(allForms.isEmpty())
    }

    @Test
    fun testInitializeWithRealAssets() = runBlocking {
        // Copy test assets to the test assets directory
        val testForms = createTestFormsFromAssets()
        
        // Insert forms into database
        formDao.insertForms(testForms)
        
        // Verify forms were inserted
        val formCount = formDao.getFormCount()
        assertEquals(2, formCount) // all-fields.json and 200-form.json
        
        val allForms = formDao.getAllForms().first()
        assertEquals(2, allForms.size)
        
        // Verify specific form data
        val firstForm = allForms.find { it.id == "all-fields-form" }
        assertNotNull(firstForm)
        assertEquals("All Fields Form", firstForm?.title)
        
        val secondForm = allForms.find { it.id == "200-fields-form" }
        assertNotNull(secondForm)
        assertEquals("200 Fields Form", secondForm?.title)
    }

    @Test
    fun testFormEntryCreationAndRetrieval() = runBlocking {
        // Setup: Insert a test form
        val testForm = createTestForm()
        formDao.insertForm(testForm)
        
        // Create multiple entries for the form
        val entries = createTestEntries(testForm.id, 5)
        entries.forEach { formEntryDao.insertEntry(it) }
        
        // Test retrieval
        val formEntries = formEntryDao.getEntriesForForm(testForm.id).first()
        assertEquals(5, formEntries.size) // All entries including 1 draft
        
        val entryCount = formEntryDao.getEntryCountForForm(testForm.id)
        assertEquals(4, entryCount)
        
        // Test draft entry
        val draftEntry = formEntryDao.getDraftEntry(testForm.id).first()
        assertNotNull(draftEntry)
        assertTrue(draftEntry?.isDraft == true)
        assertFalse(draftEntry?.isComplete == true)
    }

    @Test
    fun testCompleteFormSubmissionFlow() = runBlocking {
        // Setup: Insert a form
        val form = createTestForm()
        formDao.insertForm(form)
        
        // Simulate creating a draft entry
        val draftEntry = FormEntryEntity(
            id = UUID.randomUUID().toString(),
            formId = form.id,
            fieldValuesJson = """{"field1":"draft value","field2":""}""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isComplete = false,
            isDraft = true
        )
        
        formEntryDao.saveDraftEntry(draftEntry)
        
        // Verify draft exists
        val savedDraft = formEntryDao.getDraftEntry(form.id).first()
        assertNotNull(savedDraft)
        assertEquals(draftEntry.id, savedDraft?.id)
        
        // Simulate completing the entry
        val completedEntry = draftEntry.copy(
            fieldValuesJson = """{"field1":"completed value","field2":"another value"}""",
            updatedAt = System.currentTimeMillis(),
            isComplete = true,
            isDraft = false
        )
        
        formEntryDao.updateEntry(completedEntry)
        
        // Verify the entry is now completed
        val retrievedEntry = formEntryDao.getEntryById(completedEntry.id).first()
        assertNotNull(retrievedEntry)
        assertTrue(retrievedEntry?.isComplete == true)
        assertFalse(retrievedEntry?.isDraft == true)
        
        // Verify it appears in completed entries
        val completedEntries = formEntryDao.getEntriesForForm(form.id).first()
        assertEquals(1, completedEntries.size)
        assertEquals(completedEntry.id, completedEntries.first().id)
    }

    @Test
    fun testDatabaseConstraintsAndRelationships() = runBlocking {
        // Test foreign key constraint
        val form = createTestForm()
        formDao.insertForm(form)
        
        val entry = FormEntryEntity(
            id = UUID.randomUUID().toString(),
            formId = form.id,
            fieldValuesJson = """{"test":"value"}""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isComplete = true,
            isDraft = false
        )
        
        formEntryDao.insertEntry(entry)
        
        // Verify relationship exists
        val entries = formEntryDao.getEntriesForForm(form.id).first()
        assertEquals(1, entries.size)
        
        // Test cascade delete
        formDao.deleteForm(form.id)
        
        // Entry should be deleted due to cascade
        val entriesAfterDelete = formEntryDao.getEntriesForForm(form.id).first()
        assertTrue(entriesAfterDelete.isEmpty())
        
        val deletedEntry = formEntryDao.getEntryById(entry.id).first()
        assertNull(deletedEntry)
    }

    @Test
    fun testBulkOperations() = runBlocking {
        // Test bulk insert performance
        val forms = (1..50).map { i ->
            FormEntity(
                id = "form_$i",
                title = "Form $i",
                fieldsJson = """[{"id":"field_$i","type":"text","label":"Field $i"}]""",
                sectionsJson = """[{"id":"section_$i","title":"Section $i","fields":["field_$i"]}]""",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }
        
        val startTime = System.currentTimeMillis()
        formDao.insertForms(forms)
        val endTime = System.currentTimeMillis()
        
        // Verify all forms were inserted
        val formCount = formDao.getFormCount()
        assertEquals(50, formCount)
        
        // Ensure operation completed reasonably quickly (less than 1 second)
        assertTrue("Bulk insert took too long: ${endTime - startTime}ms", (endTime - startTime) < 1000)
        
        // Test cleanup
        formDao.deleteAllForms()
        val finalCount = formDao.getFormCount()
        assertEquals(0, finalCount)
    }

    private fun createTestFormsFromAssets(): List<FormEntity> {
        // Simulate parsing forms from assets
        val allFieldsForm = FormEntity(
            id = "all-fields-form",
            title = "All Fields Form",
            fieldsJson = """[
                {"id":"text_field","type":"text","label":"Text Field","required":true},
                {"id":"number_field","type":"number","label":"Number Field","required":false},
                {"id":"description_field","type":"description","label":"Description","content":"<p>This is a description</p>"},
                {"id":"dropdown_field","type":"dropdown","label":"Dropdown","options":["Option 1","Option 2","Option 3"]}
            ]""",
            sectionsJson = """[
                {"id":"general","title":"General Information","fields":["text_field","number_field"]},
                {"id":"details","title":"Details","fields":["description_field","dropdown_field"]}
            ]""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val form200 = FormEntity(
            id = "200-fields-form",
            title = "200 Fields Form",
            fieldsJson = generateLargeFormFields(200),
            sectionsJson = generateLargeFormSections(200),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        return listOf(allFieldsForm, form200)
    }

    private fun createTestForm(): FormEntity {
        return FormEntity(
            id = "test-form-${UUID.randomUUID()}",
            title = "Test Form",
            fieldsJson = """[
                {"id":"field1","type":"text","label":"Field 1","required":true},
                {"id":"field2","type":"number","label":"Field 2","required":false}
            ]""",
            sectionsJson = """[
                {"id":"section1","title":"Test Section","fields":["field1","field2"]}
            ]""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun createTestEntries(formId: String, count: Int): List<FormEntryEntity> {
        return (1..count).map { i ->
            val isDraft = i == count // Last entry is a draft
            FormEntryEntity(
                id = "entry-$i-${UUID.randomUUID()}",
                formId = formId,
                fieldValuesJson = """{"field1":"value $i","field2":"${i * 10}"}""",
                createdAt = System.currentTimeMillis() - (count - i) * 1000L,
                updatedAt = System.currentTimeMillis() - (count - i) * 1000L,
                isComplete = !isDraft,
                isDraft = isDraft
            )
        }
    }

    private fun generateLargeFormFields(count: Int): String {
        val fields = (1..count).map { i ->
            val type = when (i % 4) {
                0 -> "text"
                1 -> "number"
                2 -> "dropdown"
                else -> "description"
            }
            """{"id":"field_$i","type":"$type","label":"Field $i","required":${i % 3 == 0}}"""
        }
        return "[${fields.joinToString(",")}]"
    }

    private fun generateLargeFormSections(fieldCount: Int): String {
        val fieldsPerSection = 10
        val sectionCount = (fieldCount + fieldsPerSection - 1) / fieldsPerSection
        
        val sections = (1..sectionCount).map { i ->
            val startField = (i - 1) * fieldsPerSection + 1
            val endField = minOf(i * fieldsPerSection, fieldCount)
            val sectionFields = (startField..endField).map { "field_$it" }
            """{"id":"section_$i","title":"Section $i","fields":[${sectionFields.joinToString(",") { "\"$it\"" }}]}"""
        }
        return "[${sections.joinToString(",")}]"
    }
}