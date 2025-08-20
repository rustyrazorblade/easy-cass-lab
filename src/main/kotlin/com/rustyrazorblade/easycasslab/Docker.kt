package com.rustyrazorblade.easycasslab

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.command.PullImageResultCallback
import com.github.dockerjava.api.model.AccessMode
import com.github.dockerjava.api.model.Bind
import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.Image
import com.github.dockerjava.api.model.PullResponseItem
import com.github.dockerjava.api.model.Volume
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.concurrent.thread

// Interface for Docker client operations to improve testability
interface DockerClientInterface {
    fun listImages(
        name: String,
        tag: String,
    ): List<Image>

    fun pullImage(
        name: String,
        tag: String,
        callback: PullImageResultCallback,
    )

    fun createContainer(imageTag: String): ContainerCreationCommand

    fun attachContainer(
        containerId: String,
        stdin: PipedInputStream,
        callback: ResultCallback.Adapter<Frame>,
    )

    fun startContainer(containerId: String)

    fun inspectContainer(containerId: String): InspectContainerResponse

    fun removeContainer(
        containerId: String,
        removeVolumes: Boolean,
    )
}

// Default implementation that delegates to the actual DockerClient
class DefaultDockerClient(private val dockerClient: DockerClient) : DockerClientInterface {
    override fun listImages(
        name: String,
        tag: String,
    ): List<Image> {
        return dockerClient.listImagesCmd().withImageNameFilter("$name:$tag").exec()
    }

    override fun pullImage(
        name: String,
        tag: String,
        callback: PullImageResultCallback,
    ) {
        val pullCommand =
            if (tag.isNotBlank()) {
                dockerClient.pullImageCmd(name).withTag(tag)
            } else {
                dockerClient.pullImageCmd(name)
            }
        pullCommand.exec(callback)
    }

    override fun createContainer(imageTag: String): ContainerCreationCommand {
        return ContainerCreationCommand(dockerClient.createContainerCmd(imageTag))
    }

    override fun attachContainer(
        containerId: String,
        stdin: PipedInputStream,
        callback: ResultCallback.Adapter<Frame>,
    ) {
        dockerClient.attachContainerCmd(containerId)
            .withStdIn(stdin)
            .withStdOut(true)
            .withStdErr(true)
            .withFollowStream(true)
            .exec(callback)
    }

    override fun startContainer(containerId: String) {
        dockerClient.startContainerCmd(containerId).exec()
    }

    override fun inspectContainer(containerId: String): InspectContainerResponse {
        return dockerClient.inspectContainerCmd(containerId).exec()
    }

    override fun removeContainer(
        containerId: String,
        removeVolumes: Boolean,
    ) {
        dockerClient.removeContainerCmd(containerId)
            .withRemoveVolumes(removeVolumes)
            .exec()
    }
}

// Wrapper class for container creation commands to make testing easier
class ContainerCreationCommand(private val command: com.github.dockerjava.api.command.CreateContainerCmd) {
    fun withCmd(commands: List<String>): ContainerCreationCommand {
        command.withCmd(commands)
        return this
    }

    fun withEnv(env: Array<String>): ContainerCreationCommand {
        // Spread operator is required to pass array to vararg parameter
        @Suppress("SpreadOperator")
        command.withEnv(*env)
        return this
    }

    fun withStdinOpen(open: Boolean): ContainerCreationCommand {
        command.withStdinOpen(open)
        return this
    }

    fun withVolumes(volumes: List<Volume>): ContainerCreationCommand {
        command.withVolumes(volumes)
        return this
    }

    fun withHostConfig(config: HostConfig): ContainerCreationCommand {
        command.withHostConfig(config)
        return this
    }

    fun withWorkingDir(dir: String): ContainerCreationCommand {
        command.withWorkingDir(dir)
        return this
    }

    fun exec(): com.github.dockerjava.api.command.CreateContainerResponse {
        return command.exec()
    }
}

// Utility for getting user ID, extracted for testability
interface UserIdProvider {
    fun getUserId(): Int
}

class DefaultUserIdProvider : UserIdProvider {
    override fun getUserId(): Int {
        val idQuery = ProcessBuilder("id", System.getProperty("user.name"))
            .start().inputStream.bufferedReader().readLine()
        val matches = "uid=(\\d*)".toRegex().find(idQuery)

        return matches?.groupValues?.get(1)?.toInt() ?: 0
    }
}

data class VolumeMapping(val source: String, val destination: String, val mode: AccessMode) {
    companion object {
        val log = KotlinLogging.logger {}
    }

    init {
        log.info { "Creating volume mapping $source to $destination, mode: $mode" }
    }
}

class Docker(
    val context: Context,
    private val dockerClient: DockerClientInterface = DefaultDockerClient(context.docker),
    private val userIdProvider: UserIdProvider = DefaultUserIdProvider(),
) {
    companion object {
        private const val CONTAINER_ID_DISPLAY_LENGTH = 12
        private const val CONTAINER_POLLING_INTERVAL_MS = 1000L
        
        val log = KotlinLogging.logger {}
    }

    private val volumes = mutableListOf<VolumeMapping>()
    private val env = mutableListOf<String>()

    fun addVolume(vol: VolumeMapping): Docker {
        log.info { "adding volume: $vol" }
        volumes.add(vol)
        return this
    }

    fun addEnv(envList: String): Docker {
        env.add(envList)
        return this
    }

    fun exists(
        name: String,
        tag: String,
    ): Boolean {
        val result = dockerClient.listImages(name, tag)
        return result.isNotEmpty()
    }

    internal fun pullImage(container: Containers) {
        try {
            return pullImage(container.containerName, container.tag)
        } catch (e: Exception) {
            throw DockerException("Error pulling image ${container.containerName}:${container.tag}", e)
        }
    }

    /**
     * Tag is required here, otherwise we pull every tag
     * and that isn't fun
     */
    private fun pullImage(
        name: String,
        tag: String,
    ) {
        log.debug { "Creating pull object" }

        val callback =
            object : PullImageResultCallback() {
                override fun awaitStarted(): ResultCallback.Adapter<PullResponseItem>? {
                    log.info { "Pulling image $name" }
                    return super.awaitStarted()
                }

                override fun onNext(item: PullResponseItem?) {
                    if (item != null) {
                        item.progressDetail?.let {
                            if (it.current != null && it.total != null) {
                                println("Pulling: ${it.current} / ${it.total}")
                            }
                        }
                    }
                    return super.onNext(item)
                }
            }

        dockerClient.pullImage(name, tag, callback)
        callback.awaitCompletion()

        log.info { "Finished pulling $name" }
    }

    fun runContainer(
        container: Containers,
        command: MutableList<String>,
        workingDirectory: String,
    ): Result<String> {
        if (!exists(container.containerName, container.tag)) {
            pullImage(container)
        }

        return runContainer(container.imageWithTag, command, workingDirectory)
    }

    internal fun runContainer(
        imageTag: String,
        command: MutableList<String>,
        workingDirectory: String,
    ): Result<String> {
        val capturedStdOut = StringBuilder()
        val dockerCommandBuilder = dockerClient.createContainer(imageTag)

        // Get user ID using the injected provider for testability
        val userId = userIdProvider.getUserId()
        log.debug { "user id: $userId" }
        check(userId > 0)

        env.add("HOST_USER_ID=$userId")

        log.info { "Starting docker container with command $command and environment variables: $env" }

        dockerCommandBuilder
            .withCmd(command)
            .withEnv(env.toTypedArray())
            .withStdinOpen(true)

        if (volumes.isNotEmpty()) {
            setupVolumes(dockerCommandBuilder)
        }

        if (workingDirectory.isNotEmpty()) {
            println("Setting working directory inside container to $workingDirectory")
            dockerCommandBuilder.withWorkingDir(workingDirectory)
        }

        val dockerContainer = dockerCommandBuilder.exec()
        println("Starting $imageTag container (${dockerContainer.id.substring(0, CONTAINER_ID_DISPLAY_LENGTH)})")

        // Prepare container with stdin/stdout and attach
        val stdInputPipe = PipedOutputStream()
        val stdIn = System.`in`.bufferedReader()
        val framesRead = setupContainerIO(dockerContainer.id, stdInputPipe, stdIn, capturedStdOut)

        log.info("Starting container with command $command")
        println("Starting container ${dockerContainer.id}")

        try {
            dockerClient.startContainer(dockerContainer.id)
        } catch (e: Exception) {
            log.error(e) { "Error starting container: ${dockerContainer.id}" }
            println("Error starting container: ${e.message}")
            throw e
        }

        // Wait for container to finish and get state
        val containerState = waitForContainerToComplete(dockerContainer.id)

        // Build and log return message
        val returnMessage = buildReturnMessage(containerState, framesRead)
        println(returnMessage)

        // Clean up
        stdInputPipe.close()
        dockerClient.removeContainer(dockerContainer.id, true)

        val returnCode = containerState.exitCode ?: -1
        return if (returnCode == 0) {
            Result.success(capturedStdOut.toString())
        } else {
            Result.failure(Exception("Non zero response returned."))
        }
    }

    // Extracted methods for better testability

    private fun setupVolumes(dockerCommandBuilder: ContainerCreationCommand) {
        val volumesList = mutableListOf<Volume>()
        val bindesList = mutableListOf<Bind>()

        volumes.forEach {
            val containerVolume = Volume(it.destination)
            volumesList.add(containerVolume)
            bindesList.add(Bind(it.source, containerVolume, it.mode))
        }

        dockerCommandBuilder
            .withVolumes(volumesList)
            // This api changed a little when docker-java was upgraded, they stopped proxying this call
            // now we have to explicitly set a host config and add the binds there
            .withHostConfig(HostConfig().withBinds(bindesList))
    }

    private fun setupContainerIO(
        containerId: String,
        stdInputPipe: PipedOutputStream,
        stdIn: java.io.BufferedReader,
        capturedStdOut: StringBuilder,
    ): Int {
        var framesRead = 0

        // Create a daemon thread to redirect stdin to the container
        thread(isDaemon = true) {
            try {
                while (true) {
                    val line = stdIn.readLine() + "\n"
                    stdInputPipe.write(line.toByteArray())
                }
            } catch (ignored: IOException) {
                log.info("Pipe closed.")
            }
        }

        println("Attaching to running container")

        // Create callback to capture stdout and stderr
        val frameCallback =
            object : ResultCallback.Adapter<Frame>() {
                override fun onNext(item: Frame?) {
                    if (item == null) return

                    framesRead++
                    val payloadStr = String(item.payload)

                    if (item.streamType.name.equals("STDOUT")) {
                        // no need to use println - payloadStr already has carriage returns
                        print(payloadStr)
                        capturedStdOut.append(payloadStr)
                    } else if (item.streamType.name.equals("STDERR")) {
                        print(payloadStr)
                        log.error(payloadStr)
                    }
                }

                override fun onError(throwable: Throwable) {
                    log.error(throwable) { "Container attachment error" }
                    println(throwable.toString())
                    super.onError(throwable)
                }
            }

        // Attach to the container with our callback
        dockerClient.attachContainer(containerId, PipedInputStream(stdInputPipe), frameCallback)

        return framesRead
    }

    private fun waitForContainerToComplete(containerId: String): InspectContainerResponse.ContainerState {
        var containerState: InspectContainerResponse.ContainerState

        do {
            Thread.sleep(CONTAINER_POLLING_INTERVAL_MS)
            containerState = dockerClient.inspectContainer(containerId).state
        } while (containerState.running == true)

        return containerState
    }

    private fun buildReturnMessage(
        containerState: InspectContainerResponse.ContainerState,
        framesRead: Int,
    ): String {
        var errorMessage = ""

        if (!containerState.error.isNullOrEmpty()) {
            errorMessage = ", ${containerState.error}"
        }

        return "Container exited with exit code ${containerState.exitCodeLong}$errorMessage, frames read: $framesRead"
    }
}

class DockerException(message: String, cause: Exception) : Throwable(message, cause)
