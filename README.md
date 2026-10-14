# QBit Build Parent

This repository publishes the Maven parent **`com.kingsrook:qbit-build-parent`** and the **`com.kingsrook:qbit-guardrails`** test JAR. Version **2.1.0** adds report-first architecture checks, retains the **`com.kingsrook.qqq:qqq-bom-pom:4.0.0`** baseline, sets Java 21 compilation defaults, and provides shared release plugin configuration. Qbits using QQQ 4.1 release candidates import their chosen `qqq-bom-pom:4.1.0-RC.x` in their own dependency management.

## Use the parent

```xml
<parent>
   <groupId>com.kingsrook</groupId>
   <artifactId>qbit-build-parent</artifactId>
   <version>2.1.0</version>
   <relativePath/>
</parent>

<groupId>com.example.qbits</groupId>
<artifactId>my-qbit</artifactId>
<version>1.0.0-SNAPSHOT</version>

<dependencies>
   <dependency>
      <groupId>com.kingsrook.qqq</groupId>
      <artifactId>qqq-backend-core</artifactId>
   </dependency>
</dependencies>
```

Requires Java 21 and Maven 3.8 or later. Resolve releases from Maven Central. The imported QQQ BOM supplies framework dependency versions; **production QBit dependency versions are still chosen by each application**. This parent does not certify the production QBit fleet as compatible with QQQ 4.0.

The previous parent release, 1.6.0, imports QQQ 0.40.0. Moving to parent 2.0.0 therefore includes QQQ's breaking API changes; follow the [4.0 migration guide](https://github.com/QRun-IO/qqq/blob/main/docs/migration/4.0.adoc).

## Release workflow

The parent can be published only after its imported final QQQ BOM is available. Run `mvn clean verify` and test consumption from a separate child project before tagging. The existing CircleCI release workflow publishes a final version tag through the `release` Maven profile; a `release/*` branch produces a release candidate.

Both reactor projects have independent release profiles. Publish them together with the same revision; for an alternate release revision also set `-Dqbit.guardrails.version=<revision>` so the parent's dependency points at that release. The guardrail module deliberately has no parent, avoiding a dependency cycle. Its production dependencies are ArchUnit core 1.5.1, JUnit Jupiter API 6.0.1 and Maven artifact 3.9.11; it does not depend on QQQ. The inherited plugins are Surefire 3.5.6 and Enforcer 3.6.3.

## Architecture guardrails

All checks **report by default**: Enforcer logs warnings, and the inherited Jupiter tests print summaries such as `[qbit-guardrails] G1-arch FAIL (report-only): 2 violations` with details. Report-only tests pass without JUnit assumptions or skipped-test results. Existing violations therefore do not fail an upgrading qbit's build.

Opt into enforcement in a qbit's POM (or pass `-Dqbit.guardrails.enforce=true`):

```xml
<properties>
   <qbit.guardrails.enforce>true</qbit.guardrails.enforce>
   <qbit.guardrails.strictUiApiRoutes>true</qbit.guardrails.strictUiApiRoutes>
</properties>
```

| Rule | Check |
| --- | --- |
| G1-deps | Enforcer bans cross-qbit dependencies in compile, runtime, provided and system scopes, including transitives, except allow-listed artifacts. Test scope is allowed. |
| G1-arch | ArchUnit checks the qbit's main output for foreign qbit implementation references. Own packages, QQQ, JDK and third-party packages are allowed. |
| G1-literals | Java string literals beginning with another qbit's class/package name obey the same API boundary. Diagnostics include file and line. Comments and character literals are ignored; dynamic string construction is outside this check. |
| G2 | `qbit.guardrails.strictUiApiRoutes=true` forwards `qqq.uiApi.strictRoutes=true` to tests. QQQ 4.1.0-RC.6+ rejects raw UI API routes at server startup. Harmless on older core versions. |
| G3 | A main class referencing `MiddlewareSpecExtension` requires at least one `src/main/resources/openapi/qqq-v1/*.yaml` and a compiled test class referencing `UiApiContract` or `UiApiContractAssertions`. In a Git checkout the YAML must exist in HEAD; source archives are checked for source presence. |
| G4 | Malformed or expired `STAND-IN` markers in main/test Java sources are violations. Untagged stand-in prose is advisory. |
| G7 | Enforcer rejects SNAPSHOT dependencies in `com.kingsrook.qqq` and `com.kingsrook.qbits`, including transitives and test dependencies. Other groups, the project's own SNAPSHOT revision and its parent are not banned. |

Main classes that call Javalin route registration methods and contain string literals starting `/qqq/v1/` are listed as **raw /qqq/v1 routes**. These static findings are always warnings, even with enforcement enabled. Runtime G2 checking provides the authoritative route audit. Qbit CI should enable G2 now; the planned default flip is with core **4.1.0-RC.7**. This parent defaults `qbit.guardrails.strictUiApiRoutes` to `false` for the transition.

Configuration properties:

| Property | Default / meaning |
| --- | --- |
| `qbit.guardrails.enforce` | `false`; fail on G1, G3, G4 and G7 violations when `true`. |
| `qbit.guardrails.allowedQbitArtifacts` | Comma-separated Maven `groupId:artifactId` patterns, with `*` wildcards. Default: `com.kingsrook.qbits:qbit-object-storage,com.kingsrook.qbits:qbit-email,com.kingsrook.qbits:*-api`. Replacing this property replaces the allow-list. |
| `qbit.guardrails.allowedApiPackages` | Comma-separated package prefixes. Default: `com.kingsrook.qbits.objectstorage.api,com.kingsrook.qbits.email.api`. A trailing `..` is accepted. Package boundaries are exact (`apiInternal` is not `api`). |
| `qbit.guardrails.ownPackage` | Optional JVM/CLI property: fourth segment (e.g. `alerts`) or full `com.kingsrook.qbits.alerts` prefix. Otherwise the most frequent fourth segment among main classes is used, with lexical tie-breaking. |
| `qbit.guardrails.strictUiApiRoutes` | `false`; opt into core's strict runtime route audit in tests. |
| `qbit.guardrails.skip` | Unset. `-Dqbit.guardrails.skip=true` disables the entire profile. Activation is based on property absence, so even setting it to `false` disables the profile; leave it unset to enable checks. |

Allowed `*-api` JARs automatically add their actual `com.kingsrook.qbits.<x>.api..` packages, identified using Maven metadata and class entries in the test classpath. An artifact exemption alone does **not** expose its implementation packages: object storage is exempt at the dependency level, while only its configured API package is allowed in code. Test-only qbit implementation dependencies are legal Maven dependencies but still cannot be referenced from main classes.

The parent adds the test JAR and merges Surefire `dependenciesToScan` and system properties through plugin management. Child Surefire settings are preserved; do not replace inherited configuration with `combine.self="override"`. Custom child includes/excludes or skipped tests can prevent discovery; use names matching `*GuardrailTest`. Missing/empty main output passes gracefully, including POM-only children. To run only the inherited tests:

```shell
mvn -B test -DskipTests=false -Dqbit.guardrails.enforce=true -Dtest='*GuardrailTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Enforcer runs at `validate`, with transitive and direct passes because Enforcer 3.6.3's transitive resolver omits optional direct dependencies. The direct pass covers these too. If Enforcer blocks compilation during diagnosis, first record its findings, then use `-Denforcer.skip=true` with the test command to see the independent architecture/source results. That diagnostic command is not a full passing build.

### Stand-in marker grammar

Place each marker on a single Java source line, normally in a comment:

```java
// STAND-IN(core=QRun-IO/qqq#1234, removeBy=4.1.0-RC.6)
// STAND-IN(core=QRun-IO/qbit-x#42, removeBy=4.2.0)
```

The exact case-sensitive grammar is `STAND-IN(core=OWNER/REPO#ISSUE, removeBy=VERSION)`: no whitespace except one space after the comma. OWNER and REPO contain letters, digits, `_`, `.` or `-`; ISSUE is a positive integer; VERSION is `MAJOR.MINOR.PATCH` with optional dot/dash-separated alphanumeric qualifiers (e.g. `-RC.6`). Every marker is checked, including multiple markers on one line. A marker-shaped `stand-in(...)` or `standin(...)` with wrong syntax/case is malformed. Ordinary `stand-in`/`standin` prose without a marker produces only a tagging warning.

The running core version is read from `META-INF/maven/com.kingsrook.qqq/qqq-backend-core/pom.properties`, falling back to `-Dqqq.version=...`. Maven `ComparableVersion` determines expiry: `4.1.0-RC.5 < 4.1.0-RC.6 < 4.1.0`; equality is expired. A marker naming another repository still uses the **QQQ core milestone** for `removeBy`. If no core version is available, valid markers warn that expiry could not be evaluated; malformed markers still violate G4.

## License

See [LICENSE](LICENSE) and [NOTICE](NOTICE).
