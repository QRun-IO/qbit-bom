# CLAUDE.md — qbit-bom

## Knowledge base

Reviewed dossiers for this repo and the wider QQQ platform live in the second-brain vault:

- Hub / start here: `R:/Git.Local/KofTwentyTwo/second-brain/knowledge/qqq/qqq-hub.md`
- This repo's dossier: `R:/Git.Local/KofTwentyTwo/second-brain/knowledge/qqq/repos/qbit-bom.md`
  (reviewed at commit `dd9a67723a7d`, branch `main`, 2026-07-04)

Key orientation facts (details and caveats in the dossier):

- Despite the repo name and README, this repo publishes `com.kingsrook:qbit-build-parent` —
  the shared Maven parent POM for the qbit repos. It is NOT a qbit-version BOM, and the
  README's `io.qrun:qbit-bom` coordinates do not exist on Maven Central.
- v1.6.0 imports `com.kingsrook.qqq:qqq-bom-pom:0.40.0` (Java 21, junit-bom 6.0.1);
  develop pins `0.41.0-SNAPSHOT`, which no longer resolves (qqq renumbered to 4.0.x).
- Known licensing inconsistency: LICENSE/NOTICE = Apache-2.0, pom `<licenses>` = AGPL-3.0
  (shipped to Central in 1.6.0), README says Proprietary. See dossier "Maturity & risks".
