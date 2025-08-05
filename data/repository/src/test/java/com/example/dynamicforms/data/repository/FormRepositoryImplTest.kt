package com.example.dynamicforms.data.repository

import android.content.Context
import kotlinx.coroutines.test.runTest
import com.example.dynamicforms.core.testutils.TestDataFactory
import com.example.dynamicforms.data.local.datasource.FormLocalDataSource
import com.example.dynamicforms.data.mapper.FormMapper
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
import java.io.ByteArrayInputStream
import java.io.IOException

@ExperimentalCoroutinesApi
class FormRepositoryImplTest {

    @Mock
    private lateinit var mockLocalDataSource: FormLocalDataSource

    @Mock
    private lateinit var mockFormMapper: FormMapper

    @Mock
    private lateinit var mockContext: Context

    private lateinit var repository: FormRepositoryImpl

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = FormRepositoryImpl(
            localDataSource = mockLocalDataSource,
            formMapper = mockFormMapper,
            context = mockContext
        )
    }

    @Test
    fun getAllForms_returnsTransformedDomainModels() = runTest {
        // Given
        val formEntities = listOf(
            TestDataFactory.createTestFormEntity("form1", "Form 1"),
            TestDataFactory.createTestFormEntity("form2", "Form 2")
        )
        val domainForms = listOf(
            TestDataFactory.createTestDynamicForm("form1", "Form 1"),
            TestDataFactory.createTestDynamicForm("form2", "Form 2")
        )

        `when`(mockLocalDataSource.getAllForms()).thenReturn(flowOf(formEntities))
        `when`(mockFormMapper.toDomain(formEntities[0])).thenReturn(domainForms[0])
        `when`(mockFormMapper.toDomain(formEntities[1])).thenReturn(domainForms[1])

        // When
        val result = repository.getAllForms().first()

        // Then
        assertEquals(2, result.size)
        assertEquals("form1", result[0].id)
        assertEquals("form2", result[1].id)
        verify(mockLocalDataSource).getAllForms()
        verify(mockFormMapper, times(2)).toDomain(any())
    }

    @Test
    fun getFormById_withExistingId_returnsTransformedDomainModel() = runTest {
        // Given
        val formEntity = TestDataFactory.createTestFormEntity("existing-form", "Existing Form")
        val domainForm = TestDataFactory.createTestDynamicForm("existing-form", "Existing Form")

        `when`(mockLocalDataSource.getFormById("existing-form")).thenReturn(flowOf(formEntity))
        `when`(mockFormMapper.toDomain(formEntity)).thenReturn(domainForm)

        // When
        val result = repository.getFormById("existing-form").first()

        // Then
        assertNotNull(result)
        assertEquals("existing-form", result?.id)
        assertEquals("Existing Form", result?.title)
        verify(mockLocalDataSource).getFormById("existing-form")
        verify(mockFormMapper).toDomain(formEntity)
    }

    @Test
    fun getFormById_withNonExistingId_returnsNull() = runTest {
        // Given
        `when`(mockLocalDataSource.getFormById("non-existing")).thenReturn(flowOf(null))

        // When
        val result = repository.getFormById("non-existing").first()

        // Then
        assertNull(result)
        verify(mockLocalDataSource).getFormById("non-existing")
        verify(mockFormMapper, never()).toDomain(any())
    }

    @Test
    fun insertForm_success_returnsSuccessResult() = runTest {
        // Given
        val domainForm = TestDataFactory.createTestDynamicForm("form1", "Form 1")
        val formEntity = TestDataFactory.createTestFormEntity("form1", "Form 1")

        `when`(mockFormMapper.toEntity(domainForm)).thenReturn(formEntity)

        // When
        val result = repository.insertForm(domainForm)

        // Then
        assertTrue(result.isSuccess)
        verify(mockFormMapper).toEntity(domainForm)
        verify(mockLocalDataSource).insertForm(formEntity)
    }

    @Test
    fun insertForm_failure_returnsFailureResult() = runTest {
        // Given
        val domainForm = TestDataFactory.createTestDynamicForm("form1", "Form 1")
        val formEntity = TestDataFactory.createTestFormEntity("form1", "Form 1")
        val expectedException = RuntimeException("Database error")

        `when`(mockFormMapper.toEntity(domainForm)).thenReturn(formEntity)
        `when`(mockLocalDataSource.insertForm(formEntity)).thenThrow(expectedException)

        // When
        val result = repository.insertForm(domainForm)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
        verify(mockFormMapper).toEntity(domainForm)
        verify(mockLocalDataSource).insertForm(formEntity)
    }

    @Test
    fun updateForm_success_returnsSuccessResult() = runTest {
        // Given
        val domainForm = TestDataFactory.createTestDynamicForm("form1", "Updated Form")
        val formEntity = TestDataFactory.createTestFormEntity("form1", "Updated Form")

        `when`(mockFormMapper.toEntity(domainForm)).thenReturn(formEntity)

        // When
        val result = repository.updateForm(domainForm)

        // Then
        assertTrue(result.isSuccess)
        verify(mockFormMapper).toEntity(domainForm)
        verify(mockLocalDataSource).updateForm(formEntity)
    }

    @Test
    fun updateForm_failure_returnsFailureResult() = runTest {
        // Given
        val domainForm = TestDataFactory.createTestDynamicForm("form1", "Updated Form")
        val formEntity = TestDataFactory.createTestFormEntity("form1", "Updated Form")
        val expectedException = RuntimeException("Update error")

        `when`(mockFormMapper.toEntity(domainForm)).thenReturn(formEntity)
        `when`(mockLocalDataSource.updateForm(formEntity)).thenThrow(expectedException)

        // When
        val result = repository.updateForm(domainForm)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    @Test
    fun deleteForm_success_returnsSuccessResult() = runTest {
        // When
        val result = repository.deleteForm("form1")

        // Then
        assertTrue(result.isSuccess)
        verify(mockLocalDataSource).deleteForm("form1")
    }

    @Test
    fun deleteForm_failure_returnsFailureResult() = runTest {
        // Given
        val expectedException = RuntimeException("Delete error")
        `when`(mockLocalDataSource.deleteForm("form1")).thenThrow(expectedException)

        // When
        val result = repository.deleteForm("form1")

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedException, result.exceptionOrNull())
    }

    @Test
    fun loadFormsFromAssets_success_returnsParsedForms() = runTest {
        // Given
        val allFieldsJson = """{"title":"All Fields Form","fields":[],"sections":[]}"""
        val largeFormJson = """{"title":"200 Form","fields":[],"sections":[]}"""
        
        val allFieldsForm = TestDataFactory.createTestDynamicForm("all-fields", "All Fields Form")
        val largeForm = TestDataFactory.createTestDynamicForm("200-form", "200 Form")

        mockAssetReading("all-fields.json", allFieldsJson)
        mockAssetReading("200-form.json", largeFormJson)

        `when`(mockFormMapper.fromJsonToDomain(allFieldsJson)).thenReturn(allFieldsForm)
        `when`(mockFormMapper.fromJsonToDomain(largeFormJson)).thenReturn(largeForm)

        // When
        val result = repository.loadFormsFromAssets()

        // Then
        assertTrue(result.isSuccess)
        val forms = result.getOrThrow()
        assertEquals(2, forms.size)
        assertTrue(forms.any { it.id == "all-fields" })
        assertTrue(forms.any { it.id == "200-form" })
    }

    @Test
    fun loadFormsFromAssets_ioException_returnsFailureResult() = runTest {
        // Given
        mockAssetReadingThrowsException("all-fields.json", IOException("Asset not found"))

        // When
        val result = repository.loadFormsFromAssets()

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun loadFormsFromAssets_withEmptyJsonFiles_returnsEmptyList() = runTest {
        // Given
        mockAssetReading("all-fields.json", "")
        mockAssetReading("200-form.json", "")

        // When
        val result = repository.loadFormsFromAssets()

        // Then
        assertTrue(result.isSuccess)
        val forms = result.getOrThrow()
        assertTrue(forms.isEmpty())
        verify(mockFormMapper, never()).fromJsonToDomain(any())
    }

    @Test
    fun isFormsDataInitialized_delegatesToLocalDataSource() = runTest {
        // Given
        `when`(mockLocalDataSource.isFormsDataInitialized()).thenReturn(true)

        // When
        val result = repository.isFormsDataInitialized()

        // Then
        assertTrue(result)
        verify(mockLocalDataSource).isFormsDataInitialized()
    }

    @Test
    fun isFormsDataInitialized_returnsFalse() = runTest {
        // Given
        `when`(mockLocalDataSource.isFormsDataInitialized()).thenReturn(false)

        // When
        val result = repository.isFormsDataInitialized()

        // Then
        assertFalse(result)
        verify(mockLocalDataSource).isFormsDataInitialized()
    }

    private fun mockAssetReading(fileName: String, content: String) {
        val mockAssetManager = mock(android.content.res.AssetManager::class.java)
        val inputStream = ByteArrayInputStream(content.toByteArray())
        
        `when`(mockContext.assets).thenReturn(mockAssetManager)
        `when`(mockAssetManager.open(fileName)).thenReturn(inputStream)
    }

    private fun mockAssetReadingThrowsException(fileName: String, exception: Exception) {
        val mockAssetManager = mock(android.content.res.AssetManager::class.java)
        
        `when`(mockContext.assets).thenReturn(mockAssetManager)
        `when`(mockAssetManager.open(fileName)).thenThrow(exception)
    }
}