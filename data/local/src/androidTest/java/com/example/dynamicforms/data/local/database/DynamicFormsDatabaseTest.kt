package com.example.dynamicforms.data.local.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dynamicforms.data.local.dao.FormDao
import com.example.dynamicforms.data.local.dao.FormEntryDao
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
class DynamicFormsDatabaseTest {

    private lateinit var formDao: FormDao
    private lateinit var formEntryDao: FormEntryDao
    private lateinit var database: DynamicFormsDatabase

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DynamicFormsDatabase::class.java
        ).build()
        formDao = database.formDao()
        formEntryDao = database.formEntryDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun testDatabaseInitialization() = runBlocking {
        // Test that database is properly initialized
        assertNotNull(database)
        assertNotNull(formDao)
        assertNotNull(formEntryDao)
        
        // Test that tables are empty initially
        val formCount = formDao.getFormCount()
        assertEquals(0, formCount)
    }

    @Test
    fun testInsertAndGetForm() = runBlocking {
        // Given
        val form = createTestForm("form1", "Test Form")
        
        // When
        formDao.insertForm(form)
        
        // Then
        val retrievedForm = formDao.getFormById("form1").first()
        assertNotNull(retrievedForm)
        assertEquals("form1", retrievedForm?.id)
        assertEquals("Test Form", retrievedForm?.title)
        assertEquals(form.fieldsJson, retrievedForm?.fieldsJson)
        assertEquals(form.sectionsJson, retrievedForm?.sectionsJson)
    }

    @Test
    fun testInsertMultipleForms() = runBlocking {
        // Given
        val forms = listOf(
            createTestForm("form1", "Form 1"),
            createTestForm("form2", "Form 2"),
            createTestForm("form3", "Form 3")
        )
        
        // When
        formDao.insertForms(forms)
        
        // Then
        val allForms = formDao.getAllForms().first()
        assertEquals(3, allForms.size)
        assertEquals(3, formDao.getFormCount())
    }

    @Test
    fun testInsertAndGetFormEntry() = runBlocking {
        // Given - First insert a form
        val form = createTestForm("form1", "Test Form")
        formDao.insertForm(form)
        
        val formEntry = createTestFormEntry("entry1", "form1")
        
        // When
        formEntryDao.insertEntry(formEntry)
        
        // Then
        val retrievedEntry = formEntryDao.getEntryById("entry1").first()
        assertNotNull(retrievedEntry)
        assertEquals("entry1", retrievedEntry?.id)
        assertEquals("form1", retrievedEntry?.formId)
        assertEquals(formEntry.fieldValuesJson, retrievedEntry?.fieldValuesJson)
    }

    @Test
    fun testFormEntryRelationship() = runBlocking {
        // Given
        val form = createTestForm("form1", "Test Form")
        formDao.insertForm(form)
        
        val entries = listOf(
            createTestFormEntry("entry1", "form1", isComplete = true),
            createTestFormEntry("entry2", "form1", isComplete = true),
            createTestFormEntry("entry3", "form1", isDraft = true)
        )
        
        // When
        entries.forEach { formEntryDao.insertEntry(it) }
        
        // Then
        val formEntries = formEntryDao.getEntriesForForm("form1").first()
        assertEquals(3, formEntries.size) // All entries including draft
        
        val entryCount = formEntryDao.getEntryCountForForm("form1")
        assertEquals(2, entryCount)
        
        val draftEntry = formEntryDao.getDraftEntry("form1").first()
        assertNotNull(draftEntry)
        assertEquals("entry3", draftEntry?.id)
    }

    @Test
    fun testUpdateForm() = runBlocking {
        // Given
        val originalForm = createTestForm("form1", "Original Title")
        formDao.insertForm(originalForm)
        
        // When
        val updatedForm = originalForm.copy(
            title = "Updated Title",
            updatedAt = System.currentTimeMillis()
        )
        formDao.updateForm(updatedForm)
        
        // Then
        val retrievedForm = formDao.getFormById("form1").first()
        assertEquals("Updated Title", retrievedForm?.title)
    }

    @Test
    fun testUpdateFormEntry() = runBlocking {
        // Given
        val form = createTestForm("form1", "Test Form")
        formDao.insertForm(form)
        
        val originalEntry = createTestFormEntry("entry1", "form1", isDraft = true)
        formEntryDao.insertEntry(originalEntry)
        
        // When
        val updatedEntry = originalEntry.copy(
            isComplete = true,
            isDraft = false,
            updatedAt = System.currentTimeMillis()
        )
        formEntryDao.updateEntry(updatedEntry)
        
        // Then
        val retrievedEntry = formEntryDao.getEntryById("entry1").first()
        assertTrue(retrievedEntry?.isComplete == true)
        assertFalse(retrievedEntry?.isDraft == true)
    }

    @Test
    fun testCascadeDeleteFormEntries() = runBlocking {
        // Given
        val form = createTestForm("form1", "Test Form")
        formDao.insertForm(form)
        
        val entry = createTestFormEntry("entry1", "form1")
        formEntryDao.insertEntry(entry)
        
        // When - Delete the form
        formDao.deleteForm("form1")
        
        // Then - Entry should be deleted due to cascade
        val retrievedEntry = formEntryDao.getEntryById("entry1").first()
        assertNull(retrievedEntry)
    }

    @Test
    fun testDraftEntryManagement() = runBlocking {
        // Given
        val form = createTestForm("form1", "Test Form")
        formDao.insertForm(form)
        
        val draftEntry = createTestFormEntry("draft1", "form1", isDraft = true)
        
        // When
        formEntryDao.saveDraftEntry(draftEntry)
        
        // Then
        val retrievedDraft = formEntryDao.getDraftEntry("form1").first()
        assertNotNull(retrievedDraft)
        assertEquals("draft1", retrievedDraft?.id)
        
        // When - Delete draft
        formEntryDao.deleteDraftEntry("form1")
        
        // Then
        val deletedDraft = formEntryDao.getDraftEntry("form1").first()
        assertNull(deletedDraft)
    }

    @Test
    fun testTypeConverters() = runBlocking {
        // Given
        val fieldValuesJson = """{"field1":"value1","field2":"value2","field3":"value3"}"""
        
        val form = createTestForm("form1", "Test Form")
        formDao.insertForm(form)
        
        val entry = FormEntryEntity(
            id = "entry1",
            formId = "form1",
            fieldValuesJson = fieldValuesJson,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isComplete = false,
            isDraft = true
        )
        
        // When
        formEntryDao.insertEntry(entry)
        
        // Then
        val retrievedEntry = formEntryDao.getEntryById("entry1").first()
        assertNotNull(retrievedEntry)
        assertEquals(fieldValuesJson, retrievedEntry?.fieldValuesJson)
    }

    private fun createTestForm(
        id: String,
        title: String,
        fieldsJson: String = """[{"id":"field1","type":"text","label":"Test Field"}]""",
        sectionsJson: String = """[{"id":"section1","title":"Test Section","fields":["field1"]}]"""
    ): FormEntity {
        val currentTime = System.currentTimeMillis()
        return FormEntity(
            id = id,
            title = title,
            fieldsJson = fieldsJson,
            sectionsJson = sectionsJson,
            createdAt = currentTime,
            updatedAt = currentTime
        )
    }

    private fun createTestFormEntry(
        id: String,
        formId: String,
        fieldValuesJson: String = """{"field1":"test value"}""",
        isComplete: Boolean = false,
        isDraft: Boolean = false
    ): FormEntryEntity {
        val currentTime = System.currentTimeMillis()
        return FormEntryEntity(
            id = id,
            formId = formId,
            fieldValuesJson = fieldValuesJson,
            createdAt = currentTime,
            updatedAt = currentTime,
            isComplete = isComplete,
            isDraft = isDraft
        )
    }
}