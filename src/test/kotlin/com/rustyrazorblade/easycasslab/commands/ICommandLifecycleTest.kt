package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.annotations.PostExecute
import com.rustyrazorblade.easycasslab.annotations.PreExecute
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ICommandLifecycleTest {
    @Test
    fun `single PreExecute method called before execute`() {
        val command = SinglePreExecuteCommand()
        command.executeAll()

        assertThat(command.executionOrder).containsExactly("preExecute", "execute")
    }

    @Test
    fun `multiple PreExecute methods called in order before execute`() {
        val command = MultiplePreExecuteCommand()
        command.executeAll()

        assertThat(command.executionOrder)
            .containsExactly("preExecute1", "preExecute2", "preExecute3", "execute")
    }

    @Test
    fun `single PostExecute method called after execute`() {
        val command = SinglePostExecuteCommand()
        command.executeAll()

        assertThat(command.executionOrder).containsExactly("execute", "postExecute")
    }

    @Test
    fun `multiple PostExecute methods called in order after execute`() {
        val command = MultiplePostExecuteCommand()
        command.executeAll()

        assertThat(command.executionOrder)
            .containsExactly("execute", "postExecute1", "postExecute2", "postExecute3")
    }

    @Test
    fun `complete lifecycle PreExecute then execute then PostExecute`() {
        val command = CompleteLifecycleCommand()
        command.executeAll()

        assertThat(command.executionOrder)
            .containsExactly(
                "preExecute1",
                "preExecute2",
                "execute",
                "postExecute1",
                "postExecute2",
            )
    }

    @Test
    fun `no annotations means only execute is called`() {
        val command = NoAnnotationsCommand()
        command.executeAll()

        assertThat(command.executionOrder).containsExactly("execute")
    }

    @Test
    fun `exception in PreExecute prevents execute from running`() {
        val command = PreExecuteExceptionCommand()

        assertThatThrownBy { command.executeAll() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("PreExecute failed")

        assertThat(command.executionOrder).containsExactly("preExecute")
    }

    @Test
    fun `exception in execute prevents PostExecute from running`() {
        val command = ExecuteExceptionCommand()

        assertThatThrownBy { command.executeAll() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("Execute failed")

        assertThat(command.executionOrder).containsExactly("preExecute", "execute")
    }

    @Test
    fun `exception in PostExecute propagates correctly`() {
        val command = PostExecuteExceptionCommand()

        assertThatThrownBy { command.executeAll() }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("PostExecute failed")

        assertThat(command.executionOrder).containsExactly("execute", "postExecute")
    }

    @Test
    fun `private annotated methods work correctly`() {
        val command = PrivateMethodsCommand()
        command.executeAll()

        assertThat(command.executionOrder)
            .containsExactly("privatePreExecute", "execute", "privatePostExecute")
    }

    @Test
    fun `protected annotated methods work correctly`() {
        val command = ProtectedMethodsCommand()
        command.executeAll()

        assertThat(command.executionOrder)
            .containsExactly("protectedPreExecute", "execute", "protectedPostExecute")
    }

    // Test command implementations

    private class SinglePreExecuteCommand : ICommand {
        val executionOrder = mutableListOf<String>()

        @PreExecute
        fun preExecute() {
            executionOrder.add("preExecute")
        }

        override fun execute() {
            executionOrder.add("execute")
        }
    }

    private class MultiplePreExecuteCommand : ICommand {
        val executionOrder = mutableListOf<String>()

        @PreExecute
        fun preExecute1() {
            executionOrder.add("preExecute1")
        }

        @PreExecute
        fun preExecute2() {
            executionOrder.add("preExecute2")
        }

        @PreExecute
        fun preExecute3() {
            executionOrder.add("preExecute3")
        }

        override fun execute() {
            executionOrder.add("execute")
        }
    }

    private class SinglePostExecuteCommand : ICommand {
        val executionOrder = mutableListOf<String>()

        override fun execute() {
            executionOrder.add("execute")
        }

        @PostExecute
        fun postExecute() {
            executionOrder.add("postExecute")
        }
    }

    private class MultiplePostExecuteCommand : ICommand {
        val executionOrder = mutableListOf<String>()

        override fun execute() {
            executionOrder.add("execute")
        }

        @PostExecute
        fun postExecute1() {
            executionOrder.add("postExecute1")
        }

        @PostExecute
        fun postExecute2() {
            executionOrder.add("postExecute2")
        }

        @PostExecute
        fun postExecute3() {
            executionOrder.add("postExecute3")
        }
    }

    private class CompleteLifecycleCommand : ICommand {
        val executionOrder = mutableListOf<String>()

        @PreExecute
        fun preExecute1() {
            executionOrder.add("preExecute1")
        }

        @PreExecute
        fun preExecute2() {
            executionOrder.add("preExecute2")
        }

        override fun execute() {
            executionOrder.add("execute")
        }

        @PostExecute
        fun postExecute1() {
            executionOrder.add("postExecute1")
        }

        @PostExecute
        fun postExecute2() {
            executionOrder.add("postExecute2")
        }
    }

    private class NoAnnotationsCommand : ICommand {
        val executionOrder = mutableListOf<String>()

        override fun execute() {
            executionOrder.add("execute")
        }
    }

    private class PreExecuteExceptionCommand : ICommand {
        val executionOrder = mutableListOf<String>()

        @PreExecute
        fun preExecute() {
            executionOrder.add("preExecute")
            throw RuntimeException("PreExecute failed")
        }

        override fun execute() {
            executionOrder.add("execute")
        }
    }

    private class ExecuteExceptionCommand : ICommand {
        val executionOrder = mutableListOf<String>()

        @PreExecute
        fun preExecute() {
            executionOrder.add("preExecute")
        }

        override fun execute() {
            executionOrder.add("execute")
            throw RuntimeException("Execute failed")
        }

        @PostExecute
        fun postExecute() {
            executionOrder.add("postExecute")
        }
    }

    private class PostExecuteExceptionCommand : ICommand {
        val executionOrder = mutableListOf<String>()

        override fun execute() {
            executionOrder.add("execute")
        }

        @PostExecute
        fun postExecute() {
            executionOrder.add("postExecute")
            throw RuntimeException("PostExecute failed")
        }
    }

    private class PrivateMethodsCommand : ICommand {
        val executionOrder = mutableListOf<String>()

        @PreExecute
        private fun privatePreExecute() {
            executionOrder.add("privatePreExecute")
        }

        override fun execute() {
            executionOrder.add("execute")
        }

        @PostExecute
        private fun privatePostExecute() {
            executionOrder.add("privatePostExecute")
        }
    }

    private open class ProtectedMethodsCommand : ICommand {
        val executionOrder = mutableListOf<String>()

        @PreExecute
        protected fun protectedPreExecute() {
            executionOrder.add("protectedPreExecute")
        }

        override fun execute() {
            executionOrder.add("execute")
        }

        @PostExecute
        protected fun protectedPostExecute() {
            executionOrder.add("protectedPostExecute")
        }
    }
}
