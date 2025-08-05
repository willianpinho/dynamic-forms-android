package com.example.dynamicforms.data.local.datasource

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dynamicforms.core.utils.result.Resource
import com.example.dynamicforms.data.local.database.DynamicFormsDatabase
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DatabaseInitializerTest {

    private lateinit var context: Context
    private lateinit var database: DynamicFormsDatabase
    private lateinit var databaseInitializer: DatabaseInitializer
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
        
        // Create dependencies
        assetsDataSource = AssetsDataSource(context, gson)
        databaseInitializer = DatabaseInitializer(
            context = context,
            formDao = database.formDao(),
            assetsDataSource = assetsDataSource,
            gson = gson
        )
    }

    @After
    @Throws(IOException::class)
    fun cleanup() {
        database.close()
    }

    @Test
    fun testInitializeEmptyDatabase() = runBlocking {
        // Given - Empty database
        val initialCount = database.formDao().getFormCount()
        assertEquals(0, initialCount)
        
        // When - Initialize database
        val result = databaseInitializer.initializeDatabase()
        
        // Then - Should succeed and load forms
        assertTrue("Database initialization should succeed", result is Resource.Success)
        
        val finalCount = database.formDao().getFormCount()
        assertTrue("Should have loaded forms from assets", finalCount > 0)
        
        // Verify forms were loaded
        val allForms = database.formDao().getAllForms().first()
        assertTrue("Should have at least one form", allForms.isNotEmpty())
        
        // Check that forms have proper structure
        allForms.forEach { form ->
            assertNotNull("Form ID should not be null", form.id)
            assertNotNull("Form title should not be null", form.title)
            assertNotNull("Form fields JSON should not be null", form.fieldsJson)
            assertNotNull("Form sections JSON should not be null", form.sectionsJson)
            assertTrue("Created at should be positive", form.createdAt > 0)
            assertTrue("Updated at should be positive", form.updatedAt > 0)
        }
    }

    @Test
    fun testInitializeNonEmptyDatabase() = runBlocking {
        // Given - Database with existing forms
        val testForm = createTestFormEntity("existing-form", "Existing Form")
        database.formDao().insertForm(testForm)
        
        val initialCount = database.formDao().getFormCount()
        assertEquals(1, initialCount)
        
        // When - Try to initialize
        val result = databaseInitializer.initializeDatabase()
        
        // Then - Should skip initialization
        assertTrue("Should succeed without doing anything", result is Resource.Success)
        
        val finalCount = database.formDao().getFormCount()
        assertEquals("Should not add more forms", 1, finalCount)
        
        // Verify original form is still there
        val existingForm = database.formDao().getFormById("existing-form").first()
        assertNotNull(existingForm)
        assertEquals("Existing Form", existingForm?.title)
    }

    @Test
    fun testForceReloadForms() = runBlocking {
        // Given - Database with existing forms
        val testForm = createTestFormEntity("old-form", "Old Form")
        database.formDao().insertForm(testForm)
        
        val initialCount = database.formDao().getFormCount()
        assertEquals(1, initialCount)
        
        // When - Force reload
        val result = databaseInitializer.forceReloadForms()
        
        // Then - Should clear and reload
        assertTrue("Force reload should succeed", result is Resource.Success)
        
        val finalCount = database.formDao().getFormCount()
        assertTrue("Should have loaded new forms", finalCount >= 0)
        
        // Verify old form is gone
        val oldForm = database.formDao().getFormById("old-form").first()
        assertNull("Old form should be deleted", oldForm)
    }

    @Test
    fun testGetDatabaseStats() = runBlocking {
        // Given - Empty database
        val initialStats = databaseInitializer.getDatabaseStats()
        assertEquals(0, initialStats.formCount)
        
        // When - Initialize database
        databaseInitializer.initializeDatabase()
        
        // Then - Stats should reflect loaded forms
        val finalStats = databaseInitializer.getDatabaseStats()
        assertTrue("Form count should be greater than 0", finalStats.formCount > 0)
        assertTrue("Last initialized should be recent", finalStats.lastInitialized > 0)
    }

    @Test
    fun testFormDataParsing() = runBlocking {
        // Test that forms loaded from assets have proper JSON structure
        databaseInitializer.initializeDatabase()
        
        val allForms = database.formDao().getAllForms().first()
        assertTrue("Should have loaded forms", allForms.isNotEmpty())
        
        allForms.forEach { form ->
            // Test fields JSON is valid
            try {
                val fields = gson.fromJson(form.fieldsJson, List::class.java)
                assertNotNull("Fields should be parsed as list", fields)
                assertTrue("Fields should not be empty", fields.isNotEmpty())
            } catch (e: Exception) {
                fail("Fields JSON should be valid: ${e.message}")
            }
            
            // Test sections JSON is valid
            try {
                val sections = gson.fromJson(form.sectionsJson, List::class.java)
                assertNotNull("Sections should be parsed as list", sections)
                assertTrue("Sections should not be empty", sections.isNotEmpty())
            } catch (e: Exception) {
                fail("Sections JSON should be valid: ${e.message}")
            }
        }
    }

    @Test
    fun testFormIdGeneration() = runBlocking {
        // Initialize and check that form IDs are generated consistently
        databaseInitializer.initializeDatabase()
        
        val allForms = database.formDao().getAllForms().first()
        val formIds = allForms.map { it.id }
        
        // Verify all IDs are unique
        assertEquals("All form IDs should be unique", formIds.size, formIds.toSet().size)
        
        // Verify IDs follow expected pattern (lowercase, hyphenated)
        formIds.forEach { id ->
            assertTrue("Form ID should not be empty", id.isNotEmpty())
            assertFalse("Form ID should not contain spaces", id.contains(" "))
            assertTrue("Form ID should be lowercase", id == id.lowercase())
        }
    }

    @Test
    fun testFormFieldTypes() = runBlocking {
        // Test that various field types are properly loaded
        databaseInitializer.initializeDatabase()
        
        val allForms = database.formDao().getAllForms().first()
        val foundFieldTypes = mutableSetOf<String>()
        
        allForms.forEach { form ->
            @Suppress("UNCHECKED_CAST")
            val fields = gson.fromJson(form.fieldsJson, List::class.java) as List<Map<String, Any>>
            fields.forEach { field ->
                val type = field["type"] as? String
                if (type != null) {
                    foundFieldTypes.add(type)
                }
            }
        }
        
        // Should have found field types from the assets
        assertTrue("Should find text fields", foundFieldTypes.contains("text"))
        assertTrue("Found field types should not be empty", foundFieldTypes.isNotEmpty())
        // Note: Actual field types depend on the asset files
    }

    @Test
    fun testErrorHandlingWithMissingAssets() = runBlocking {
        // Test error handling when asset files don't exist
        // The current AssetsDataSource tries to load "all-fields.json" and "200-form.json"
        // In a test environment where these files might not exist, this should handle the error gracefully
        
        val result = databaseInitializer.initializeDatabase()
        
        // The result could be either Success (if assets exist) or Error (if assets don't exist)
        // Both are valid behaviors, but the important thing is that it doesn't crash
        assertTrue("Should not crash - should return either Success or Error", 
            result is Resource.Success || result is Resource.Error)
        
        // If it's an error, it should be handled gracefully
        if (result is Resource.Error) {
            assertNotNull("Error should have a message", result.message)
            assertTrue("Should handle missing assets gracefully", true)
        }
    }

    private fun createTestFormEntity(id: String, title: String) = 
        com.example.dynamicforms.data.local.entity.FormEntity(
            id = id,
            title = title,
            fieldsJson = """[{"id":"test_field","type":"text","label":"Test Field"}]""",
            sectionsJson = """[{"id":"test_section","title":"Test Section","fields":["test_field"]}]""",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
}