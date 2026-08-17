## What it is

`CycleModel` is a nucleotide substitution model in which a substitution can only ever
**increment** the state, wrapping around from T back to A:

```
  A -> C -> G -> T -> A
```

Nothing else is possible. A substitution can never go backwards and never skip ahead. Which
substitution happens next is therefore fully determined by the current state — only the *waiting
time* is random.

Each state has its own rate. The model takes a single input, `rates`, a length-4 vector where
`rates[i]` is the rate of the one substitution leaving state `i`, in the order **A, C, G, T**.

It is built against the BEAST 3 `beast.base.spec` API (BEAST 2.8 / 3).

---

## The model

### Rate matrix

With `r = (r0, r1, r2, r3)`, all strictly positive:

```
            to
          A     C     G     T
      A [ -r0    r0    0     0  ]
 from C [  0    -r1    r1    0  ]
      G [  0     0    -r2    r2 ]
      T [  r3    0     0    -r3 ]
```

One non-zero off-diagonal per row. Rows sum to zero.

### Equilibrium frequencies

You do **not** supply frequencies — they are determined by the rates. Reading `πQ = 0` column by
column gives `π_{j-1} r_{j-1} = π_j r_j`, so the probability flux `π_i r_i` is the same constant
all the way around the ring. Therefore

```
  π_i  ∝  1 / r_i           π_i = (1/r_i) / Σ_k (1/r_k)
```

States you leave quickly are states you are rarely found in. The chain is a single cycle, so it
is irreducible and π is unique and strictly positive.

*Example:* `rates = (1, 6, 1, 6)` gives `π = (3/7, 1/14, 3/7, 1/14)` — C and T are left six times
faster than A and G, so they are occupied one sixth as often.

### Normalisation

Q is scaled so that one time unit means one expected substitution, matching HKY, GTR and
Jukes-Cantor:

```
  μ = Σ_i π_i r_i = 4 / Σ_k (1/r_k)          Q_normalised = Q / μ
```

Two things follow, and both matter in practice:

- After normalisation, `π_i · r_i = 1/4` for **every** state.
- The model is **scale-invariant** in `rates`: `rates` and `α·rates` give an identical rate matrix
  and an identical likelihood. Only the *relative* rates are identifiable — the overall magnitude
  is absorbed by the clock rate. See [choosing operators](#choosing-an-operator-for-rates), which
  is where this bites.

### It is non-reversible

Detailed balance fails outright: `π_i q_{i,i+1} > 0` while the reverse rate `q_{i+1,i}` is exactly
zero. The characteristic equation is

```
  Π_i (λ + r_i) = Π_i r_i
```

and its non-zero roots are **complex in general**. Even the uniform case `rates = (1,1,1,1)` gives
eigenvalues `0, −1+i, −1−i, −2`. The implementation therefore extends
`BasicComplexSubstitutionModel`, which uses a complex eigensystem; a real-only eigensolver would
silently return wrong numbers.

Practical consequence: this model has a **direction in time**. Unlike a reversible model, the
likelihood depends on where the root is, so root placement is informed by the substitution process
itself.

---

## Installing

Build the package ZIP from a checkout:

```bash
git clone https://github.com/tochsner/fancy-beast2-package.git
cd fancy-beast2-package
./release.sh
```

Then install it where BEAST looks for packages:

```bash
PKG_DIR=~/.beast/2.8/MyPackage        # macOS and Linux
# Windows: %USERPROFILE%\.beast\2.8\MyPackage

mkdir -p "$PKG_DIR"
unzip -o MyPackage.v1.0.0.zip -d "$PKG_DIR"
```

Verify it is visible:

```bash
packagemanager -list
```

To run the tests, or to build without packaging:

```bash
mvn -o test
mvn -o package
```

---

## Using it in a BEAST XML

The class is not in any default namespace, so give the fully qualified `spec`:

```xml
<input spec="my.beast.example.CycleModel" id="cycleModel">
    <rates idref="cycle.rates"/>
</input>

<parameter id="cycle.rates"
           spec="beast.base.spec.inference.parameter.RealVectorParam"
           value="1.0 1.0 1.0 1.0" domain="PositiveReal" dimension="4"/>
```

Then wire it into a site model exactly as you would HKY or GTR:

```xml
<input spec="beast.base.spec.evolution.sitemodel.SiteModel" id="siteModel"
       gammaCategoryCount="1">
    <substModel idref="cycleModel"/>
</input>
```

A complete, runnable analysis ships with the package at
`src/test/resources/my.beast.example/examples/cyclemodel.xml`. It uses the same 6-taxon primate
mitochondrial alignment as BEAST's own `testHKY.xml`, so the two are directly comparable.

### Inputs

| Input | Type | Required | Meaning |
|---|---|---|---|
| `rates` | `RealVector<PositiveReal>`, length 4 | yes | `rates[i]` is the rate of the substitution leaving state `i`, in A, C, G, T order |

### Do not supply `frequencies`

Every other BEAST substitution model takes a `frequencies` input. **This one rejects it.**
Equilibrium frequencies are derived from `rates`, so any frequencies you supplied would be
mathematically inconsistent with the process. Supplying one is an error and `initAndValidate()`
throws:

```
CycleModel does not accept a frequencies input:
equilibrium frequencies are derived from the rates.
```

If you are adapting an existing HKY or GTR XML, **delete** the `<frequencies>` block rather than
trying to adapt it.

Bounds are carried by the domain type `PositiveReal`, not by `lower`/`upper` attributes — the spec
API replaced `RealParameter` and friends.

### Choosing an operator for `rates`

This is the one place the model's mathematics constrains your XML, and getting it wrong produces a
chain that looks like it is working but is not.

**Do not put a plain joint `ScaleOperator` on the `rates` vector.** Because Q is normalised,
multiplying all four rates by the same factor leaves the rate matrix *exactly* unchanged. It is a
likelihood-null move: it will be accepted essentially always, contribute nothing, and let the
chain wander along a direction the data cannot see.

Use an operator that changes the **relative** rates. `DeltaExchangeOperator` moves mass between
two randomly chosen elements:

```xml
<operator id="ratesDeltaExchange"
          spec="beast.base.spec.inference.operator.DeltaExchangeOperator"
          rvparameter="@cycle.rates" delta="0.2" weight="3"/>
```

Note that delta exchange conserves the vector's sum, so `rates` stays on a fixed hyperplane for
the whole run (with the initial values above, summing to 4). That is not a limitation here — it
confines sampling to exactly the directions that are identifiable — but it does mean your prior
shapes the rates *within* that slice rather than pinning their overall magnitude. If you want to
sample the scale too, you will need to fix the clock rate instead, since the two are not
separately identifiable.

### Interpreting the output

Log `rates` and read them as *relative* exit rates. Because `π_i ∝ 1/r_i`, a high `rates[i]`
means state `i` is transient and therefore rare at equilibrium; a low one means it is sticky and
common. Only ratios between the four entries are meaningful — their overall size is not.

---

## Worked examples

Useful for checking an implementation or building intuition. `P(t)` below is the transition
probability matrix at branch length `t` under the normalised Q.

**`rates = (1, 1, 1, 1)`** — Q is circulant, giving a closed form. With `d = (j − i) mod 4`:

```
  P(t)[i][j] = ¼ [ 1 + e^{-2t} + 2 e^{-t} cos t ]     d = 0
             = ¼ [ 1 − e^{-2t} + 2 e^{-t} sin t ]     d = 1
             = ¼ [ 1 + e^{-2t} − 2 e^{-t} cos t ]     d = 2
             = ¼ [ 1 − e^{-2t} − 2 e^{-t} sin t ]     d = 3
```

At `t = 1`: `0.383216875982, 0.370946117017, 0.184450765636, 0.061386241364`.

Note that even here the eigenvalues are complex (`0, −1±i, −2`), and that `P(t)` is *not*
symmetric — the `d = 1` and `d = 3` entries differ, which is the model's directionality showing
up directly.

**`rates = (1, 6, 1, 6)`** — the period-2 symmetry splits Q into two 2×2 blocks, so everything is
exactly rational. Eigenvalues `0, −7/4, −7/3, −49/12`; with `a = e^{−7t/4}`, `b = e^{−7t/3}`,
`c = e^{−49t/12}`:

```
  P[0][0] = 3/7  + (3/2)a −      b + (1/14)c
  P[0][1] = 1/14 + (1/2)a − (1/2)b − (1/14)c
  P[0][2] = 3/7  − (3/2)a +      b + (1/14)c
  P[0][3] = 1/14 − (1/2)a + (1/2)b − (1/14)c
```

**`rates = (1, 2, 3, 4)`** — genuinely complex and asymmetric. `π = (0.48, 0.24, 0.16, 0.12)`
exactly, and `(λ+1)(λ+2)(λ+3)(λ+4) = 24` factorises as `λ(λ+5)(λ² + 5λ + 10) = 0`.

---

## Limits worth knowing

- **Nucleotide data only.** `canHandleDataType` accepts `Nucleotide`; a `rates` vector of any
  length other than 4 is rejected at initialisation.
- **The state order is fixed** to BEAST's nucleotide order, A C G T. The cycle is a property of
  the model, not something you can permute.
- **Very large rate ratios are untested.** Structural invariants hold to 1e-9 at a ratio of 1e6.
  Behaviour beyond that has not been verified.
- **No independent implementation exists** to validate against. The test suite derives its
  expected values from the mathematics rather than from another program.

---

## Links

- [Source repository](https://github.com/tochsner/fancy-beast2-package)
- [BEAST 3](https://github.com/CompEvol/beast3)
- [BEAST 2 → 3 migration guide](https://github.com/CompEvol/beast3/blob/master/scripts/migration-guide.md)
