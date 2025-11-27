package com.rustyrazorblade.easycasslab.configuration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClusterS3PathTest {
    @Test
    fun `from creates cluster-namespaced path`() {
        val path = ClusterS3Path.from("my-bucket", "cluster-123")

        assertThat(path.toString()).isEqualTo("s3://my-bucket/clusters/cluster-123")
        assertThat(path.bucket).isEqualTo("my-bucket")
        assertThat(path.getKey()).isEqualTo("clusters/cluster-123")
    }

    @Test
    fun `resolve appends path segments`() {
        val path = ClusterS3Path.from("my-bucket", "cluster-123")
        val resolved = path.resolve("spark-jars").resolve("app.jar")

        assertThat(resolved.toString())
            .isEqualTo("s3://my-bucket/clusters/cluster-123/spark-jars/app.jar")
        assertThat(resolved.getKey())
            .isEqualTo("clusters/cluster-123/spark-jars/app.jar")
    }

    @Test
    fun `resolve handles paths with slashes`() {
        val path = ClusterS3Path.from("my-bucket", "cluster-123")
        val resolved = path.resolve("spark-jars/nested/app.jar")

        assertThat(resolved.toString())
            .isEqualTo("s3://my-bucket/clusters/cluster-123/spark-jars/nested/app.jar")
    }

    @Test
    fun `resolve ignores empty segments`() {
        val path = ClusterS3Path.from("my-bucket", "cluster-123")
        val resolved = path.resolve("spark-jars//app.jar")

        assertThat(resolved.toString())
            .isEqualTo("s3://my-bucket/clusters/cluster-123/spark-jars/app.jar")
    }

    @Test
    fun `getParent returns parent path`() {
        val path =
            ClusterS3Path
                .from("my-bucket", "cluster-123")
                .resolve("spark-jars")
                .resolve("app.jar")

        val parent = path.getParent()

        assertThat(parent).isNotNull
        assertThat(parent.toString())
            .isEqualTo("s3://my-bucket/clusters/cluster-123/spark-jars")
    }

    @Test
    fun `getParent returns null for root`() {
        val path = ClusterS3Path.root("my-bucket")

        assertThat(path.getParent()).isNull()
    }

    @Test
    fun `getFileName returns last segment`() {
        val path =
            ClusterS3Path
                .from("my-bucket", "cluster-123")
                .resolve("spark-jars")
                .resolve("app.jar")

        assertThat(path.getFileName()).isEqualTo("app.jar")
    }

    @Test
    fun `getFileName returns null for root`() {
        val path = ClusterS3Path.root("my-bucket")

        assertThat(path.getFileName()).isNull()
    }

    @Test
    fun `toString returns full S3 URI`() {
        val path =
            ClusterS3Path
                .from("my-bucket", "cluster-123")
                .resolve("backups")
                .resolve("snapshot.tar.gz")

        assertThat(path.toString())
            .isEqualTo("s3://my-bucket/clusters/cluster-123/backups/snapshot.tar.gz")
    }

    @Test
    fun `toUri returns same as toString`() {
        val path = ClusterS3Path.from("my-bucket", "cluster-123").resolve("file.txt")

        assertThat(path.toUri()).isEqualTo(path.toString())
    }

    @Test
    fun `root creates path without cluster namespacing`() {
        val path = ClusterS3Path.root("my-bucket")

        assertThat(path.toString()).isEqualTo("s3://my-bucket")
        assertThat(path.getKey()).isEmpty()
    }

    @Test
    fun `sparkJars returns correct path`() {
        val path = ClusterS3Path.from("my-bucket", "cluster-123")
        val jarsPath = path.sparkJars()

        assertThat(jarsPath.toString())
            .isEqualTo("s3://my-bucket/clusters/cluster-123/spark-jars")
    }

    @Test
    fun `backups returns correct path`() {
        val path = ClusterS3Path.from("my-bucket", "cluster-123")
        val backupsPath = path.backups()

        assertThat(backupsPath.toString())
            .isEqualTo("s3://my-bucket/clusters/cluster-123/backups")
    }

    @Test
    fun `logs returns correct path`() {
        val path = ClusterS3Path.from("my-bucket", "cluster-123")
        val logsPath = path.logs()

        assertThat(logsPath.toString())
            .isEqualTo("s3://my-bucket/clusters/cluster-123/logs")
    }

    @Test
    fun `data returns correct path`() {
        val path = ClusterS3Path.from("my-bucket", "cluster-123")
        val dataPath = path.data()

        assertThat(dataPath.toString())
            .isEqualTo("s3://my-bucket/clusters/cluster-123/data")
    }

    @Test
    fun `convenience methods can be chained with resolve`() {
        val path = ClusterS3Path.from("my-bucket", "cluster-123")
        val jarPath = path.sparkJars().resolve("myapp.jar")

        assertThat(jarPath.toString())
            .isEqualTo("s3://my-bucket/clusters/cluster-123/spark-jars/myapp.jar")
        assertThat(jarPath.getFileName()).isEqualTo("myapp.jar")
    }

    @Test
    fun `immutability - resolve returns new instance`() {
        val original = ClusterS3Path.from("my-bucket", "cluster-123")
        val resolved = original.resolve("subdir")

        assertThat(original.toString()).isEqualTo("s3://my-bucket/clusters/cluster-123")
        assertThat(resolved.toString()).isEqualTo("s3://my-bucket/clusters/cluster-123/subdir")
    }
}
