package com.example.dynamicforms.data.local

import org.junit.runner.RunWith
import org.junit.runners.Suite
import com.example.dynamicforms.data.local.database.DynamicFormsDatabaseTest
import com.example.dynamicforms.data.local.database.DatabaseInitializationTest
import com.example.dynamicforms.data.local.datasource.DatabaseInitializerTest

/**
 * Test suite that runs all Room database related tests
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    DynamicFormsDatabaseTest::class,
    DatabaseInitializationTest::class,
    DatabaseInitializerTest::class
)
class RoomTestSuite