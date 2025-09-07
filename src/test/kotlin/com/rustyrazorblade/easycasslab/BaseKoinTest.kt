package com.rustyrazorblade.easycasslab

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module

/**
 * Base test class that provides automatic Koin dependency injection setup and teardown.
 * 
 * This class ensures that critical services (like AWS) are always mocked to prevent
 * accidental real API calls during testing, while allowing tests to add their own
 * specific modules as needed.
 * 
 * Usage:
 * - Simple tests can just extend this class to get core mocks
 * - Tests needing additional mocks can override additionalTestModules()
 * - Tests can override coreTestModules() if they need to replace core mocks (rare)
 * 
 * Example:
 * ```
 * class MyTest : BaseKoinTest() {
 *     override fun additionalTestModules() = listOf(
 *         TestModules.testSSHModule()
 *     )
 * }
 * ```
 */
abstract class BaseKoinTest {
    /**
     * Core test modules that should always be loaded.
     * These include mocks for services that should NEVER make real calls in tests.
     * 
     * Override this only if you need to replace core mocks (rare).
     * Usually you want to override additionalTestModules() instead.
     */
    protected open fun coreTestModules(): List<Module> = TestModules.coreTestModules()

    /**
     * Additional test-specific modules.
     * Override this to add modules needed for your specific test.
     * 
     * Examples:
     * - Return TestModules.testSSHModule() if you need SSH mocks
     * - Return real modules if you're doing integration testing
     * - Return custom modules with specific mocks for your test
     */
    protected open fun additionalTestModules(): List<Module> = emptyList()

    /**
     * Set up Koin before each test.
     * Combines core modules with any additional test-specific modules.
     */
    @BeforeEach
    fun setupKoin() {
        // Only start Koin if it's not already running
        // This prevents issues with nested test classes or parallel test execution
        if (GlobalContext.getOrNull() == null) {
            startKoin {
                modules(coreTestModules() + additionalTestModules())
            }
        }
    }

    /**
     * Tear down Koin after each test.
     * Ensures clean state between tests.
     */
    @AfterEach
    fun tearDownKoin() {
        stopKoin()
    }
}