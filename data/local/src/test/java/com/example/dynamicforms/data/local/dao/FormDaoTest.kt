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
class FormDaoTest {

    private lateinit var database: DynamicFormsDatabase
    private lateinit var formDao: FormDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            DynamicFormsDatabase::class.java
        ).allowMainThreadQueries().build()
        
        formDao = database.formDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertForm_savesFormSuccessfully() = runTest {
        // Given
        val testForm = TestDataFactory.createTestFormEntity(
            id = "form1",
            title = "Test Form 1"
        )

        // When
        formDao.insertForm(testForm)

        // Then
        val retrievedForm = formDao.getFormById("form1").first()
        assertNotNull(retrievedForm)
        assertEquals(testForm.id, retrievedForm?.id)
        assertEquals(testForm.title, retrievedForm?.title)
    }

    @Test
    fun insertForms_savesMultipleFormsSuccessfully() = runTest {
        // Given
        val testForms = listOf(
            TestDataFactory.createTestFormEntity("form1", "Form 1"),
            TestDataFactory.createTestFormEntity("form2", "Form 2"),
            TestDataFactory.createTestFormEntity("form3", "Form 3")
        )

        // When
        formDao.insertForms(testForms)

        // Then
        val allForms = formDao.getAllForms().first()
        assertEquals(3, allForms.size)
        assertTrue(allForms.any { it.id == "form1" })
        assertTrue(allForms.any { it.id == "form2" })
        assertTrue(allForms.any { it.id == "form3" })
    }

    @Test
    fun getAllForms_returnsFormsOrderedByUpdatedAtDesc() = runTest {
        // Given
        val form1 = TestDataFactory.createTestFormEntity("form1", "Form 1").copy(updatedAt = 1000L)
        val form2 = TestDataFactory.createTestFormEntity("form2", "Form 2").copy(updatedAt = 3000L)
        val form3 = TestDataFactory.createTestFormEntity("form3", "Form 3").copy(updatedAt = 2000L)

        formDao.insertForms(listOf(form1, form2, form3))

        // When
        val forms = formDao.getAllForms().first()

        // Then
        assertEquals(3, forms.size)
        assertEquals("form2", forms[0].id) // Most recent
        assertEquals("form3", forms[1].id) // Middle
        assertEquals("form1", forms[2].id) // Oldest
    }

    @Test
    fun getFormById_withExistingId_returnsForm() = runTest {
        // Given
        val testForm = TestDataFactory.createTestFormEntity("existing-form", "Existing Form")
        formDao.insertForm(testForm)

        // When
        val result = formDao.getFormById("existing-form").first()

        // Then
        assertNotNull(result)
        assertEquals("existing-form", result?.id)
        assertEquals("Existing Form", result?.title)
    }

    @Test
    fun getFormById_withNonExistingId_returnsNull() = runTest {
        // When
        val result = formDao.getFormById("non-existing-form").first()

        // Then
        assertNull(result)
    }

    @Test
    fun updateForm_modifiesExistingForm() = runTest {
        // Given
        val originalForm = TestDataFactory.createTestFormEntity("form1", "Original Title")
        formDao.insertForm(originalForm)

        val updatedForm = originalForm.copy(
            title = "Updated Title",
            updatedAt = 2000L
        )

        // When
        formDao.updateForm(updatedForm)

        // Then
        val result = formDao.getFormById("form1").first()
        assertNotNull(result)
        assertEquals("Updated Title", result?.title)
        assertEquals(2000L, result?.updatedAt)
    }

    @Test
    fun deleteForm_removesFormFromDatabase() = runTest {
        // Given
        val testForm = TestDataFactory.createTestFormEntity("form-to-delete", "Form to Delete")
        formDao.insertForm(testForm)
        
        // Verify form exists
        assertNotNull(formDao.getFormById("form-to-delete").first())

        // When
        formDao.deleteForm("form-to-delete")

        // Then
        val result = formDao.getFormById("form-to-delete").first()
        assertNull(result)
    }

    @Test
    fun getFormCount_returnsCorrectCount() = runTest {
        // Given - initially empty
        assertEquals(0, formDao.getFormCount())

        // When - insert forms
        val testForms = listOf(
            TestDataFactory.createTestFormEntity("form1", "Form 1"),
            TestDataFactory.createTestFormEntity("form2", "Form 2")
        )
        formDao.insertForms(testForms)

        // Then
        assertEquals(2, formDao.getFormCount())
    }

    @Test
    fun deleteAllForms_removesAllFormsFromDatabase() = runTest {
        // Given
        val testForms = listOf(
            TestDataFactory.createTestFormEntity("form1", "Form 1"),
            TestDataFactory.createTestFormEntity("form2", "Form 2"),
            TestDataFactory.createTestFormEntity("form3", "Form 3")
        )
        formDao.insertForms(testForms)
        assertEquals(3, formDao.getFormCount())

        // When
        formDao.deleteAllForms()

        // Then
        assertEquals(0, formDao.getFormCount())
        assertTrue(formDao.getAllForms().first().isEmpty())
    }

    @Test
    fun insertForm_withConflictStrategy_replacesExistingForm() = runTest {
        // Given
        val originalForm = TestDataFactory.createTestFormEntity("duplicate-id", "Original")
        val conflictingForm = TestDataFactory.createTestFormEntity("duplicate-id", "Replacement")

        // When
        formDao.insertForm(originalForm)
        formDao.insertForm(conflictingForm) // Should replace due to OnConflictStrategy.REPLACE

        // Then
        val result = formDao.getFormById("duplicate-id").first()
        assertNotNull(result)
        assertEquals("Replacement", result?.title)
        assertEquals(1, formDao.getFormCount()) // Only one form should exist
    }
}