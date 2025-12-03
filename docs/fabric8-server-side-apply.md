# Fabric8 Server-Side Apply Pattern

This document explains a common error when using fabric8's Kubernetes client for server-side apply operations, and the correct pattern to use.

## The Error

When applying Kubernetes manifests using fabric8, you may encounter:

```
java.lang.IllegalStateException: Could not find a registered handler for item:
[GenericKubernetesResource(apiVersion=v1, kind=Namespace, metadata=ObjectMeta...)]
```

This is a **client-side fabric8 error**, not a Kubernetes server error.

## Root Cause

Fabric8 has two loading paths with different behaviors:

1. **Typed Loader** (works): `client.namespaces().load(stream)` → returns typed `Namespace` → `serverSideApply()` works
2. **Generic Loader** (fails): `client.load(stream)` → returns `GenericKubernetesResource` → `serverSideApply()` fails

**Critical**: Items returned by `client.load()` are **always** `GenericKubernetesResource` at runtime, regardless of the YAML content. They **cannot** be cast to typed classes like `Namespace` or `ConfigMap`.

## Patterns That Do NOT Work

### Attempt 1: Direct serverSideApply on loader
```kotlin
// DON'T DO THIS - causes "Could not find a registered handler" error
client.load(inputStream).serverSideApply()
```

### Attempt 2: Load items then use client.resource()
```kotlin
// DON'T DO THIS - still fails with same error
val items = client.load(inputStream).items()
for (item in items) {
    client.resource(item).serverSideApply()
}
```

Even though we load the items first, they are still `GenericKubernetesResource` objects internally, and `client.resource(item).serverSideApply()` still fails.

### Attempt 3: Cast GenericKubernetesResource to typed class
```kotlin
// DON'T DO THIS - causes ClassCastException
val items = client.load(inputStream).items()
for (item in items) {
    when (item.kind) {
        "Namespace" -> client.namespaces().resource(item as Namespace).serverSideApply()
        // ...
    }
}
```

**Error**: `java.lang.ClassCastException: class io.fabric8.kubernetes.api.model.GenericKubernetesResource cannot be cast to class io.fabric8.kubernetes.api.model.Namespace`

The items from `client.load()` are truly `GenericKubernetesResource` at runtime - they cannot be cast to typed classes.

## The Pattern That Works

Use **typed client loaders** directly with `forceConflicts()`:

```kotlin
private fun loadAndApplyManifest(client: KubernetesClient, file: File) {
    val yamlContent = file.readText()
    val kind = extractKind(yamlContent)

    ByteArrayInputStream(yamlContent.toByteArray()).use { stream ->
        when (kind) {
            "Namespace" -> client.namespaces().load(stream).forceConflicts().serverSideApply()
            "ConfigMap" -> client.configMaps().load(stream).forceConflicts().serverSideApply()
            "Service" -> client.services().load(stream).forceConflicts().serverSideApply()
            "DaemonSet" -> client.apps().daemonSets().load(stream).forceConflicts().serverSideApply()
            "Deployment" -> client.apps().deployments().load(stream).forceConflicts().serverSideApply()
            else -> throw IllegalStateException("Unsupported resource kind: $kind")
        }
    }
}

private fun extractKind(yamlContent: String): String {
    val kindRegex = Regex("""^kind:\s*(\w+)""", RegexOption.MULTILINE)
    return kindRegex.find(yamlContent)?.groupValues?.get(1)
        ?: throw IllegalStateException("Could not determine resource kind from YAML")
}
```

This works because:
1. **Typed loaders** (e.g., `client.namespaces().load(stream)`) return properly typed resources
2. Typed resources have registered handlers for `serverSideApply()`
3. **`forceConflicts()`** resolves field manager conflicts when multiple controllers manage the same resource

## Required Imports

```kotlin
import io.fabric8.kubernetes.client.KubernetesClient
import java.io.ByteArrayInputStream
import java.io.File
```

## Adding New Resource Types

If you need to support additional Kubernetes resource types, add them to the `when` statement:

```kotlin
"Pod" -> client.pods().load(stream).forceConflicts().serverSideApply()
"Secret" -> client.secrets().load(stream).forceConflicts().serverSideApply()
"StatefulSet" -> client.apps().statefulSets().load(stream).forceConflicts().serverSideApply()
// etc.
```

## References

- Fabric8 Kubernetes Client: https://github.com/fabric8io/kubernetes-client
- Server-side apply documentation: https://github.com/fabric8io/kubernetes-client/blob/main/doc/CHEATSHEET.md

## Fix History

| Date | Issue | Resolution |
|------|-------|------------|
| 2025-12-02 | `client.load().serverSideApply()` fails | Attempted: load items first, then apply via `client.resource(item)` |
| 2025-12-02 | `client.resource(item).serverSideApply()` also fails | Attempted: cast items to typed classes (e.g., `item as Namespace`) |
| 2025-12-02 | `item as Namespace` causes ClassCastException | Use typed loaders directly (`client.namespaces().load(stream)`) |
| 2025-12-02 | Patch operation fails for Namespace | **Fixed**: Add `forceConflicts()` before `serverSideApply()` |
