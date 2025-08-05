package com.example.dynamicforms.features.formlist

import androidx.lifecycle.SavedStateHandle
import com.example.dynamicforms.core.testutils.MainDispatcherRule
import com.example.dynamicforms.core.testutils.TestDataFactory
import com.example.dynamicforms.domain.usecase.GetAllFormsUseCase
import com.example.dynamicforms.domain.usecase.InitializeFormsUseCase
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

@ExperimentalCoroutinesApi
class FormListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Mock
    private lateinit var mockGetAllFormsUseCase: GetAllFormsUseCase

    @Mock
    private lateinit var mockInitializeFormsUseCase: InitializeFormsUseCase

    private lateinit var viewModel: FormListViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun init_whenInitializeFormsSucceeds_loadsFormsSuccessfully() = runTest {
        // Given
        val testForms = listOf(
            TestDataFactory.createTestDynamicForm("form1", "Form 1"),
            TestDataFactory.createTestDynamicForm("form2", "Form 2")
        )

        `when`(mockInitializeFormsUseCase.invoke()).thenReturn(Result.success(Unit))
        `when`(mockGetAllFormsUseCase.invoke()).thenReturn(flowOf(testForms))

        // When
        viewModel = FormListViewModel(mockGetAllFormsUseCase, mockInitializeFormsUseCase)

        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertFalse(finalState.isInitializing)
        assertEquals(2, finalState.forms.size)
        assertNull(finalState.error)
        assertFalse(finalState.isEmpty)
        assertFalse(finalState.hasError)

        verify(mockInitializeFormsUseCase).invoke()
        verify(mockGetAllFormsUseCase).invoke()
    }

    @Test
    fun init_whenInitializeFormsFails_showsError() = runTest {
        // Given
        val exception = RuntimeException("Initialization failed")
        `when`(mockInitializeFormsUseCase.invoke()).thenReturn(Result.failure(exception))

        // When
        viewModel = FormListViewModel(mockGetAllFormsUseCase, mockInitializeFormsUseCase)

        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertFalse(finalState.isInitializing)
        assertTrue(finalState.forms.isEmpty())
        assertNotNull(finalState.error)
        assertTrue(finalState.error!!.contains("Failed to load forms"))
        assertTrue(finalState.hasError)

        verify(mockInitializeFormsUseCase).invoke()
        verify(mockGetAllFormsUseCase, never()).invoke()
    }

    @Test
    fun init_whenGetAllFormsThrowsException_showsError() = runTest {
        // Given
        val exception = RuntimeException("Database error")
        `when`(mockInitializeFormsUseCase.invoke()).thenReturn(Result.success(Unit))
        `when`(mockGetAllFormsUseCase.invoke()).thenThrow(exception)

        // When
        viewModel = FormListViewModel(mockGetAllFormsUseCase, mockInitializeFormsUseCase)

        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertFalse(finalState.isInitializing)
        assertTrue(finalState.forms.isEmpty())
        assertNotNull(finalState.error)
        assertTrue(finalState.error!!.contains("Unexpected error"))
        assertTrue(finalState.hasError)

        verify(mockInitializeFormsUseCase).invoke()
        verify(mockGetAllFormsUseCase).invoke()
    }

    @Test
    fun onEvent_loadForms_loadsFormsSuccessfully() = runTest {
        // Given
        val testForms = listOf(TestDataFactory.createTestDynamicForm("form1", "Form 1"))
        `when`(mockInitializeFormsUseCase.invoke()).thenReturn(Result.success(Unit))
        `when`(mockGetAllFormsUseCase.invoke()).thenReturn(flowOf(testForms))

        viewModel = FormListViewModel(mockGetAllFormsUseCase, mockInitializeFormsUseCase)

        // Reset invocation count
        clearInvocations(mockGetAllFormsUseCase)

        // When
        viewModel.onEvent(FormListEvent.LoadForms)

        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertEquals(1, finalState.forms.size)
        assertEquals("form1", finalState.forms[0].id)
        assertNull(finalState.error)

        verify(mockGetAllFormsUseCase).invoke()
    }

    @Test
    fun onEvent_retry_loadsFormsAgain() = runTest {
        // Given
        val testForms = listOf(TestDataFactory.createTestDynamicForm("form1", "Form 1"))
        `when`(mockInitializeFormsUseCase.invoke()).thenReturn(Result.success(Unit))
        `when`(mockGetAllFormsUseCase.invoke()).thenReturn(flowOf(testForms))

        viewModel = FormListViewModel(mockGetAllFormsUseCase, mockInitializeFormsUseCase)

        // Reset invocation count
        clearInvocations(mockGetAllFormsUseCase)

        // When
        viewModel.onEvent(FormListEvent.Retry)

        // Then
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertEquals(1, finalState.forms.size)
        assertNull(finalState.error)

        verify(mockGetAllFormsUseCase).invoke()
    }

    @Test
    fun onEvent_navigateToFormEntries_doesNotChangeState() = runTest {
        // Given
        `when`(mockInitializeFormsUseCase.invoke()).thenReturn(Result.success(Unit))
        `when`(mockGetAllFormsUseCase.invoke()).thenReturn(flowOf(emptyList()))

        viewModel = FormListViewModel(mockGetAllFormsUseCase, mockInitializeFormsUseCase)
        val initialState = viewModel.uiState.value

        // When
        viewModel.onEvent(FormListEvent.NavigateToFormEntries("form1"))

        // Then
        val finalState = viewModel.uiState.value
        assertEquals(initialState, finalState) // State should not change
    }

    @Test
    fun uiState_isEmpty_returnsCorrectValue() = runTest {
        // Given
        `when`(mockInitializeFormsUseCase.invoke()).thenReturn(Result.success(Unit))
        `when`(mockGetAllFormsUseCase.invoke()).thenReturn(flowOf(emptyList()))

        // When
        viewModel = FormListViewModel(mockGetAllFormsUseCase, mockInitializeFormsUseCase)

        // Then
        val finalState = viewModel.uiState.value
        assertTrue(finalState.isEmpty) // Empty list, not loading, no error
        assertFalse(finalState.hasError)
    }

    @Test
    fun uiState_hasError_returnsCorrectValue() = runTest {
        // Given
        val exception = RuntimeException("Test error")
        `when`(mockInitializeFormsUseCase.invoke()).thenReturn(Result.failure(exception))

        // When
        viewModel = FormListViewModel(mockGetAllFormsUseCase, mockInitializeFormsUseCase)

        // Then
        val finalState = viewModel.uiState.value
        assertTrue(finalState.hasError)
        assertFalse(finalState.isEmpty)
    }

    @Test
    fun loadingState_setCorrectlyDuringOperations() = runTest {
        // Given
        val testForms = listOf(TestDataFactory.createTestDynamicForm("form1", "Form 1"))
        `when`(mockInitializeFormsUseCase.invoke()).thenReturn(Result.success(Unit))
        `when`(mockGetAllFormsUseCase.invoke()).thenReturn(flowOf(testForms))

        // When
        viewModel = FormListViewModel(mockGetAllFormsUseCase, mockInitializeFormsUseCase)

        // Then - final state should not be loading
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertFalse(finalState.isInitializing)
    }
}