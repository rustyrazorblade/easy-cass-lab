package com.rustyrazorblade.easydblab.providers.aws

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * IAM policy document for AWS access control.
 * Supports both resource-based policies (OpenSearch, S3) and trust policies (IAM roles).
 */
@Serializable
data class IamPolicyDocument(
    @SerialName("Version")
    val version: String = "2012-10-17",
    @SerialName("Statement")
    val statement: List<IamPolicyStatement>,
) {
    companion object {
        private val policyJson =
            Json {
                encodeDefaults = true
                explicitNulls = false
            }

        /**
         * Serialize this policy document to JSON string.
         */
        fun IamPolicyDocument.toJson(): String = policyJson.encodeToString(serializer(), this)
    }
}

/**
 * Single statement within an IAM policy document.
 * Supports flexible principal, action, and resource configurations.
 *
 * Note: Trust policies typically don't include Resource. Inline policies typically don't include Principal.
 */
@Serializable
data class IamPolicyStatement(
    @SerialName("Effect")
    val effect: String,
    @SerialName("Principal")
    val principal: IamPolicyPrincipal? = null,
    @SerialName("Action")
    val action: IamPolicyAction,
    @SerialName("Resource")
    val resource: IamPolicyResource? = null,
)

/**
 * Principal specification for an IAM policy statement.
 * Supports AWS principals (users/roles/accounts) and Service principals.
 */
@Serializable
data class IamPolicyPrincipal(
    @SerialName("AWS")
    val aws: IamPolicyAwsPrincipal? = null,
    @SerialName("Service")
    val service: String? = null,
) {
    init {
        require(aws != null || service != null) {
            "Principal must have either AWS or Service specified"
        }
    }

    companion object {
        /**
         * Create a principal for a single AWS entity (user, role, account, or "*").
         */
        fun aws(value: String) = IamPolicyPrincipal(aws = IamPolicyAwsPrincipal.Single(value))

        /**
         * Create a principal for multiple AWS entities.
         */
        fun aws(values: List<String>) = IamPolicyPrincipal(aws = IamPolicyAwsPrincipal.Multiple(values))

        /**
         * Create a principal for an AWS service.
         */
        fun service(serviceName: String) = IamPolicyPrincipal(service = serviceName)
    }
}

/**
 * AWS principal value that can be either a single string or a list of strings.
 */
@Serializable(with = IamPolicyAwsPrincipalSerializer::class)
sealed class IamPolicyAwsPrincipal {
    data class Single(
        val value: String,
    ) : IamPolicyAwsPrincipal()

    data class Multiple(
        val values: List<String>,
    ) : IamPolicyAwsPrincipal()
}

/**
 * Action specification that can be either a single action or a list of actions.
 */
@Serializable(with = IamPolicyActionSerializer::class)
sealed class IamPolicyAction {
    data class Single(
        val value: String,
    ) : IamPolicyAction()

    data class Multiple(
        val values: List<String>,
    ) : IamPolicyAction()

    companion object {
        fun single(action: String) = Single(action)

        fun multiple(actions: List<String>) = Multiple(actions)
    }
}

/**
 * Resource specification that can be either a single resource or a list of resources.
 */
@Serializable(with = IamPolicyResourceSerializer::class)
sealed class IamPolicyResource {
    data class Single(
        val value: String,
    ) : IamPolicyResource()

    data class Multiple(
        val values: List<String>,
    ) : IamPolicyResource()

    companion object {
        fun single(resource: String) = Single(resource)

        fun multiple(resources: List<String>) = Multiple(resources)
    }
}
