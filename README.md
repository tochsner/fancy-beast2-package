# BEAST 3 Package Skeleton

A minimal, ready-to-build template for creating a BEAST 3 external package
using the strongly-typed `spec` class hierarchy.

This skeleton demonstrates:
- A custom scalar distribution (`MyDistribution`) extending `ScalarDistribution` — usable directly as a prior (no `Prior` wrapper)
- A custom MCMC operator (`MyScaleOperator`) working with `RealScalarParam`
- JPMS `module-info.java` with `provides` declarations
- `version.xml` for package service discovery
- JUnit 5 testing with the new strongly-typed API
- A BEAST XML file using both custom classes with `RealScalarParam` and domain constraints

## Prerequisites

- Java 25+
- Maven 3.9+

BEAST 3 artifacts are resolved from [Maven Central](https://central.sonatype.com/namespace/io.github.compevol) — no extra configuration needed.

If you want to develop against an unreleased SNAPSHOT version of BEAST 3, you can either add the GitHub Packages repository to your `pom.xml` (requires a [personal access token](https://github.com/settings/tokens) with `read:packages` scope in `~/.m2/settings.xml`), or install BEAST 3 to your local Maven repository from source:

```bash
cd /path/to/beast3
mvn install -DskipTests
```

## Build and test

```bash
mvn compile   # compile against beast-base
mvn test      # run MyDistributionTest
```

## How to customise this skeleton

1. **Rename the Maven coordinates** in `pom.xml`:
   - Change `groupId` (should be a verified Maven Central namespace, e.g. `io.github.yourname`), `artifactId`, and `version`
   - Update `<url>`, `<developers>`, and `<scm>` to point to your repository

2. **Rename the Java module** in `src/main/java/module-info.java`:
   - Change `module my.beast.example` to your module name
   - Update `exports` and `provides` declarations

3. **Rename the Java package** under `src/main/java/`:
   - Move source files to your package directory
   - Update `package` statements in all `.java` files

4. **Update `version.xml`**:
   - Change the package `name` and `version`
   - List your `BEASTInterface` providers

5. **Replace the example classes** with your own:
   - See `MyDistribution.java` for the `ScalarDistribution` pattern
   - See `MyScaleOperator.java` for the `Operator` + `RealScalarParam` pattern

6. **Update `src/assembly/beast-package.xml`**:
   - This file controls what goes into the BEAST package ZIP (built by `release.sh`)
   - Update the `<includes>` to match your Maven `groupId:artifactId`
   - Update the `<fileSets>` paths to match your module name (replace `my.beast.example` with your JPMS module name)
   - Add `<include>` lines for any third-party runtime dependencies your package needs

## Key concepts (new spec API)

| Old (deprecated)                    | New (spec)                           |
|-------------------------------------|--------------------------------------|
| `RealParameter`                     | `RealScalarParam<D>` / `RealVectorParam<D>` |
| `ParametricDistribution`            | `ScalarDistribution<S, T>`           |
| `Prior` wrapper + `ParametricDistribution` | Distribution with `param` input (acts as its own prior) |
| `lower`/`upper` bounds              | Domain types: `Real`, `PositiveReal`, `NonNegativeReal`, `UnitInterval` |

## Adding GUI (BEAUti) support

If your package includes BEAUti input editors, alignment providers, or other
GUI components, you have two options for how to organise them.

### Option A: single module (recommended for most packages)

Keep everything in one Maven artifact and one JPMS module. Use `requires static`
so the module loads without JavaFX on the module path (headless/cluster runs):

Add the dependency to `pom.xml`:

```xml
<dependency>
    <groupId>io.github.compevol</groupId>
    <artifactId>beast-fx</artifactId>
    <version>${beast.version}</version>
</dependency>
```

In `module-info.java`, declare the GUI dependencies as **static** (compile-time only):

```java
open module my.beast.example {
    requires beast.pkgmgmt;
    requires beast.base;
    requires static beast.fx;         // optional at runtime
    requires static javafx.controls;  // optional at runtime

    exports my.beast.example;
    exports my.beast.example.app.beauti;  // GUI classes

    provides beast.base.core.BEASTInterface with
        my.beast.example.MyDistribution,
        my.beast.example.MyScaleOperator,
        my.beast.example.app.beauti.MyAlignmentProvider;
}
```

**Convention:** place GUI classes in a `*.app.beauti` subpackage to keep them
separate from core logic.

When running headless (no beast-fx on the module path), the module loads normally.
BEAUti provider classes are registered by name but never instantiated, so the
missing GUI dependencies cause no errors. When running with BEAUti, everything
works as expected.

### Option B: two modules (core + fx)

Split into a parent POM with two submodules: one for core logic and one for GUI.
This is the pattern used by beast3 itself (`beast-base` + `beast-fx`) and by
[morph-models](https://github.com/CompEvol/morph-models).

Use this when your package has substantial GUI code (multiple custom input
editors, complex BEAUti panels) that warrants its own module.

```
my-package/
    pom.xml                    (parent, packaging: pom)
    beast-my-package/          (core module)
        pom.xml
        src/main/java/module-info.java
    beast-my-package-fx/       (GUI module, depends on core)
        pom.xml
        src/main/java/module-info.java
```

The core module has no JavaFX dependency at all. The fx module declares
`requires beast.fx;` and `requires javafx.controls;` as regular (non-static)
dependencies.

**Trade-off:** cleaner separation, but doubles the number of Eclipse/IDE projects.
For most packages where the GUI code is one or two classes, Option A is simpler.

## Releasing your package

The included `release.sh` script automates the full release process: build, package, and
optionally create a GitHub release.

### 1. Build the package ZIP

```bash
./release.sh
```

This will:
- Read the package name and version from `version.xml`
- Run `mvn clean package -DskipTests`
- Assemble a BEAST package ZIP with the correct flat structure
- Output a file like `MyPackage.v1.0.0.zip`

**Linux/Ubuntu users:** if `./release.sh` fails with errors about `\r` characters, your
git checkout may have converted line endings to CRLF. Fix with:

```bash
tr -d '\r' < release.sh > release_fixed.sh && mv release_fixed.sh release.sh
chmod +x release.sh
```

Or run the script explicitly with bash: `bash release.sh`

### 2. Create a GitHub release

```bash
./release.sh --release
```

This additionally creates a GitHub release (e.g. `v1.0.0`) with the ZIP attached,
and prints the CBAN XML entry you'll need for the next step.

### 3. Submit to CBAN

The [CBAN repository](https://github.com/CompEvol/CBAN) is where BEAST's Package
Manager discovers available packages. To make your package installable:

1. Fork [CompEvol/CBAN](https://github.com/CompEvol/CBAN)
2. Add your package entry to `packages2.8.xml` (the `--release` flag prints this for you):

```xml
<package name="MyPackage" version="1.0.0"
    url="https://github.com/YOU/YOUR-REPO/releases/download/v1.0.0/MyPackage.v1.0.0.zip"
    projectURL="https://github.com/YOU/YOUR-REPO"
    description="One-line description of your package">
    <depends on="BEAST.base" atleast="2.8.0"/>
</package>
```

3. Open a pull request against CompEvol/CBAN

Once merged, your package will appear in the BEAST Package Manager.

### 4. Local testing

To test your package locally before releasing, install the built ZIP into BEAST's
package directory:

```bash
# Build the ZIP
./release.sh

# Install to the local BEAST package directory
PKG=MyPackage                          # your package name from version.xml
BEAST_PKG_DIR=~/.beast/2.8/$PKG       # macOS and Linux
# Windows: %USERPROFILE%\.beast\2.8\MyPackage

mkdir -p "$BEAST_PKG_DIR"
unzip -o "$PKG.v1.0.0.zip" -d "$BEAST_PKG_DIR"
```

After installation, BEAST/BEAUti will discover the package on next launch. You can
verify with:

```bash
packagemanager -list
```

## Publishing to Maven Central

BEAST 3 can also install packages directly from Maven Central. This is an
alternative (or complement) to the ZIP/CBAN distribution above.

The recommended path is to publish automatically via GitHub Actions on a
`v*` tag push. For a full step-by-step setup guide (Sonatype account,
namespace verification, GPG key, repo secrets, the `ci-publish.yml`
workflow, troubleshooting), see
[**`package-release-setup.md`**](https://github.com/CompEvol/beast3/blob/master/scripts/package-release-setup.md)
in the beast3 repo.

### Quick local deploy (manual alternative)

If you've already configured `~/.m2/settings.xml` with a `central` server
entry and have GPG set up locally, you can deploy from your machine:

```bash
mvn clean deploy -Prelease
```

This builds the JAR (with `version.xml` embedded), generates sources and javadoc
JARs, signs everything with GPG, and uploads to Maven Central.

### User install

Once published, BEAST 3 users can install your package with:

```
Package Manager > Install from Maven > groupId:artifactId:version
```

Or from the command line:

```bash
packagemanager -maven groupId:artifactId:version
```

### ZIP structure

The BEAST Package Manager expects a flat ZIP (no wrapper directory) containing:

```
version.xml            # required — package name, version, service providers
lib/                   # required — your JARs (and any third-party runtime deps)
fxtemplates/           # optional — BEAUti templates
examples/              # optional — example BEAST XML files and data
```

**Important:** the ZIP must NOT contain a top-level directory named after your package.
The Package Manager extracts the ZIP into its own directory, so a wrapper would
cause double-nesting and break service discovery.

## Further reading

- [BEAST 3 source](https://github.com/CompEvol/beast3)
- [BEAST 2 → 3 migration guide](https://github.com/CompEvol/beast3/blob/master/scripts/migration-guide.md)
- [morph-models](https://github.com/CompEvol/morph-models) — worked example of a two-module (core + fx) BEAST 3 package (Option B)
