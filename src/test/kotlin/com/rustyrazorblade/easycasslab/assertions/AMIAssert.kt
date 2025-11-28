package com.rustyrazorblade.easycasslab.assertions

import com.rustyrazorblade.easycasslab.providers.aws.model.AMI
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

/**
 * Custom AssertJ assertion class for AMI domain objects.
 *
 * Provides fluent, domain-specific assertions for testing AMI-related functionality.
 * Following the pattern recommended in docs/TESTING.md for domain-driven testing.
 *
 * Usage:
 * ```kotlin
 * import com.rustyrazorblade.easycasslab.assertions.assertThat
 *
 * assertThat(ami)
 *     .hasId("ami-123")
 *     .hasArchitecture("amd64")
 *     .isNewerThan(otherAmi)
 * ```
 */
class AMIAssert(
    actual: AMI,
) : AbstractObjectAssert<AMIAssert, AMI>(actual, AMIAssert::class.java) {
    fun hasId(expected: String): AMIAssert {
        isNotNull
        assertThat(actual.id)
            .withFailMessage("Expected AMI id to be <%s> but was <%s>", expected, actual.id)
            .isEqualTo(expected)
        return this
    }

    fun hasName(expected: String): AMIAssert {
        isNotNull
        assertThat(actual.name)
            .withFailMessage("Expected AMI name to be <%s> but was <%s>", expected, actual.name)
            .isEqualTo(expected)
        return this
    }

    fun hasArchitecture(expected: String): AMIAssert {
        isNotNull
        assertThat(actual.architecture)
            .withFailMessage("Expected AMI architecture to be <%s> but was <%s>", expected, actual.architecture)
            .isEqualTo(expected)
        return this
    }

    fun hasType(expected: String): AMIAssert {
        isNotNull
        assertThat(actual.type)
            .withFailMessage("Expected AMI type to be <%s> but was <%s>", expected, actual.type)
            .isEqualTo(expected)
        return this
    }

    fun hasGroupKey(expected: String): AMIAssert {
        isNotNull
        assertThat(actual.groupKey)
            .withFailMessage("Expected AMI groupKey to be <%s> but was <%s>", expected, actual.groupKey)
            .isEqualTo(expected)
        return this
    }

    fun hasOwnerId(expected: String): AMIAssert {
        isNotNull
        assertThat(actual.ownerId)
            .withFailMessage("Expected AMI ownerId to be <%s> but was <%s>", expected, actual.ownerId)
            .isEqualTo(expected)
        return this
    }

    fun isPublic(): AMIAssert {
        isNotNull
        assertThat(actual.isPublic)
            .withFailMessage("Expected AMI to be public but was private")
            .isTrue()
        return this
    }

    fun isPrivate(): AMIAssert {
        isNotNull
        assertThat(actual.isPublic)
            .withFailMessage("Expected AMI to be private but was public")
            .isFalse()
        return this
    }

    fun hasSnapshotIds(vararg expectedIds: String): AMIAssert {
        isNotNull
        assertThat(actual.snapshotIds)
            .withFailMessage(
                "Expected AMI to have snapshot IDs %s but had %s",
                expectedIds.toList(),
                actual.snapshotIds,
            ).containsExactlyInAnyOrder(*expectedIds)
        return this
    }

    fun hasNoSnapshots(): AMIAssert {
        isNotNull
        assertThat(actual.snapshotIds)
            .withFailMessage("Expected AMI to have no snapshots but had %s", actual.snapshotIds)
            .isEmpty()
        return this
    }

    fun isNewerThan(other: AMI): AMIAssert {
        isNotNull
        assertThat(actual.creationDate)
            .withFailMessage(
                "Expected AMI <%s> created at <%s> to be newer than AMI <%s> created at <%s>",
                actual.id,
                actual.creationDate,
                other.id,
                other.creationDate,
            ).isAfter(other.creationDate)
        return this
    }

    fun isOlderThan(other: AMI): AMIAssert {
        isNotNull
        assertThat(actual.creationDate)
            .withFailMessage(
                "Expected AMI <%s> created at <%s> to be older than AMI <%s> created at <%s>",
                actual.id,
                actual.creationDate,
                other.id,
                other.creationDate,
            ).isBefore(other.creationDate)
        return this
    }

    companion object {
        @JvmStatic
        fun assertThat(ami: AMI): AMIAssert = AMIAssert(ami)
    }
}

/**
 * Entry point for AMI assertions.
 *
 * Import this function to use fluent AMI assertions:
 * ```kotlin
 * import com.rustyrazorblade.easycasslab.assertions.assertThat
 * ```
 */
fun assertThat(ami: AMI): AMIAssert = AMIAssert.assertThat(ami)
