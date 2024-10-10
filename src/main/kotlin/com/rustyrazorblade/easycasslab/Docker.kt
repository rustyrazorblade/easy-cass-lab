package com.rustyrazorblade.easycasslab

import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model.*
import com.github.dockerjava.core.command.PullImageResultCallback
import org.apache.logging.log4j.kotlin.logger
import java.io.IOException
import java.io.PipedOutputStream
import java.io.PipedInputStream
import kotlin.concurrent.thread


data class VolumeMapping(val source: String, val destination: String, val mode: AccessMode) {
    companion object {
        val log = logger()
    }
    init {
        log.info { "Creating volume mapping $source to $destination, mode: $mode" }
    }
}

class Docker(val context: Context) {

    companion object {
        val log = logger()
    }

    private val volumes = mutableListOf<VolumeMapping>()
    private val env = mutableListOf<String>()

    fun addVolume(vol: VolumeMapping) : Docker {
        log.info { "adding volume: $vol" }
        volumes.add(vol)
        return this
    }

    fun addEnv(envList: String) : Docker {
        env.add(envList)
        return this
    }

    fun exists(name: String, tag: String) : Boolean {
        val result = context.docker.listImagesCmd().withImageNameFilter("$name:$tag").exec()
        return result.size > 0
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
    private fun pullImage(name: String, tag: String) {
        log.debug { "Creating pull object" }

        var pullCommand = context.docker.pullImageCmd(name)

        if(tag.isNotBlank())
            pullCommand = pullCommand.withTag(tag)

        pullCommand.exec(
                object : PullImageResultCallback() {

                    override fun awaitStarted(): PullImageResultCallback {
                        log.info { "Pulling image $name" }
                        return super.awaitStarted()
                    }

                    override fun onNext(item: PullResponseItem?) {
                        if(item != null) {

                            item.progressDetail?.let {
                                if(it.current != null && it.total != null)
                                    println("Pulling: ${it.current} / ${it.total}")
                            }
                        }
                        return super.onNext(item)
                    }

                }

        ).awaitCompletion()

        log.info{"Finished pulling $name"}
    }

    fun runContainer(container: Containers, command: MutableList<String>, workingDirectory: String) : Result<String> {
        if(!exists(container.containerName, container.tag)) {
            pullImage(container)
        }

        return runContainer(container.imageWithTag,  command, workingDirectory)
    }

    internal fun runContainer(
            imageTag: String,
            command: MutableList<String>,
            workingDirectory : String) : Result<String> {

        val capturedStdOut = StringBuilder()
        val dockerCommandBuilder = context.docker.createContainerCmd(imageTag)

        // this only runs on linux or mac, deeply sorry
        val idQuery = ProcessBuilder("id", System.getProperty("user.name")).start().inputStream.bufferedReader().readLine()
        val matches = "uid=(\\d*)".toRegex().find(idQuery)

        var userId = 0
        if(matches != null) {
            userId = matches.groupValues[1].toInt()
        }

        log.debug { "user id: $userId" }
        check(userId > 0)

        env.add("HOST_USER_ID=$userId")

        log.info{ "Starting docker container with command $command and environment variables: $env"}

        dockerCommandBuilder
                .withCmd(command)
                .withEnv(*env.toTypedArray())
                .withStdinOpen(true)

        if (volumes.isNotEmpty()) {
            val volumesList = mutableListOf<Volume>()
            val bindesList = mutableListOf<Bind>()

            volumes.forEach{
                val containerVolume = Volume(it.destination)
                volumesList.add(containerVolume)
                bindesList.add(Bind(it.source, containerVolume, it.mode))
            }

            dockerCommandBuilder
                    .withVolumes(volumesList)
                    // This api changed a little when docker-java was upgraded, they stopped proxying this call
                    // now we have to explicitly set a host config and add the binds there
                    .withHostConfig(HostConfig().withBinds(bindesList)
                    )
        }

        if (workingDirectory.isNotEmpty()) {
            println("Setting working directory inside container to $workingDirectory")
            dockerCommandBuilder
                    .withWorkingDir(workingDirectory)
        }

        val dockerContainer = dockerCommandBuilder.exec()

        println("Starting $imageTag container (${dockerContainer.id.substring(0,12)})")

        var containerState : InspectContainerResponse.ContainerState

        // handle stdin.  the PipedOutputStream will accept data and
        // feed it to PipedInputStream, which then goes to docker
        // it looks like this, essentially
        // stdInputPipe -> PipedInputStream(stdInputPipe) -> terraform container
        val stdInputPipe = PipedOutputStream()

        // now a means of reading from stdin
        val stdIn = System.`in`.bufferedReader()

        val redirectStdInputThread = thread(isDaemon = true) {
            try {
                while (true) {
                    val line = stdIn.readLine() + "\n"
                    stdInputPipe.write(line.toByteArray())
                }
            } catch(e: IOException) {
                log.info("Pipe closed.")
            }
        }
        println("Attaching to running container")
        var framesRead = 0
        context.docker.attachContainerCmd(dockerContainer.id)
                .withStdIn(PipedInputStream(stdInputPipe))
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
            .exec(object : ResultCallback.Adapter<Frame>() {
                override fun onNext(item: Frame?) {
                        // should only include standard out - please fix me
                        if(item == null) return

                        framesRead++
                        val payloadStr = String(item.payload)

                        if(item.streamType.name.equals("STDOUT")) {
                            // no need to use println - payloadStr already has carriage returns
                            print(payloadStr)
                            capturedStdOut.append(payloadStr)
                        } else if(item.streamType.name.equals("STDERR")) {
                            print(payloadStr)
                            log.error(payloadStr)
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        println(throwable.toString())
                        println(throwable.printStackTrace())
                        super.onError(throwable)
                    }
            })
        log.info("Starting container with command $command")
        println("Starting container ${dockerContainer.id}")

        try {
            context.docker.startContainerCmd(dockerContainer.id).exec()
        } catch (e: Exception) {
            println("Error starting container: ${e.printStackTrace()}")
            throw e
        }

        // stay here till the container stops
        do {
            Thread.sleep(1000)
            containerState = context.docker.inspectContainerCmd(dockerContainer.id).exec().state
        } while (containerState.running == true)


        val returnMessage: String by lazy {
            var errorMessage = ""

            if (!containerState.error.isNullOrEmpty()) {
                errorMessage = ", ${containerState.error}"
            }

            "Container exited with exit code ${containerState.exitCodeLong}$errorMessage, frames read: $framesRead"
        }

        println(returnMessage)

        // close the stdin pipe otherwise we will never exit back to the commandline
        stdInputPipe.close()

        // clean up after ourselves
        context.docker.removeContainerCmd(dockerContainer.id)
                .withRemoveVolumes(true)
                .exec()

        val returnCode = containerState.exitCode ?: -1

        return if ( returnCode == 0) Result.success(capturedStdOut.toString()) else Result.failure(Exception("Non zero response returned."))
    }
}


class DockerException(s: String, e: Exception) : Throwable() {

}
