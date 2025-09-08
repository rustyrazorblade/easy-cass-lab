package com.rustyrazorblade.easycasslab.di

import com.rustyrazorblade.easycasslab.Context
import com.rustyrazorblade.easycasslab.configuration.TFState
import org.koin.dsl.module
import java.io.File
import java.io.InputStream

/**
 * Provides Terraform state management services
 */
interface TFStateProvider {
    /**
     * Parse a TFState from a file
     */
    fun parseFromFile(file: File): TFState
    
    /**
     * Parse a TFState from an input stream
     */
    fun parseFromStream(stream: InputStream): TFState
    
    /**
     * Get the default TFState from terraform.tfstate in the current working directory
     */
    fun getDefault(): TFState
}

/**
 * Default implementation of TFStateProvider
 */
class DefaultTFStateProvider(
    private val context: Context
) : TFStateProvider {
    
    private val defaultStateFile by lazy {
        File(context.cwdPath, "terraform.tfstate")
    }
    
    override fun parseFromFile(file: File): TFState {
        return TFState(context, file.inputStream())
    }
    
    override fun parseFromStream(stream: InputStream): TFState {
        return TFState(context, stream)
    }
    
    override fun getDefault(): TFState {
        if (!defaultStateFile.exists()) {
            throw IllegalStateException("Terraform state file not found at ${defaultStateFile.absolutePath}")
        }
        return parseFromFile(defaultStateFile)
    }
}

/**
 * Koin module for Terraform-related services
 */
val terraformModule = module {
    single<TFStateProvider> {
        DefaultTFStateProvider(get())
    }
    
    // Provide a default TFState instance for backward compatibility
    // This mimics the current lazy behavior in Context
    factory {
        get<TFStateProvider>().getDefault()
    }
}
