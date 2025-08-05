package com.example.dynamicforms.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dynamicforms.core.testutils.TestDataFactory
import com.example.dynamicforms.data.local.database.DynamicFormsDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class FormEntryDaoTest {

    private lateinit var database: DynamicFormsDatabase
    private lateinit var formEntryDao: FormEntryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DynamicFormsDatabase::class.java
        ).allowMainThreadQueries().build()
        
        formEntryDao = database.formEntryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertEntry_savesEntrySuccessfully() = runTest {
        // Given
        val testEntry = TestDataFactory.createTestFormEntryEntity(
            id = "entry1",
            formId = "form1"
        )

        // When
        val insertedId = formEntryDao.insertEntry(testEntry)

        // Then
        assertTrue(insertedId > 0)
        val retrievedEntry = formEntryDao.getEntryById("entry1").first()
        assertNotNull(retrievedEntry)
        assertEquals(testEntry.id, retrievedEntry?.id)
        assertEquals(testEntry.formId, retrievedEntry?.formId)
    }

    @Test
    fun getEntriesForForm_returnsCorrectEntriesOrderedByUpdatedAtDesc() = runTest {
        // Given
        val entry1 = TestDataFactory.createTestFormEntryEntity("entry1", "form1", false)
            .copy(updatedAt = 1000L)
        val entry2 = TestDataFactory.createTestFormEntryEntity("entry2", "form1", false)
            .copy(updatedAt = 3000L)
        val entry3 = TestDataFactory.createTestFormEntryEntity("entry3", "form2", false)
            .copy(updatedAt = 2000L)

        formEntryDao.insertEntry(entry1)
        formEntryDao.insertEntry(entry2)
        formEntryDao.insertEntry(entry3)

        // When
        val form1Entries = formEntryDao.getEntriesForForm("form1").first()

        // Then
        assertEquals(2, form1Entries.size)
        assertEquals("entry2", form1Entries[0].id) // Most recent
        assertEquals("entry1", form1Entries[1].id) // Older
    }

    @Test
    fun getEntriesForForm_returnsAllEntriesIncludingDrafts() = runTest {
        // Given
        val submittedEntry = TestDataFactory.createTestFormEntryEntity("submitted", "form1", false)
            .copy(updatedAt = 1000L)
        val draftEntry = TestDataFactory.createTestFormEntryEntity("draft", "form1", true)
            .copy(updatedAt = 2000L)
        val otherFormEntry = TestDataFactory.createTestFormEntryEntity("other", "form2", false)
            .copy(updatedAt = 1500L)

        formEntryDao.insertEntry(submittedEntry)
        formEntryDao.insertEntry(draftEntry)
        formEntryDao.insertEntry(otherFormEntry)

        // When
        val form1Entries = formEntryDao.getEntriesForForm("form1").first()

        // Then
        assertEquals(2, form1Entries.size) // Both submitted and draft entries for form1
        assertEquals("draft", form1Entries[0].id) // Most recent (draft)
        assertEquals("submitted", form1Entries[1].id) // Older (submitted)
        assertTrue(form1Entries[0].isDraft)
        assertFalse(form1Entries[1].isDraft)
    }

    @Test
    fun getSubmittedEntriesForForm_returnsOnlySubmittedEntries() = runTest {
        // Given
        val draftEntry = TestDataFactory.createTestFormEntryEntity("draft", "form1", true)
        val submittedEntry = TestDataFactory.createTestFormEntryEntity("submitted", "form1", false)

        formEntryDao.insertEntry(draftEntry)
        formEntryDao.insertEntry(submittedEntry)

        // When
        val submittedEntries = formEntryDao.getSubmittedEntriesForForm("form1").first()

        // Then
        assertEquals(1, submittedEntries.size)
        assertEquals("submitted", submittedEntries[0].id)
        assertFalse(submittedEntries[0].isDraft)
    }

    @Test
    fun getEntryById_withExistingId_returnsEntry() = runTest {
        // Given
        val testEntry = TestDataFactory.createTestFormEntryEntity("existing-entry", "form1")
        formEntryDao.insertEntry(testEntry)

        // When
        val result = formEntryDao.getEntryById("existing-entry").first()

        // Then
        assertNotNull(result)
        assertEquals("existing-entry", result?.id)
        assertEquals("form1", result?.formId)
    }

    @Test
    fun getEntryById_withNonExistingId_returnsNull() = runTest {
        // When
        val result = formEntryDao.getEntryById("non-existing-entry").first()

        // Then
        assertNull(result)
    }

    @Test
    fun updateEntry_modifiesExistingEntry() = runTest {
        // Given
        val originalEntry = TestDataFactory.createTestFormEntryEntity("entry1", "form1", true)
        formEntryDao.insertEntry(originalEntry)

        val updatedEntry = originalEntry.copy(
            isDraft = false,
            isComplete = true,
            updatedAt = 2000L
        )

        // When
        formEntryDao.updateEntry(updatedEntry)

        // Then
        val result = formEntryDao.getEntryById("entry1").first()
        assertNotNull(result)
        assertFalse(result?.isDraft ?: true)
        assertTrue(result?.isComplete ?: false)
        assertEquals(2000L, result?.updatedAt)
    }

    @Test
    fun deleteEntry_removesEntryFromDatabase() = runTest {
        // Given
        val testEntry = TestDataFactory.createTestFormEntryEntity("entry-to-delete", "form1")
        formEntryDao.insertEntry(testEntry)
        
        // Verify entry exists
        assertNotNull(formEntryDao.getEntryById("entry-to-delete").first())

        // When
        formEntryDao.deleteEntry("entry-to-delete")

        // Then
        val result = formEntryDao.getEntryById("entry-to-delete").first()
        assertNull(result)
    }

    @Test
    fun getDraftEntry_returnsLatestDraftForForm() = runTest {
        // Given
        val oldDraft = TestDataFactory.createTestFormEntryEntity("old-draft", "form1", true)
            .copy(updatedAt = 1000L)
        val newDraft = TestDataFactory.createTestFormEntryEntity("new-draft", "form1", true)
            .copy(updatedAt = 2000L)
        val submitted = TestDataFactory.createTestFormEntryEntity("submitted", "form1", false)

        formEntryDao.insertEntry(oldDraft)
        formEntryDao.insertEntry(newDraft)
        formEntryDao.insertEntry(submitted)

        // When
        val draftEntry = formEntryDao.getDraftEntry("form1").first()

        // Then
        assertNotNull(draftEntry)
        assertEquals("new-draft", draftEntry?.id) // Should return the latest draft
        assertTrue(draftEntry?.isDraft ?: false)
    }

    @Test
    fun getDraftEntry_withNoDrafts_returnsNull() = runTest {
        // Given
        val submittedEntry = TestDataFactory.createTestFormEntryEntity("submitted", "form1", false)
        formEntryDao.insertEntry(submittedEntry)

        // When
        val draftEntry = formEntryDao.getDraftEntry("form1").first()

        // Then
        assertNull(draftEntry)
    }

    @Test
    fun deleteDraftEntry_removesOnlyDraftEntries() = runTest {
        // Given
        val draftEntry = TestDataFactory.createTestFormEntryEntity("draft", "form1", true)
        val submittedEntry = TestDataFactory.createTestFormEntryEntity("submitted", "form1", false)

        formEntryDao.insertEntry(draftEntry)
        formEntryDao.insertEntry(submittedEntry)

        // When
        formEntryDao.deleteDraftEntry("form1")

        // Then
        assertNull(formEntryDao.getEntryById("draft").first())
        assertNotNull(formEntryDao.getEntryById("submitted").first())
    }

    @Test
    fun saveDraftEntry_insertsOrReplacesEntry() = runTest {
        // Given
        val draftEntry = TestDataFactory.createTestFormEntryEntity("draft", "form1", true)

        // When - first save
        formEntryDao.saveDraftEntry(draftEntry)

        // Then
        val firstResult = formEntryDao.getEntryById("draft").first()
        assertNotNull(firstResult)

        // When - save again with updated data
        val updatedDraft = draftEntry.copy(
            fieldValuesJson = """{"updated":"value"}""",
            updatedAt = 2000L
        )
        formEntryDao.saveDraftEntry(updatedDraft)

        // Then
        val secondResult = formEntryDao.getEntryById("draft").first()
        assertNotNull(secondResult)
        assertEquals(2000L, secondResult?.updatedAt)
        assertEquals("""{"updated":"value"}""", secondResult?.fieldValuesJson)
    }

    @Test
    fun cleanOldDrafts_removesOldDraftsButKeepsCurrent() = runTest {
        // Given
        val currentDraft = TestDataFactory.createTestFormEntryEntity("current", "form1", true)
        val oldDraft1 = TestDataFactory.createTestFormEntryEntity("old1", "form1", true)
        val oldDraft2 = TestDataFactory.createTestFormEntryEntity("old2", "form1", true)

        formEntryDao.insertEntry(currentDraft)
        formEntryDao.insertEntry(oldDraft1)
        formEntryDao.insertEntry(oldDraft2)

        // When
        formEntryDao.cleanOldDrafts("form1", "current")

        // Then
        assertNotNull(formEntryDao.getEntryById("current").first())
        assertNull(formEntryDao.getEntryById("old1").first())
        assertNull(formEntryDao.getEntryById("old2").first())
    }

    @Test
    fun getEntryCountForForm_returnsCorrectSubmittedEntriesCount() = runTest {
        // Given
        val draftEntry = TestDataFactory.createTestFormEntryEntity("draft", "form1", true)
        val submitted1 = TestDataFactory.createTestFormEntryEntity("sub1", "form1", false)
        val submitted2 = TestDataFactory.createTestFormEntryEntity("sub2", "form1", false)
        val otherFormEntry = TestDataFactory.createTestFormEntryEntity("other", "form2", false)

        formEntryDao.insertEntry(draftEntry)
        formEntryDao.insertEntry(submitted1)
        formEntryDao.insertEntry(submitted2)
        formEntryDao.insertEntry(otherFormEntry)

        // When
        val count = formEntryDao.getEntryCountForForm("form1")

        // Then
        assertEquals(2, count) // Only submitted entries for form1
    }
}