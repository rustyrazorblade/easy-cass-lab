package com.rustyrazorblade.easycasslab.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.rustyrazorblade.easycasslab.output.OutputHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.File

/**
 * Provides User configuration by loading and caching it from the YAML file.
 * This replaces the lazy userConfig property that was previously in Context.
 */
class UserConfigProvider(
    private val profileDir: File,
    private val outputHandler: OutputHandler,
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val yaml: ObjectMapper = ObjectMapper(YAMLFactory()).registerKotlinModule()
    private val userConfigFile = File(profileDir, "settings.yaml")

    /**
     * Cached user configuration instance
     */
    private var cachedUserConfig: User? = null

    /**
     * Gets the User configuration, loading it from file if not already cached.
     * If the settings.yaml file doesn't exist, it will trigger interactive setup.
     *
     * @return The User configuration
     */
    fun getUserConfig(): User = cachedUserConfig ?: loadUserConfig().also { cachedUserConfig = it }

    /**
     * Loads the user configuration from the YAML file.
     * Always calls createInteractively which handles both new setups and missing field updates.
     */
    private fun loadUserConfig(): User {
        log.debug { "Loading user config from $userConfigFile" }
        // Create a temporary context for interactive setup - this is a bit of a hack
        // but needed for the createInteractively method
        val tempContext = com.rustyrazorblade.easycasslab.Context(profileDir.parentFile.parentFile)
        User.createInteractively(tempContext, userConfigFile, outputHandler)

        return yaml.readValue<User>(userConfigFile)
    }

    /**
     * Clears the cached user configuration, forcing it to be reloaded on next access.
     * Useful for tests or when the configuration file has been modified externally.
     */
    fun clearCache() {
        cachedUserConfig = null
    }
}
