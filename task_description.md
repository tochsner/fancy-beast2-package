# Develop a BEAST 3 package

I want to write a BEAST 3 package providing a fancy new substitution model.

The main agent acts as coordinator and delegates to the `researcher`, `coder` and `reviewer`
subagents in `@../.claude/agents` (see `@../CLAUDE.md`). Each section below is a separate
prompt — the coordinator stops and reports after each one.

## Model Description

We are creating a new substitution model called `CycleModel`. It is a nucleotide substitution
model. Number the four nucleotides 0-3, in the order A, C, G, T.

The rates are as follows:

- A substitution can only ever increment the state by one, wrapping around from T back to A:
  A -> C -> G -> T -> A.
- Nothing else is possible. A substitution can never skip ahead and never go backwards.
- So which substitution comes next is fully determined by the current state. Only the waiting
  time is random.
- Each state has its own rate. The model takes an input `rates`, a `RealVector<PositiveReal>`
  of length 4, where `rates[i]` is the rate of the substitution leaving state i.

## Planning Instructions

1. This is a skeleton repo for a fresh package. Check out the @README.md and the existing code in @src .
2. Send the `researcher` into @../beast3 , which holds the BEAST 3 source, to report back on how
   substitution models are written against the new spec API.
   @../beast3/beast-base/src/main/java/beast/base/spec/evolution/substitutionmodel has the
   existing models to imitate. In plan mode it can't write files, so have it report back inline.
3. Work out the rate matrix, the equilibrium frequencies and the transition probabilities
   yourself. Don't delegate the maths — the `reviewer` needs to derive its expected values
   independently of the `coder`.
4. Ask me about anything the model description leaves genuinely open.
5. Present a spec I can review: the public API of `CycleModel`, the rate matrix as actual numbers,
   worked examples with expected transition probabilities, and the invariants that must hold.
   That spec is the plan.

5min


## Implementation

Go ahead and implement it. Dispatch `coder` and `reviewer` in a single message so they run in
parallel: `coder` implements `CycleModel` from the frozen spec (and other classes it might need),
`reviewer` writes behaviour tests from the same spec.

Bring me anything either of them flags as `BLOCKED:`.

3min


# Tests

We now test the model.

- Tell `reviewer` to review the implementation, run the tests, and add
  implementation-specific ones. Report what it found before changing anything.
- Tell the `coder` to create a BEAST 3 XML which we can use to actually apply the new substitution model. Look at
  @../beast3/beast-base/src/test/resources/beast.base/examples/testHKY.xml for an example with the
  HKY model. Don't run anything yet, just create the XML.

5min


# Docs

/goal Set up GitHub pages and publish a one-pager with a model description and instructions on how to use the model.

Update the @README.md . Make it less generic and add a link to the website.

2min


# PR

Create a PR for the changes compared to main. Create a description outlining the model and the
code changes, including the design decisions you raised with me and how we resolved them.

Stage paths explicitly — don't commit `plan/` or the IDE files.

2min
