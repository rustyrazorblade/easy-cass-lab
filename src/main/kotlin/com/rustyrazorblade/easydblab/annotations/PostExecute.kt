package com.rustyrazorblade.easydblab.annotations

/**
 * Marks a method to be executed after the command's main execute() method. Methods annotated with
 * @PostExecute will be called in declaration order after execute() completes. If execute() throws
 * an exception, @PostExecute methods will not be called.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PostExecute
