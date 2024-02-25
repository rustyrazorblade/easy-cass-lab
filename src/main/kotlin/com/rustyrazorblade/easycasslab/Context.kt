package com.rustyrazorblade.easycasslab

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.dockerjava.core.DefaultDockerClientConfig
import  com.rustyrazorblade.easycasslab.configuration.TFState
import  com.rustyrazorblade.easycasslab.configuration.User
import  com.rustyrazorblade.easycasslab.core.YamlDelegate
import org.apache.logging.log4j.kotlin.logger
import java.io.File
import java.nio.file.Files
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.rustyrazorblade.easycasslab.configuration.Host
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.keyprovider.KeyIdentityProvider
import org.apache.sshd.common.util.security.SecurityUtils
import org.apache.sshd.scp.client.CloseableScpClient
import org.apache.sshd.scp.client.ScpClient
import org.apache.sshd.scp.client.ScpClientCreator
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.Path


data class Context(val easycasslabUserDirectory: File) {

    val cassandraBuildDir = File(easycasslabUserDirectory, "builds")
    var profilesDir = File(easycasslabUserDirectory, "profiles")
    var profileDir = File(profilesDir, "default")
    val terraformCacheDir = File(easycasslabUserDirectory, "terraform_cache").also { it.mkdirs() }

    var nettyInitialised = false
    val cassandraRepo = com.rustyrazorblade.easycasslab.Cassandra()

    init {
        profileDir.mkdirs()
    }

    val log = logger()

    fun createBuildSkeleton(name: String) {

        val buildLocation = File(cassandraBuildDir, name)
        buildLocation.mkdirs()
        File(buildLocation, "conf").mkdirs()
        File(buildLocation, "deb").mkdirs()
    }

    /**
     * Please use this for reading and writing yaml to objects
     *
     * Example usage:
     *
     * val state = mapper.readValue<MyStateObject>(json)
     */
    val json = getJsonMapper()
    val yaml : ObjectMapper by YamlDelegate()

    // if you need to anything funky with the mapper (settings etc) use this
    fun getJsonMapper() = jacksonObjectMapper()

    private val userConfigFile = File(profileDir, "settings.yaml")

    // this will let us write out the yaml
    val userConfig by lazy {
        if(!userConfigFile.exists()) {
            log.debug { "$userConfigFile not found, going through interactive setup" }
            profilesDir.mkdirs()
            User.createInteractively(this, userConfigFile)
        }

        yaml.readValue<User>(userConfigFile)
    }

    val docker by lazy {
        nettyInitialised = true

        val dockerConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .build()

        val httpClient = ApacheDockerHttpClient.Builder()
                .dockerHost(dockerConfig.dockerHost)
                .sslConfig(dockerConfig.sslConfig)
                .build();
        DockerClientImpl.getInstance(dockerConfig, httpClient)
    }

    val cwdPath = System.getProperty("user.dir")

    val tfstate by lazy { TFState.parse(this, File(cwdPath, "terraform.tfstate")) }
    val home = File(System.getProperty("user.home"))

    val keyPairs by lazy {
        val loader = SecurityUtils.getKeyPairResourceParser()
        loader.loadKeyPairs(null, Path( userConfig.sshKeyPath), null)

    }

    val sshClient by lazy {
        val client = SshClient.setUpDefaultClient()
        client.setKeyIdentityProvider(KeyIdentityProvider.wrapKeyPairs(keyPairs))
        client.start()
        client
    }

    private fun getSession(host: Host): ClientSession {
        val session = sshClient.connect("ubuntu", host.public, 22)
            .verify(Duration.ofSeconds(10))
            .session
        session.addPublicKeyIdentity(keyPairs.first())
        session.auth().verify()
        return session
    }

    fun executeRemotely(host: Host, command: String) {
        // Setup guide: https://github.com/apache/mina-sshd/blob/master/docs/client-setup.md

        // Create the client.
        // We have to register the keys with the client.
        // Client can be used to connect to multiple hosts
        println("Connecting to ${host.public}")
        val session = getSession(host)
        println("Executing remote command: $command")
        println(session.executeRemoteCommand(command))
    }

    fun getScpClient(host: Host) : CloseableScpClient {
        val session = getSession(host)
        val creator = ScpClientCreator.instance()
        val client = creator.createScpClient(session)
        return CloseableScpClient.singleSessionInstance(client)
    }

    fun upload(host: Host, local: Path, remote: String) {
        val client = getScpClient(host)
        client.upload(local, remote)
    }


    fun stop() {
        sshClient.stop()
    }

    companion object {
        /**
         * Used only for testing
         */
        fun testContext() : Context {
            val tmpContentParent = File("test/contexts")
            tmpContentParent.mkdirs()

            val testTempDirectory = Files.createTempDirectory(tmpContentParent.toPath(), "easycasslab")
            // create a default profile
            // generate a fake key
            val user = User("test@rustyrazorblade.com", "us-west-2", "test", "test", "test", "test", "test", "", "")

            val context = Context(testTempDirectory.toFile())
            context.yaml.writeValue(context.userConfigFile, user)
            return context
        }
    }

    fun requireSshKey() {
        if(!File(userConfig.sshKeyPath).exists()) {
            log.error { "SSH key not found at ${userConfig.sshKeyPath}" }
            System.exit(1)
        }
    }
}
