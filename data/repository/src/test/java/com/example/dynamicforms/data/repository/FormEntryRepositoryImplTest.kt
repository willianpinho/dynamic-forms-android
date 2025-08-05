package com.example.dynamicforms.data.repository

import kotlinx.coroutines.test.runTest
import com.example.dynamicforms.core.testutils.TestDataFactory
import com.example.dynamicforms.data.local.datasource.FormEntryLocalDataSource
import com.example.dynamicforms.data.mapper.FormEntryMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

@ExperimentalCoroutinesApi
class FormEntryRepositoryImplTest {

    @Mock
    private lateinit var mockLocalDataSource: FormEntryLocalDataSource

    @Mock
    private lateinit var mockFormEntryMapper: FormEntryMapper

    private lateinit var repository: FormEntryRepositoryImpl

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = FormEntryRepositoryImpl(
            localDataSource = mockLocalDataSource,
            formEntryMapper = mockFormEntryMapper
        )
    }

    @Test
    fun getEntriesForForm_returnsTransformedDomainModels() = runTest {
        // Given
        val formId = "test-form-id"
        val entryEntities = listOf(
            TestDataFactory.createTestFormEntryEntity("entry1", formId, false),
            TestDataFactory.createTestFormEntryEntity("entry2", formId, true)
        )
        val domainEntries = listOf(
            TestDataFactory.createTestFormEntry("entry1", formId, isComplete = true, isDraft = false),
            TestDataFactory.createTestFormEntry("entry2", formId, isComplete = false, isDraft = true)
        )

        `when`(mockLocalDataSource.getEntriesForForm(formId)).thenReturn(flowOf(entryEntities))
        `when`(mockFormEntryMapper.toDomain(entryEntities[0])).thenReturn(domainEntries[0])
        `when`(mockFormEntryMapper.toDomain(entryEntities[1])).thenReturn(domainEntries[1])

        // When
        val result = repository.getEntriesForForm(formId).first()

        // Then
        assertEquals(2, result.size)
        assertEquals("entry1", result[0].id)
        assertEquals("entry2", result[1].id)
        verify(mockLocalDataSource).getEntriesForForm(formId)
        verify(mockFormEntryMapper, times(2)).toDomain(any())
    }

    @Test
    fun getEntryById_withExistingId_returnsTransformedDomainModel() = runTest {
        // Given
        val entryId = "existing-entry"
        val entryEntity = TestDataFactory.createTestFormEntryEntity(entryId, "form1")
        val domainEntry = TestDataFactory.createTestFormEntry(entryId, "form1")

        `when`(mockLocalDataSource.getEntryById(entryId)).thenReturn(flowOf(entryEntity))
        `when`(mockFormEntryMapper.toDomain(entryEntity)).thenReturn(domainEntry)

        // When
        val result = repository.getEntryById(entryId).first()

        // Then
        assertNotNull(result)
        assertEquals(entryId, result?.id)
        assertEquals("form1", result?.formId)
        verify(mockLocalDataSource).getEntryById(entryId)
        verify(mockFormEntryMapper).toDomain(entryEntity)
    }

    @Test
    fun getEntryById_withNonExistingId_returnsNull() = runTest {
        // Given
        val entryId = "non-existing"
        `when`(mockLocalDataSource.getEntryById(entryId)).thenReturn(flowOf(null))

        // When
        val result = repository.getEntryById(entryId).first()

        // Then
        assertNull(result)
        verify(mockLocalDataSource).getEntryById(entryId)
        verify(mockFormEntryMapper, never()).toDomain(any())
    }

    @Test
    fun insertEntry_success_returnsSuccessResultWithEntityId() = runTest {
        // Given
        val domainEntry = TestDataFactory.createTestFormEntry("entry1", "form1")
        val entryEntity = TestDataFactory.createTestFormEntryEntity("generated-id", "form1")
        val insertedId = 123L

        `when`(mockFormEntryMapper.toEntity(domainEntry)).thenReturn(entryEntity)
        `when`(mockLocalDataSource.insertEntry(entryEntity)).thenReturn(insertedId)

        // When
        val result = repository.insertEntry(domainEntry)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(entryEntity.id, result.getOrThrow())
        verify(mockFormEntryMapper).toEntity(domainEntry)
        verify(mockLocalDataSource).insertEntry(entryEntity)
    }

    @Test
    fun insertEntry_failure_returnsFailureResult() = runTest {
        // Given
        val domainEntry = TestDataFactory.createTestFormEntry("entry1", "form1")
        val entryEntity = TestDataFactory.createTestFormEntryEntity("entry1", "form1")
        val expectedException = RuntimeException("Insert failed")

        `when`(mockFormEntryMapper.toEntity(domainEntry)).thenReturn(entryEntity)
        `when`(mockLocalDataSource.insertEntry(entryEntity)).thenThrow(expectedException)

        // When
        val result = repository.insertEntry(domainEntry)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
        verify(mockFormEntryMapper).toEntity(domainEntry)
        verify(mockLocalDataSource).insertEntry(entryEntity)
    }

    @Test
    fun updateEntry_success_returnsSuccessResult() = runTest {
        // Given
        val domainEntry = TestDataFactory.createTestFormEntry("entry1", "form1")
        val entryEntity = TestDataFactory.createTestFormEntryEntity("entry1", "form1")

        `when`(mockFormEntryMapper.toEntity(domainEntry)).thenReturn(entryEntity)

        // When
        val result = repository.updateEntry(domainEntry)

        // Then
        assertTrue(result.isSuccess)
        verify(mockFormEntryMapper).toEntity(domainEntry)
        verify(mockLocalDataSource).updateEntry(entryEntity)
    }

    @Test
    fun updateEntry_failure_returnsFailureResult() = runTest {
        // Given
        val domainEntry = TestDataFactory.createTestFormEntry("entry1", "form1")
        val entryEntity = TestDataFactory.createTestFormEntryEntity("entry1", "form1")
        val expectedException = RuntimeException("Update failed")

        `when`(mockFormEntryMapper.toEntity(domainEntry)).thenReturn(entryEntity)
        `when`(mockLocalDataSource.updateEntry(entryEntity)).thenThrow(expectedException)

        // When
        val result = repository.updateEntry(domainEntry)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
        verify(mockFormEntryMapper).toEntity(domainEntry)
        verify(mockLocalDataSource).updateEntry(entryEntity)
    }

    @Test
    fun deleteEntry_success_returnsSuccessResult() = runTest {
        // Given
        val entryId = "entry-to-delete"

        // When
        val result = repository.deleteEntry(entryId)

        // Then
        assertTrue(result.isSuccess)
        verify(mockLocalDataSource).deleteEntry(entryId)
    }

    @Test
    fun deleteEntry_failure_returnsFailureResult() = runTest {
        // Given
        val entryId = "entry-to-delete"
        val expectedException = RuntimeException("Delete failed")
        `when`(mockLocalDataSource.deleteEntry(entryId)).thenThrow(expectedException)

        // When
        val result = repository.deleteEntry(entryId)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
        verify(mockLocalDataSource).deleteEntry(entryId)
    }

    @Test
    fun saveEntryDraft_success_returnsSuccessResult() = runTest {
        // Given
        val domainEntry = TestDataFactory.createTestFormEntry("draft-entry", "form1", isDraft = true)
        val entryEntity = TestDataFactory.createTestFormEntryEntity("draft-entry", "form1", true)

        `when`(mockFormEntryMapper.toEntity(domainEntry)).thenReturn(entryEntity)

        // When
        val result = repository.saveEntryDraft(domainEntry)

        // Then
        assertTrue(result.isSuccess)
        verify(mockFormEntryMapper).toEntity(domainEntry)
        verify(mockLocalDataSource).saveDraftEntry(entryEntity)
    }

    @Test
    fun saveEntryDraft_failure_returnsFailureResult() = runTest {
        // Given
        val domainEntry = TestDataFactory.createTestFormEntry("draft-entry", "form1", isDraft = true)
        val entryEntity = TestDataFactory.createTestFormEntryEntity("draft-entry", "form1", true)
        val expectedException = RuntimeException("Save draft failed")

        `when`(mockFormEntryMapper.toEntity(domainEntry)).thenReturn(entryEntity)
        doThrow(expectedException).`when`(mockLocalDataSource).saveDraftEntry(entryEntity)

        // When
        val result = repository.saveEntryDraft(domainEntry)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
        verify(mockFormEntryMapper).toEntity(domainEntry)
        verify(mockLocalDataSource).saveDraftEntry(entryEntity)
    }

    @Test
    fun getDraftEntry_returnsTransformedDomainModel() = runTest {
        // Given
        val formId = "test-form"
        val draftEntity = TestDataFactory.createTestFormEntryEntity("draft", formId, true)
        val domainDraft = TestDataFactory.createTestFormEntry("draft", formId, isDraft = true)

        `when`(mockLocalDataSource.getDraftEntry(formId)).thenReturn(flowOf(draftEntity))
        `when`(mockFormEntryMapper.toDomain(draftEntity)).thenReturn(domainDraft)

        // When
        val result = repository.getDraftEntry(formId).first()

        // Then
        assertNotNull(result)
        assertEquals("draft", result?.id)
        assertTrue(result?.isDraft ?: false)
        verify(mockLocalDataSource).getDraftEntry(formId)
        verify(mockFormEntryMapper).toDomain(draftEntity)
    }

    @Test
    fun getDraftEntry_withNoDraft_returnsNull() = runTest {
        // Given
        val formId = "test-form"
        `when`(mockLocalDataSource.getDraftEntry(formId)).thenReturn(flowOf(null))

        // When
        val result = repository.getDraftEntry(formId).first()

        // Then
        assertNull(result)
        verify(mockLocalDataSource).getDraftEntry(formId)
        verify(mockFormEntryMapper, never()).toDomain(any())
    }


}