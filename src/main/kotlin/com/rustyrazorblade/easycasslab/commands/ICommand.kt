package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.annotations.PostExecute
import com.rustyrazorblade.easycasslab.annotations.PreExecute
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.isAccessible

interface ICommand {
    fun executeAll() {
        val kClass = this::class

        val preExecuteMethods =
            kClass.declaredMemberFunctions
                .filter { it.findAnnotation<PreExecute>() != null }
                .sortedBy { it.name }

        for (method in preExecuteMethods) {
            method.isAccessible = true
            try {
                method.call(this)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }

        execute()

        val postExecuteMethods =
            kClass.declaredMemberFunctions
                .filter { it.findAnnotation<PostExecute>() != null }
                .sortedBy { it.name }

        for (method in postExecuteMethods) {
            method.isAccessible = true
            try {
                method.call(this)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    fun execute()
}
