# qbit-bom

Maven Bill of Materials for QBit dependencies.

**For:** QQQ developers managing multiple QBit dependencies  
**Status:** Stable

## Why This Exists

QBits have interdependencies and version requirements. Managing compatible versions across qbit-user-role-permissions, qbit-workflows, qbit-webhooks, and others becomes tedious. Version mismatches cause runtime errors.

This BOM (Bill of Materials) declares compatible versions for all QBits. Import it once and omit version numbers from individual QBit dependencies.

## Features

- **Version Management** - Single source of truth for QBit versions
- **Compatibility Guarantee** - All declared versions tested together
- **Simplified POMs** - No version numbers on individual dependencies
- **Transitive Alignment** - Consistent versions for shared dependencies

## Quick Start

### Import the BOM

Add to your `pom.xml` in the `<dependencyManagement>` section:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.qrun</groupId>
            <artifactId>qbit-bom</artifactId>
            <version>1.5.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Add QBits Without Versions

```xml
<dependencies>
    <dependency>
        <groupId>io.qrun</groupId>
        <artifactId>qbit-user-role-permissions</artifactId>
        <!-- version managed by BOM -->
    </dependency>
    <dependency>
        <groupId>io.qrun</groupId>
        <artifactId>qbit-workflows</artifactId>
    </dependency>
    <dependency>
        <groupId>io.qrun</groupId>
        <artifactId>qbit-webhooks</artifactId>
    </dependency>
</dependencies>
```

## Included QBits

| QBit | Current Version |
|------|-----------------|
| qbit-user-role-permissions | 0.3.0 |
| qbit-workflows | 0.2.0 |
| qbit-webhooks | 0.2.0 |
| qbit-sftp-data-integration | 0.2.0 |
| qbit-standard-process-trace | 0.1.0 |
| qbit-customizable-table-views | 0.2.0 |

## Compatibility

| BOM Version | QQQ Core Version | Java |
|-------------|------------------|------|
| 1.5.x | 0.35.0+ | 21 |
| 1.4.x | 0.27.0 - 0.34.x | 17 |
| 0.3.x | 0.25.0 - 0.26.x | 17 |
| 0.2.x | 0.22.0 - 0.24.x | 17 |
| 0.1.x | 0.20.0 - 0.21.x | 17 |

## Project Status

Stable. Updated with each QBit release.

## Contributing

1. Fork the repository
2. Update versions in `pom.xml`
3. Submit a pull request

## License

Proprietary - QRun.IO
