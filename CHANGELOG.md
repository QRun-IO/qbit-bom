# Changelog

All notable changes to QBit Build Parent POM will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- G1 default API packages include qbit-assistant's published provider SPI, `com.kingsrook.qbits.assistant.spi`; artifact `com.kingsrook.qbits:qbit-assistant-provider-api` is already covered by `com.kingsrook.qbits:*-api`. Assistant implementation packages remain forbidden.
- G1 default allow-lists include qbit-email's published API: artifact `com.kingsrook.qbits:qbit-email` and package `com.kingsrook.qbits.email.api` (as for qbit-object-storage), so consumers need no per-repo allow-list.

## [2.1.0] - 2026-10-13

### Added
- Independent `qbit-guardrails` JAR with inheritable Jupiter architecture, extension-contract and stand-in expiry checks (G1, G3, G4).
- Report-first Enforcer checks for cross-qbit implementation dependencies and first-party SNAPSHOT dependencies (G1, G7).
- Opt-in enforcement, API/artifact allow-lists, profile skip and strict UI API route forwarding (G2).
- Fixture tests and rollout/configuration documentation. Existing qbits receive warnings by default.

### Changed
- Parent revision is 2.1.0; the repository builds and publishes both artifacts as a reactor.
- Inherited Surefire is pinned to 3.5.6 and Enforcer to 3.6.3, with merging child configuration.

## [1.5.1] - 2025-12-28

### Fixed
- Fixed JUnit BOM version mismatch: updated from 5.12.1 to 6.0.1 to align with JUnit Jupiter 6.0.1 from qqq-bom-pom 0.35.0

## [1.5.0] - 2025-12-28

### Changed
- Migrated to Java 21 LTS (from Java 17)
- Updated qqq-bom-pom dependency to 0.35.0 (from 0.27.9)
- Updated JUnit BOM to 5.12.1 (for JUnit Jupiter 6.0.1 support)
- Updated qqq-orb to 0.5.0 (from 0.3.7)

## [1.0.0] 

### Added
- Comprehensive README documentation for parent POM
- Standard GitHub files (LICENSE, CHANGELOG, CONTRIBUTING, SECURITY, CODE_OF_CONDUCT)
- Proper project structure and documentation
- Initial parent POM implementation for QBit projects
- Centralized dependency management with QQQ BOM (0.27.9)
- JUnit 5 BOM integration (5.10.0)
- Maven plugin management for compiler, source, javadoc, GPG, and central publishing
- Release profile for Maven Central publishing
- GPG signing configuration for artifact security
- Sonatype Central publishing plugin integration
- Java 17+ configuration with UTF-8 encoding
- Standardized build properties and plugin versions

### Changed
- Updated README to reflect parent POM purpose and functionality
- Improved project documentation and structure
- Enhanced GitHub repository setup
- Initial public release of QBit Build Parent POM
- Established standardized build configuration for all QBit projects
- Created centralized dependency management approach

---

## 📚 For Detailed Information

**📖 [Complete Documentation Wiki](https://github.com/Kingsrook/qqq/wiki)** - Start here for comprehensive guides
- **[🏠 Home](https://github.com/Kingsrook/qqq/wiki/Home)** - Project overview and quick start
- **[🚀 Release Flow](https://github.com/Kingsrook/qqq/wiki/Release-Flow)** - Detailed release process
- **[🏷️ Changelog & Tagging](https://github.com/Kingsrook/qqq/wiki/Changelog-and-Tagging)** - Commit conventions and release notes
- **[🔧 Developer Onboarding](https://github.com/Kingsrook/qqq/wiki/Developer-Onboarding)** - Setup and contribution guide

## 🔄 Version Compatibility

QBit Build Parent POM follows semantic versioning:
- **MAJOR** versions may contain breaking changes to build configuration
- **MINOR** versions add new functionality (backward compatible)
- **PATCH** versions contain bug fixes (backward compatible)

For detailed compatibility information, see [QQQ Compatibility Matrix](https://github.com/Kingsrook/qqq/wiki/Compatibility-Matrix).

---

**Thank you for using QBit Build Parent POM!** 🚀
