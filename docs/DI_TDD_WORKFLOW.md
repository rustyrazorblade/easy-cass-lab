# TDD Dependency Injection Workflow

This workflow implements the dependency injection plan using Test-Driven Development with minimal, incremental changes.

## Workflow Strategy: TDD + Minimal Changes

**Core Principle**: Write failing tests first, make minimal changes to pass, refactor safely

**Change Size**: One class conversion per iteration, maximum 10-15 lines of changes per test cycle

**Prompt for Review**: After each change and test, prompt me to review the change.  I may ask to commit the changes.

## Phase 1: AWS Core Infrastructure (TDD)

### Step 1.1: Create AWS Module Foundation

**🔴 Red Phase: Write Failing Test**
```kotlin
// File: src/test/kotlin/com/rustyrazorblade/easycasslab/providers/aws/ClientsTest.kt
@Test
fun `should inject User dependency through Koin`() {
    // Given
    val mockUser = mock<User>()
    val testModule = module {
        single { mockUser }
        factory { Clients(get()) }
    }
    
    startKoin { modules(testModule) }
    
    // When
    val clients: Clients by inject()
    
    // Then - test will fail until Clients implements KoinComponent
    assertNotNull(clients)
    stopKoin()
}
```

**🟢 Green Phase: Make Test Pass**
- Convert `Clients` to implement `KoinComponent`
- Change constructor parameter from `User` to injected dependency
- Create AWS Koin module

**🔵 Refactor Phase: Clean Up**
- Extract test module setup
- Ensure consistent naming patterns

### Step 1.2: Convert Clients Class to KoinComponent

**Before:**
```kotlin
class Clients(userConfig: User) {
    // existing implementation
}
```

**🔴 Red Phase: Write Test**
```kotlin
@Test 
fun `Clients should work with injected User`() {
    // Test that will fail until conversion is complete
}
```

**🟢 Green Phase: Minimal Change**
```kotlin
class Clients : KoinComponent {
    private val userConfig: User by inject()
    // rest unchanged
}
```

**Acceptance Criteria:**
- ✅ Test passes
- ✅ Existing functionality unchanged
- ✅ No breaking changes to current usage

### Step 1.3: Create AWS Koin Module

**🔴 Red Phase: Write Test**
```kotlin
@Test
fun `AWS module should provide all dependencies`() {
    startKoin {
        modules(awsModule, testUserModule)
    }
    
    val clients: Clients by inject()
    val aws: AWS by inject()
    
    assertNotNull(clients)
    assertNotNull(aws)
    stopKoin()
}
```

**🟢 Green Phase: Create Module**
```kotlin
// File: src/main/kotlin/com/rustyrazorblade/easycasslab/di/AWSModule.kt
val awsModule = module {
    factory { Clients() }
    factory { AWS(get()) }
}
```

### Step 1.4: Convert AWS Class to KoinComponent

**🔴 Red Phase: Write Test**
```kotlin
@Test
fun `AWS should inject Clients dependency`() {
    // Test will fail until AWS uses KoinComponent
}
```

**🟢 Green Phase: Convert AWS**
```kotlin
class AWS : KoinComponent {
    private val clients: Clients by inject()
    // minimal changes only
}
```

### Step 1.5: Update Context Module Integration

**🔴 Red Phase: Write Test**
```kotlin
@Test
fun `Context should work with AWS module integration`() {
    // Integration test
}
```

**🟢 Green Phase: Add AWS Module**
```kotlin
// Add awsModule to main Koin configuration
```

## Phase 2: TFState Infrastructure

### Step 2.1: TFState Constructor Analysis

**🔴 Red Phase: Write Test**
```kotlin
@Test
fun `TFState should inject Context dependency`() {
    // Current: TFState(context, inputStream)
    // Goal: TFState(inputStream) with injected Context
}
```

**🟢 Green Phase: Minimal Conversion**
- Convert `TFState` to `KoinComponent`
- Inject `Context`, keep `InputStream` as constructor parameter
- Update callers one at a time

### Step 2.2: Update TFState Callers

**🔴 Red Phase: Write Tests for Each Caller**
- Test each class that creates `TFState` instances
- Ensure backward compatibility

**🟢 Green Phase: Update One Caller Per Iteration**
- Update caller classes one at a time
- Maintain existing public API

## Phase 3: Container System (Terraform & Packer)

### Step 3.1: Terraform Container Conversion

**Current State**: `Terraform` already injects `Docker`

**🔴 Red Phase: Write Test**
```kotlin
@Test
fun `Terraform should inject all dependencies via Koin`() {
    // Test current Docker injection + any additional dependencies
}
```

**🟢 Green Phase: Complete Conversion**
- Ensure `Context` is also injected if needed
- Remove constructor parameters

### Step 3.2: Packer Container Conversion

**Current State**: `Packer` already injects `Docker` and `OutputHandler`

**🔴 Red Phase: Write Test**
```kotlin
@Test
fun `Packer should work with full Koin injection`() {
    // Test existing injection pattern
}
```

**🟢 Green Phase: Verify and Complete**
- Ensure all dependencies properly injected
- Remove remaining constructor parameters

## Testing Strategy

### Test Structure per Class Conversion

```kotlin
class ClassNameTest {
    @BeforeEach
    fun setup() {
        KoinTestHelper.startKoin()
    }
    
    @AfterEach  
    fun teardown() {
        KoinTestHelper.stopKoin()
    }
    
    @Test
    fun `should inject dependencies correctly`() {
        // Red phase: Write failing test
    }
    
    @Test
    fun `should maintain existing functionality`() {
        // Green phase: Verify behavior unchanged
    }
    
    @Test
    fun `should work in integration scenarios`() {
        // Refactor phase: Test with other components
    }
}
```

### Test Categories

1. **Unit Tests**: Individual class conversion
2. **Integration Tests**: Class interactions after conversion
3. **Regression Tests**: Existing functionality unchanged
4. **End-to-End Tests**: Full workflow still works

## Change Size Guidelines

### Per TDD Cycle (Red-Green-Refactor):
- **Maximum 15 lines** of production code changes
- **One logical concept** per change (e.g., "add KoinComponent interface")
- **Single responsibility** per test

### Per Step:
- **One class conversion** per step
- **All tests must pass** before moving to next step
- **No skipping of refactor phase**

## Rollback Strategy

### If Tests Fail:
1. **Revert last change** immediately
2. **Analyze failure** - test issue vs implementation issue
3. **Smaller change** - break down further if needed
4. **Re-attempt** with smaller scope

### If Integration Issues:
1. **Isolate problem** to specific class
2. **Temporary feature flag** if needed for gradual rollout
3. **Revert to last known good state**
4. **Re-plan approach** with smaller changes

## Success Metrics

### Per Step:
- ✅ All existing tests pass
- ✅ New tests pass
- ✅ No breaking changes to public API
- ✅ No regression in functionality

### Per Phase:
- ✅ All classes in phase converted successfully
- ✅ Integration tests pass
- ✅ Performance unchanged
- ✅ Memory usage unchanged

### Overall:
- ✅ All dependency injection conversions complete
- ✅ Comprehensive test coverage
- ✅ Improved testability (mockable dependencies)
- ✅ Consistent DI patterns across codebase

## Development Commands Integration

### Run Tests After Each Change:
```bash
./gradlew test --tests="*ClientsTest*"
./gradlew test --tests="*AWSTest*"
./gradlew test --tests="*TFStateTest*"
```

### Verify No Regressions:
```bash
./gradlew test  # All tests must pass
```

### Check Coverage:
```bash
./gradlew jacocoTestReport  # Ensure coverage maintained/improved
```

## Timeline Estimation

### Phase 1 (AWS Infrastructure): **2-3 days**
- Step 1.1-1.2: 4 hours (Clients conversion)
- Step 1.3-1.4: 4 hours (AWS conversion + module)
- Step 1.5: 2 hours (Integration)

### Phase 2 (TFState): **1-2 days**
- Step 2.1: 3 hours (TFState conversion)
- Step 2.2: 3 hours (Update callers)

### Phase 3 (Containers): **1 day**
- Step 3.1-3.2: 4 hours (Complete container conversions)

### **Total Estimated Time: 4-6 days**

## Risk Mitigation

### High-Risk Areas:
- **AWS class**: Used throughout application
- **TFState**: Core functionality for Terraform integration
- **Context integration**: May affect many callers

### Mitigation Strategies:
- **Feature flags** for gradual rollout
- **Extensive testing** at each step
- **Rollback plan** for each change
- **Small change size** to minimize blast radius

## Next Actions

1. **Create branch**: `feature/di-aws-phase1`
2. **Start with Step 1.1**: Write failing test for Clients
3. **Follow TDD cycle**: Red-Green-Refactor
4. **Commit after each passing test**
5. **Run full test suite** before each commit