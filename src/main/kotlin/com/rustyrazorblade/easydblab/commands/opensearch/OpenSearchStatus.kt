package com.rustyrazorblade.easydblab.commands.opensearch

import com.rustyrazorblade.easydblab.Context
import com.rustyrazorblade.easydblab.annotations.McpCommand
import com.rustyrazorblade.easydblab.annotations.RequireProfileSetup
import com.rustyrazorblade.easydblab.commands.PicoBaseCommand
import com.rustyrazorblade.easydblab.providers.aws.OpenSearchService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.component.inject
import picocli.CommandLine.Command
import picocli.CommandLine.Option

/**
 * Check the status of the OpenSearch domain.
 *
 * Displays current domain state, endpoints, and configuration.
 */
@McpCommand
@RequireProfileSetup
@Command(
    name = "status",
    description = ["Check OpenSearch domain status"],
)
class OpenSearchStatus(
    context: Context,
) : PicoBaseCommand(context) {
    private val log = KotlinLogging.logger {}
    private val openSearchService: OpenSearchService by inject()

    @Option(names = ["--endpoint"], description = ["Output only the endpoint URL (for scripting)"])
    var endpointOnly: Boolean = false

    override fun execute() {
        val domainState = clusterState.openSearchDomain
        if (domainState == null) {
            if (!endpointOnly) {
                outputHandler.handleMessage("No OpenSearch domain configured for this cluster.")
                outputHandler.handleMessage("Use 'opensearch start' to create one.")
            }
            return
        }

        log.info { "Checking OpenSearch domain status: ${domainState.domainName}" }

        try {
            val result = openSearchService.describeDomain(domainState.domainName)

            // Handle --endpoint flag: output only the endpoint URL
            if (endpointOnly) {
                if (result.endpoint != null) {
                    outputHandler.handleMessage(result.endpoint)
                }
                return
            }

            outputHandler.handleMessage("")
            outputHandler.handleMessage("OpenSearch Domain Status")
            outputHandler.handleMessage("========================")
            outputHandler.handleMessage("Domain Name: ${result.domainName}")
            outputHandler.handleMessage("Domain ID:   ${result.domainId}")
            outputHandler.handleMessage("State:       ${result.state}")
            outputHandler.handleMessage("")

            if (result.endpoint != null) {
                outputHandler.handleMessage("Endpoints:")
                outputHandler.handleMessage("  REST API:   https://${result.endpoint}")
                outputHandler.handleMessage("  Dashboards: ${result.dashboardsEndpoint}")
            } else {
                outputHandler.handleMessage("Endpoints: Not yet available (domain is still creating)")
            }
            outputHandler.handleMessage("")

            // Update local state if it differs
            if (result.endpoint != domainState.endpoint || result.state != domainState.state) {
                clusterState.updateOpenSearchDomain(
                    domainState.copy(
                        endpoint = result.endpoint,
                        dashboardsEndpoint = result.dashboardsEndpoint,
                        state = result.state,
                    ),
                )
                clusterStateManager.save(clusterState)
            }
        } catch (e: Exception) {
            log.warn { "Failed to get domain status: ${e.message}" }
            if (!endpointOnly) {
                outputHandler.handleMessage("Failed to get domain status: ${e.message}")
                outputHandler.handleMessage("")
                outputHandler.handleMessage("Cached state:")
                outputHandler.handleMessage("  Domain: ${domainState.domainName}")
                outputHandler.handleMessage("  State:  ${domainState.state}")
            }
        }
    }
}
