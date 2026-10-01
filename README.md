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

### Packaged notices

The packaging change in this source tree copies each consumer's own root
`LICENSE` and `NOTICE` into `META-INF` in main/test archives and Maven shared
archive resources used by source/Javadoc archives. Existing main/test resources
remain included. Consumers must adopt a newly published parent version to receive
this change; the existing public 2.0.0 artifact is unchanged.

The default `qbit.noticesDirectory` is `${project.basedir}`. In a nested module
whose files live at the repository root, set it to `${project.basedir}/..` (adjust
for the actual layout). Do not point it at the build-parent checkout: these are
the consuming project's notices. If a child overrides `<resources>` or
`<testResources>`, retain the inherited shared archive resource directory.

After packaging the consumer, explicitly check every produced archive using
Python 3 and the guard from this checkout:

```bash
mvn -Prelease -DskipTests -Dgpg.skip=true clean package
python3 /path/to/qbit-bom/scripts/check-packaged-notices.py --root . target/*.jar
```

For a multi-module consumer, pass each module's actual JAR paths and its repository
root. The guard rejects missing archives, missing/duplicate entries and changed
notice bytes. Packaging-only checks skip runtime tests; existing tests, review
and release requirements still apply. The publishing workflow does not invoke
this guard automatically.

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
