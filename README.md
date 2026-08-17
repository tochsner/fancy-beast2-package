# CycleModel — a cyclic nucleotide substitution model for BEAST 3

📖 **[Full documentation and usage guide →](https://niconeureiter.github.io/fancy-beast2-package/)**

A BEAST 3 package providing `CycleModel`, a nucleotide substitution model in which a substitution
can only ever *increment* the state, wrapping around from T back to A:

```
  A -> C -> G -> T -> A
```

Nothing else is possible — no going backwards, no skipping ahead. Which substitution happens next
is fully determined by the current state; only the waiting time is random. The model takes one
input, `rates`, where `rates[i]` is the rate of the single substitution leaving state `i`.

Two properties make it unusual among BEAST substitution models:

- **It derives its own equilibrium frequencies.** Flux balance around the ring gives
  `π_i ∝ 1/r_i`, so there is no `frequencies` input — supplying one is an error.
- **It is non-reversible**, and its eigenvalues are complex in general. It therefore extends
  `BasicComplexSubstitutionModel` rather than the usual `BasicGeneralSubstitutionModel`, and it
  gives the model a direction in time: unlike a reversible model, the likelihood depends on where
  the root is.

The [documentation site](https://niconeureiter.github.io/fancy-beast2-package/) covers the maths,
installation, XML usage, worked examples, and — importantly — which MCMC operators are and are not
appropriate for this model.

This package targets **BEAST 2.8 / BEAST 3** and the new strongly-typed `beast.base.spec` API
(despite the repository name).

## Prerequisites

- Java 25+
- Maven 3.9+

BEAST 3 artifacts resolve from [Maven Central](https://central.sonatype.com/namespace/io.github.compevol),
so no extra configuration is needed.

To develop against an unreleased SNAPSHOT of BEAST 3, either add the GitHub Packages repository to
`pom.xml` (requires a [personal access token](https://github.com/settings/tokens) with
`read:packages` in `~/.m2/settings.xml`), or install BEAST 3 from source:

```bash
cd /path/to/beast3
mvn install -DskipTests
```

## Build and test

```bash
mvn compile
mvn test      # 35 tests
mvn package   # -> target/MyPackage.v1.0.0.zip
```

## Run the example analysis

`src/test/resources/my.beast.example/examples/cyclemodel.xml` applies `CycleModel` to the same
6-taxon primate mitochondrial alignment as BEAST's own `testHKY.xml`, so the two are directly
comparable:

```bash
mvn exec:exec -Dbeast.args="src/test/resources/my.beast.example/examples/cyclemodel.xml"
```

To launch BEAUti with the package on the module path:

```bash
mvn exec:exec -Dbeast.module=beast.fx -Dbeast.main=beastfx.app.beauti.Beauti
```

## Using it in an XML

`CycleModel` is not in a default namespace, so give the fully qualified `spec`:

```xml
<input spec="my.beast.example.CycleModel" id="cycleModel">
    <rates idref="cycle.rates"/>
</input>

<parameter id="cycle.rates"
           spec="beast.base.spec.inference.parameter.RealVectorParam"
           value="1.0 1.0 1.0 1.0" domain="PositiveReal" dimension="4"/>
```

**Do not add a `<frequencies>` block.** If you are adapting an HKY or GTR analysis, delete it
rather than editing it — `CycleModel` derives frequencies from the rates and throws if given any.

**Do not use a joint `ScaleOperator` on `rates`.** The rate matrix is normalised, so scaling all
four rates together leaves it exactly unchanged — a likelihood-null move. Use
`DeltaExchangeOperator` instead. The [documentation site](https://niconeureiter.github.io/fancy-beast2-package/#choosing-an-operator-for-rates)
explains why in full.

## Layout

```
src/main/java/module-info.java              JPMS module my.beast.example; every model class must
                                            be listed under `provides ... BEASTInterface`
src/main/java/my/beast/example/             CycleModel, plus MyDistribution and MyScaleOperator
                                            (template examples, kept as reference)
src/test/java/my/beast/example/             JUnit 5 tests
src/test/resources/my.beast.example/examples/   runnable BEAST XML
src/assembly/beast-package.xml              what goes into the package ZIP
docs/                                       the GitHub Pages site
version.xml                                 package name/version + BEASTInterface providers
```

A new class is invisible to BEAST unless it is registered in **both** `module-info.java`
(`provides`) and `version.xml` (`<provider>`).

## Key concepts (new spec API)

| Old (deprecated)                           | New (spec)                                              |
|--------------------------------------------|---------------------------------------------------------|
| `RealParameter`                            | `RealScalarParam<D>` / `RealVectorParam<D>`             |
| `ParametricDistribution`                   | `ScalarDistribution<S, T>`                              |
| `Prior` wrapper + `ParametricDistribution` | Distribution with `param` input (acts as its own prior) |
| `lower`/`upper` bounds                     | Domain types: `Real`, `PositiveReal`, `NonNegativeReal`, `UnitInterval` |

Bounds live in the domain type — `RealVector<PositiveReal>` — not in `lower`/`upper` inputs.

## Releasing

`release.sh` automates build and packaging:

```bash
./release.sh              # build MyPackage.v1.0.0.zip
./release.sh --release    # additionally create a GitHub release with the ZIP attached
```

**Linux/Ubuntu:** if the script fails on `\r` characters, your checkout converted line endings to
CRLF. Run it as `bash release.sh`, or strip them:

```bash
tr -d '\r' < release.sh > release_fixed.sh && mv release_fixed.sh release.sh && chmod +x release.sh
```

### Local install

```bash
PKG_DIR=~/.beast/2.8/MyPackage        # macOS and Linux
# Windows: %USERPROFILE%\.beast\2.8\MyPackage

mkdir -p "$PKG_DIR"
unzip -o MyPackage.v1.0.0.zip -d "$PKG_DIR"
packagemanager -list                  # verify
```

The ZIP must be **flat** — `version.xml`, `lib/`, and optionally `examples/` and `fxtemplates/` at
the top level, with no wrapper directory named after the package. The Package Manager extracts
into its own directory, so a wrapper would double-nest and break service discovery.

### Submit to CBAN

[CBAN](https://github.com/CompEvol/CBAN) is where BEAST's Package Manager discovers packages. Fork
it, add an entry to `packages2.8.xml` (`./release.sh --release` prints this for you), and open a
pull request:

```xml
<package name="MyPackage" version="1.0.0"
    url="https://github.com/YOU/YOUR-REPO/releases/download/v1.0.0/MyPackage.v1.0.0.zip"
    projectURL="https://github.com/YOU/YOUR-REPO"
    description="Cyclic non-reversible nucleotide substitution model">
    <depends on="BEAST.base" atleast="2.8.0"/>
</package>
```

### Maven Central

BEAST 3 can also install packages straight from Maven Central. The recommended path is to publish
via GitHub Actions on a `v*` tag; see
[`package-release-setup.md`](https://github.com/CompEvol/beast3/blob/master/scripts/package-release-setup.md)
for the full setup. To deploy manually, with a `central` server entry in `~/.m2/settings.xml` and
GPG configured:

```bash
mvn clean deploy -Prelease
```

Users then install with `packagemanager -maven groupId:artifactId:version`.

## Further reading

- [CycleModel documentation](https://niconeureiter.github.io/fancy-beast2-package/)
- [BEAST 3 source](https://github.com/CompEvol/beast3)
- [BEAST 2 → 3 migration guide](https://github.com/CompEvol/beast3/blob/master/scripts/migration-guide.md)
