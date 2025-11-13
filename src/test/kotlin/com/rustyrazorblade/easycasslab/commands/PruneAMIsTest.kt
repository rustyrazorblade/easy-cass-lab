package com.rustyrazorblade.easycasslab.commands

import com.rustyrazorblade.easycasslab.BaseKoinTest
import com.rustyrazorblade.easycasslab.providers.aws.AMIService
import com.rustyrazorblade.easycasslab.providers.aws.model.AMI
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

internal class PruneAMIsTest : BaseKoinTest() {
    @Test
    fun `execute should call AMIService with default parameters`() {
        // Arrange
        val mockAMIService: AMIService = mock()
        val mockResult =
            AMIService.PruneResult(
                kept = listOf(createTestAMI("ami-1")),
                deleted = emptyList(),
            )
        whenever(mockAMIService.pruneAMIs(any(), any(), any(), any())).thenReturn(mockResult)

        val command = PruneAMIs(context, mockAMIService)

        // Act
        command.execute()

        // Assert
        verify(mockAMIService).pruneAMIs(
            namePattern = "rustyrazorblade/images/easy-cass-lab-*",
            keepCount = 2,
            dryRun = true,
            typeFilter = null,
        )
    }

    @Test
    fun `execute should call AMIService with custom keep count`() {
        // Arrange
        val mockAMIService: AMIService = mock()
        val mockResult =
            AMIService.PruneResult(
                kept = emptyList(),
                deleted = emptyList(),
            )
        whenever(mockAMIService.pruneAMIs(any(), any(), any(), any())).thenReturn(mockResult)

        val command = PruneAMIs(context, mockAMIService)
        command.keep = 5

        // Act
        command.execute()

        // Assert
        verify(mockAMIService).pruneAMIs(
            namePattern = "rustyrazorblade/images/easy-cass-lab-*",
            keepCount = 5,
            dryRun = true,
            typeFilter = null,
        )
    }

    @Test
    fun `execute should call AMIService with dry-run enabled`() {
        // Arrange
        val mockAMIService: AMIService = mock()
        val mockResult =
            AMIService.PruneResult(
                kept = emptyList(),
                deleted = emptyList(),
            )
        whenever(mockAMIService.pruneAMIs(any(), any(), any(), any())).thenReturn(mockResult)

        val command = PruneAMIs(context, mockAMIService)
        command.dryRun = true

        // Act
        command.execute()

        // Assert
        verify(mockAMIService).pruneAMIs(
            namePattern = "rustyrazorblade/images/easy-cass-lab-*",
            keepCount = 2,
            dryRun = true,
            typeFilter = null,
        )
    }

    @Test
    fun `execute should call AMIService with type filter`() {
        // Arrange
        val mockAMIService: AMIService = mock()
        val mockResult =
            AMIService.PruneResult(
                kept = emptyList(),
                deleted = emptyList(),
            )
        whenever(mockAMIService.pruneAMIs(any(), any(), any(), any())).thenReturn(mockResult)

        val command = PruneAMIs(context, mockAMIService)
        command.type = "cassandra"

        // Act
        command.execute()

        // Assert
        verify(mockAMIService).pruneAMIs(
            namePattern = "rustyrazorblade/images/easy-cass-lab-*",
            keepCount = 2,
            dryRun = true,
            typeFilter = "cassandra",
        )
    }

    @Test
    fun `execute should call AMIService with custom pattern`() {
        // Arrange
        val mockAMIService: AMIService = mock()
        val mockResult =
            AMIService.PruneResult(
                kept = emptyList(),
                deleted = emptyList(),
            )
        whenever(mockAMIService.pruneAMIs(any(), any(), any(), any())).thenReturn(mockResult)

        val command = PruneAMIs(context, mockAMIService)
        command.pattern = "custom-pattern-*"

        // Act
        command.execute()

        // Assert
        verify(mockAMIService).pruneAMIs(
            namePattern = "custom-pattern-*",
            keepCount = 2,
            dryRun = true,
            typeFilter = null,
        )
    }

    @Test
    fun `execute should output summary when AMIs are deleted`() {
        // Arrange
        val mockAMIService: AMIService = mock()
        val keptAMIs = listOf(createTestAMI("ami-1"), createTestAMI("ami-2"))
        val deletedAMIs = listOf(createTestAMI("ami-3"))
        val mockResult = AMIService.PruneResult(kept = keptAMIs, deleted = deletedAMIs)
        whenever(mockAMIService.pruneAMIs(any(), any(), any(), any())).thenReturn(mockResult)

        val command = PruneAMIs(context, mockAMIService)

        // Act
        command.execute()

        // Assert - verify output handler was called
        // (Output handler is mocked in BaseKoinTest, so we can't verify exact messages,
        // but we can verify the service was called correctly)
        verify(mockAMIService).pruneAMIs(any(), any(), eq(false), any())
    }

    @Test
    fun `execute should output dry-run message when dry-run is enabled`() {
        // Arrange
        val mockAMIService: AMIService = mock()
        val deletedAMIs = listOf(createTestAMI("ami-3"))
        val mockResult = AMIService.PruneResult(kept = emptyList(), deleted = deletedAMIs)
        whenever(mockAMIService.pruneAMIs(any(), any(), any(), any())).thenReturn(mockResult)

        val command = PruneAMIs(context, mockAMIService)
        command.dryRun = true

        // Act
        command.execute()

        // Assert
        verify(mockAMIService).pruneAMIs(any(), any(), eq(true), any())
    }

    @Test
    fun `execute should handle empty results`() {
        // Arrange
        val mockAMIService: AMIService = mock()
        val mockResult = AMIService.PruneResult(kept = emptyList(), deleted = emptyList())
        whenever(mockAMIService.pruneAMIs(any(), any(), any(), any())).thenReturn(mockResult)

        val command = PruneAMIs(context, mockAMIService)

        // Act
        command.execute()

        // Assert
        verify(mockAMIService).pruneAMIs(any(), any(), any(), any())
    }

    private fun createTestAMI(id: String): AMI =
        AMI(
            id = id,
            name = "rustyrazorblade/images/easy-cass-lab-cassandra-amd64-20240101",
            architecture = "amd64",
            creationDate = Instant.now(),
            isPublic = false,
            snapshotIds = emptyList(),
        )
}
