package com.example.dynamicforms.features.formentries

import com.example.dynamicforms.core.testutils.MainDispatcherRule
import com.example.dynamicforms.core.testutils.TestDataFactory
import com.example.dynamicforms.domain.usecase.DeleteFormEntryUseCase
import com.example.dynamicforms.domain.usecase.GetFormByIdUseCase
import com.example.dynamicforms.domain.usecase.GetFormEntriesUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class FormEntriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var mockGetFormByIdUseCase: GetFormByIdUseCase

    @Mock
    private lateinit var mockGetFormEntriesUseCase: GetFormEntriesUseCase
    
    @Mock
    private lateinit var mockDeleteFormEntryUseCase: DeleteFormEntryUseCase

    private lateinit var viewModel: FormEntriesViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        viewModel = FormEntriesViewModel(mockGetFormByIdUseCase, mockGetFormEntriesUseCase, mockDeleteFormEntryUseCase)
    }

    @Test
    fun onEvent_loadFormAndEntries_loadsDataSuccessfully() = runTest {
        // Given
        val formId = "test-form"
        val form = TestDataFactory.createTestDynamicForm(formId, "Test Form")
        val submittedEntry = TestDataFactory.createTestFormEntry("entry1", formId, isComplete = true, isDraft = false)
        val draftEntry = TestDataFactory.createTestFormEntry("entry2", formId, isComplete = false, isDraft = true)
        val entries = listOf(submittedEntry, draftEntry)

        `when`(mockGetFormByIdUseCase.invoke(formId)).thenReturn(flowOf(form))
        `when`(mockGetFormEntriesUseCase.invoke(formId)).thenReturn(flowOf(entries))

        // When
        viewModel.onEvent(FormEntriesEvent.LoadFormAndEntries(formId))

        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertEquals(form, finalState.form)
        assertEquals(1, finalState.submittedEntries.size)
        assertEquals(1, finalState.draftEntries.size)
        assertEquals("entry1", finalState.submittedEntries[0].id)
        assertEquals("entry2", finalState.draftEntries[0].id)
        assertEquals(2, finalState.totalEntries)
        assertNull(finalState.error)
        assertFalse(finalState.isEmpty)
        assertFalse(finalState.hasError)

        verify(mockGetFormByIdUseCase).invoke(formId)
        verify(mockGetFormEntriesUseCase).invoke(formId)
    }

    @Test
    fun onEvent_loadFormAndEntries_withNullForm_handlesCorrectly() = runTest {
        // Given
        val formId = "non-existing-form"
        val entries = listOf(
            TestDataFactory.createTestFormEntry("entry1", formId, isComplete = true, isDraft = false)
        )

        `when`(mockGetFormByIdUseCase.invoke(formId)).thenReturn(flowOf(null))
        `when`(mockGetFormEntriesUseCase.invoke(formId)).thenReturn(flowOf(entries))

        // When
        viewModel.onEvent(FormEntriesEvent.LoadFormAndEntries(formId))

        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertNull(finalState.form)
        assertEquals(1, finalState.submittedEntries.size)
        assertEquals(0, finalState.draftEntries.size)
        assertNull(finalState.error)

        verify(mockGetFormByIdUseCase).invoke(formId)
        verify(mockGetFormEntriesUseCase).invoke(formId)
    }

    @Test
    fun onEvent_loadFormAndEntries_withEmptyEntries_handlesCorrectly() = runTest {
        // Given
        val formId = "empty-form"
        val form = TestDataFactory.createTestDynamicForm(formId, "Empty Form")

        `when`(mockGetFormByIdUseCase.invoke(formId)).thenReturn(flowOf(form))
        `when`(mockGetFormEntriesUseCase.invoke(formId)).thenReturn(flowOf(emptyList()))

        // When
        viewModel.onEvent(FormEntriesEvent.LoadFormAndEntries(formId))

        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertEquals(form, finalState.form)
        assertTrue(finalState.submittedEntries.isEmpty())
        assertTrue(finalState.draftEntries.isEmpty())
        assertEquals(0, finalState.totalEntries)
        assertNull(finalState.error)
        assertTrue(finalState.isEmpty) // Form exists but no entries

        verify(mockGetFormByIdUseCase).invoke(formId)
        verify(mockGetFormEntriesUseCase).invoke(formId)
    }

    @Test
    fun onEvent_loadFormAndEntries_whenUseCaseThrowsException_showsError() = runTest {
        // Given
        val formId = "error-form"
        val exception = RuntimeException("Database error")

        `when`(mockGetFormByIdUseCase.invoke(formId)).thenThrow(exception)

        // When
        viewModel.onEvent(FormEntriesEvent.LoadFormAndEntries(formId))

        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertNull(finalState.form)
        assertTrue(finalState.submittedEntries.isEmpty())
        assertTrue(finalState.draftEntries.isEmpty())
        assertNotNull(finalState.error)
        assertTrue(finalState.error!!.contains("Unexpected error"))
        assertTrue(finalState.hasError)

        verify(mockGetFormByIdUseCase).invoke(formId)
    }

    @Test
    fun onEvent_retry_withPreviousFormId_reloadsData() = runTest {
        // Given
        val formId = "retry-form"
        val form = TestDataFactory.createTestDynamicForm(formId, "Retry Form")
        val entries = listOf(
            TestDataFactory.createTestFormEntry("entry1", formId, isComplete = true, isDraft = false)
        )

        `when`(mockGetFormByIdUseCase.invoke(formId)).thenReturn(flowOf(form))
        `when`(mockGetFormEntriesUseCase.invoke(formId)).thenReturn(flowOf(entries))

        // First load
        viewModel.onEvent(FormEntriesEvent.LoadFormAndEntries(formId))

        // Reset mocks
        clearInvocations(mockGetFormByIdUseCase, mockGetFormEntriesUseCase)

        // When
        viewModel.onEvent(FormEntriesEvent.Retry)

        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertEquals(form, finalState.form)
        assertEquals(1, finalState.submittedEntries.size)
        assertNull(finalState.error)

        verify(mockGetFormByIdUseCase).invoke(formId)
        verify(mockGetFormEntriesUseCase).invoke(formId)
    }

    @Test
    fun onEvent_retry_withoutPreviousFormId_doesNothing() = runTest {
        // When - retry without previous load
        viewModel.onEvent(FormEntriesEvent.Retry)

        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertNull(finalState.form)
        assertTrue(finalState.submittedEntries.isEmpty())
        assertTrue(finalState.draftEntries.isEmpty())
        assertNull(finalState.error)

        verify(mockGetFormByIdUseCase, never()).invoke(any())
        verify(mockGetFormEntriesUseCase, never()).invoke(any())
    }

    @Test
    fun onEvent_addNewEntry_doesNotChangeState() = runTest {
        // Given
        val initialState = viewModel.uiState.value

        // When
        viewModel.onEvent(FormEntriesEvent.AddNewEntry)

        // Then
        val finalState = viewModel.uiState.value
        assertEquals(initialState, finalState) // State should not change
    }

    @Test
    fun onEvent_navigateToFormDetail_doesNotChangeState() = runTest {
        // Given
        val initialState = viewModel.uiState.value

        // When
        viewModel.onEvent(FormEntriesEvent.NavigateToFormDetail("form1", "entry1"))

        // Then
        val finalState = viewModel.uiState.value
        assertEquals(initialState, finalState) // State should not change
    }

    @Test
    fun onEvent_deleteEntry_successfulDeletion() = runTest {
        // Given
        val entryId = "entry1"
        val formId = "test-form"
        val form = TestDataFactory.createTestDynamicForm(formId, "Test Form")
        val entries = listOf(
            TestDataFactory.createTestFormEntry(entryId, formId, isComplete = true, isDraft = false),
            TestDataFactory.createTestFormEntry("entry2", formId, isComplete = false, isDraft = true)
        )
        
        // Set up initial state with form and entries
        `when`(mockGetFormByIdUseCase.invoke(formId)).thenReturn(flowOf(form))
        `when`(mockGetFormEntriesUseCase.invoke(formId)).thenReturn(flowOf(entries))
        viewModel.onEvent(FormEntriesEvent.LoadFormAndEntries(formId))
        
        // Set up delete success
        `when`(mockDeleteFormEntryUseCase.invoke(entryId)).thenReturn(Result.success(Unit))
        
        // When
        viewModel.onEvent(FormEntriesEvent.DeleteEntry(entryId))
        
        // Then
        verify(mockDeleteFormEntryUseCase).invoke(entryId)
        verify(mockGetFormByIdUseCase, times(2)).invoke(formId) // Initial load + reload after deletion
        verify(mockGetFormEntriesUseCase, times(2)).invoke(formId) // Initial load + reload after deletion
        
        val finalState = viewModel.uiState.value
        assertFalse(finalState.showDeleteDialog) // Dialog should be dismissed
        assertNull(finalState.entryToDelete) // Entry to delete should be cleared
    }
    
    @Test
    fun onEvent_deleteEntry_failedDeletion() = runTest {
        // Given
        val entryId = "entry1"
        val formId = "test-form"
        val form = TestDataFactory.createTestDynamicForm(formId, "Test Form")
        val entries = listOf(
            TestDataFactory.createTestFormEntry(entryId, formId, isComplete = true, isDraft = false)
        )
        val exception = RuntimeException("Failed to delete entry")
        
        // Set up initial state with form and entries
        `when`(mockGetFormByIdUseCase.invoke(formId)).thenReturn(flowOf(form))
        `when`(mockGetFormEntriesUseCase.invoke(formId)).thenReturn(flowOf(entries))
        viewModel.onEvent(FormEntriesEvent.LoadFormAndEntries(formId))
        
        // Set up delete failure
        `when`(mockDeleteFormEntryUseCase.invoke(entryId)).thenReturn(Result.failure(exception))
        
        // When
        viewModel.onEvent(FormEntriesEvent.DeleteEntry(entryId))
        
        // Then
        verify(mockDeleteFormEntryUseCase).invoke(entryId)
        
        val finalState = viewModel.uiState.value
        assertNotNull(finalState.error)
        assertTrue(finalState.error!!.contains("Failed to delete entry"))
    }
    
    @Test
    fun onEvent_showDeleteDialog_updatesState() = runTest {
        // Given
        val entryId = "entry-to-delete"
        val initialState = viewModel.uiState.value
        assertFalse(initialState.showDeleteDialog)
        assertNull(initialState.entryToDelete)
        
        // When
        viewModel.onEvent(FormEntriesEvent.ShowDeleteDialog(entryId))
        
        // Then
        val finalState = viewModel.uiState.value
        assertTrue(finalState.showDeleteDialog)
        assertEquals(entryId, finalState.entryToDelete)
    }
    
    @Test
    fun onEvent_dismissDeleteDialog_updatesState() = runTest {
        // Given
        val entryId = "entry-to-delete"
        
        // First show the dialog
        viewModel.onEvent(FormEntriesEvent.ShowDeleteDialog(entryId))
        val stateWithDialog = viewModel.uiState.value
        assertTrue(stateWithDialog.showDeleteDialog)
        assertEquals(entryId, stateWithDialog.entryToDelete)
        
        // When
        viewModel.onEvent(FormEntriesEvent.DismissDeleteDialog)
        
        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.showDeleteDialog)
        assertNull(finalState.entryToDelete)
    }

    @Test
    fun entryClassification_separatesCorrectly() = runTest {
        // Given
        val formId = "classification-form"
        val form = TestDataFactory.createTestDynamicForm(formId, "Classification Form")
        val submittedEntry1 = TestDataFactory.createTestFormEntry("sub1", formId, isComplete = true, isDraft = false)
        val submittedEntry2 = TestDataFactory.createTestFormEntry("sub2", formId, isComplete = true, isDraft = false)
        val draftEntry1 = TestDataFactory.createTestFormEntry("draft1", formId, isComplete = false, isDraft = true)
        val draftEntry2 = TestDataFactory.createTestFormEntry("draft2", formId, isComplete = false, isDraft = true)
        val entries = listOf(submittedEntry1, submittedEntry2, draftEntry1, draftEntry2)

        `when`(mockGetFormByIdUseCase.invoke(formId)).thenReturn(flowOf(form))
        `when`(mockGetFormEntriesUseCase.invoke(formId)).thenReturn(flowOf(entries))

        // When
        viewModel.onEvent(FormEntriesEvent.LoadFormAndEntries(formId))

        // Then
        val finalState = viewModel.uiState.value
        assertEquals(2, finalState.submittedEntries.size)
        assertEquals(2, finalState.draftEntries.size)
        assertEquals(4, finalState.totalEntries)

        // Verify submitted entries are correctly classified
        assertTrue(finalState.submittedEntries.all { it.isComplete && !it.isDraft })
        
        // Verify draft entries are correctly classified
        assertTrue(finalState.draftEntries.all { it.isDraft && !it.isComplete })
    }

    @Test
    fun uiState_computedProperties_returnCorrectValues() = runTest {
        // Given - empty state
        val emptyState = FormEntriesUiState()
        
        // When/Then - empty state
        assertFalse(emptyState.isEmpty) // No form loaded yet
        assertFalse(emptyState.hasError)
        assertEquals(0, emptyState.totalEntries)

        // Given - state with form but no entries
        val stateWithFormNoEntries = FormEntriesUiState(
            form = TestDataFactory.createTestDynamicForm("form1", "Form 1"),
            submittedEntries = emptyList(),
            draftEntries = emptyList(),
            isLoading = false,
            error = null
        )

        // When/Then - form with no entries
        assertTrue(stateWithFormNoEntries.isEmpty)
        assertFalse(stateWithFormNoEntries.hasError)
        assertEquals(0, stateWithFormNoEntries.totalEntries)

        // Given - state with error
        val stateWithError = FormEntriesUiState(error = "Test error")

        // When/Then - error state
        assertFalse(stateWithError.isEmpty)
        assertTrue(stateWithError.hasError)
    }
}