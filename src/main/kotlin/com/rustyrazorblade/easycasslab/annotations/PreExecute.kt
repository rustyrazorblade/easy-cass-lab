package com.rustyrazorblade.easycasslab.annotations

/**
 * Marks a method to be executed before the command's main execute() method. Methods annotated with
 * @PreExecute will be called in declaration order before execute() runs. If any @PreExecute method
 * throws an exception, execute() will not be called.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PreExecute
