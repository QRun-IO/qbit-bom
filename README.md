# QBit Build Parent

This repository publishes the Maven parent **`com.kingsrook:qbit-build-parent`**. Version **2.0.0** imports **`com.kingsrook.qqq:qqq-bom-pom:4.0.0`**, sets Java 21 compilation defaults, and provides shared release plugin configuration. Use it to adopt QQQ 4.0 in a QBit project.

## Use the parent

```xml
<parent>
   <groupId>com.kingsrook</groupId>
   <artifactId>qbit-build-parent</artifactId>
   <version>2.0.0</version>
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

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
