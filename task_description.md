# Develop a BEAST 3 package

I want to write a BEAST 3 package providing a fancy new substitution model.

## Model Description

We are creating a new substitution model called `FancyModel`. It is a Nucleotide substitution model. It should have an input called `baseRate` which is a `RealScalar<PositiveReal>`.

The rates are as follows:

- We assume uniform base frequencies.
- We are assuming a secret nucleotide X which is never sampled or observed.
- However, to go from any conventional nucleotide to another (e.g. from A to T), one has to go through X.
- This means that it impossible to go from A to T directly, but one has to do A to X to T.
- The rate to go from a nucleotide to another is equal to the baseRate times the number of letters in the alphabet between the two nucleotides.
- For instance, the rate to go from T to X (or vice versa) is baseRate x 3 , as there are three letters (UVW) between T and X.

## Implementation Instructions

1. This is a skeleton repo for a fresh package. Check out the @README.md and the existing code in @src .
2. Check out the BEAST 3 repo in @../beast3 . It contains the BEAST 3 source code. Check out @../beast3/beast-base/src/main/java/beast/base/spec/evolution/substitutionmodel for the relevant classes for substitution models and existing example models. 
3. Work out the actual substitution probabilities between the four conventional nucleotides.
4. Create a new substitution model class called `FancyModel`. It is a Nucleotide substitution model. It should have an input called `baseRate` which is a `RealScalar<PositiveReal>`.
4. Don't create any tests yet.

5m plan + 3min execution


# Tests

We now test the model.

- Create some focused unit tests for some small toy examples, testing the public interface against expected values and run them.
- Create a BEAST 3 XML which we can use to actually use the new substitution model. Look at @../beast3/beast-base/src/test/resources/beast.base/examples/testHKY.xml for an example with the HKY model. DOn't run anything, but create the XML.
  
5min

# PR

Create a PR for the changes compared to main. Create a description outlining the model and the code changes.

2min

# Docs

/goal Set up GitHub pages and publish a one-pager with a model description and instructions on how to use the model.

3min

Update the @README.md . Make it less generic and add a link to the website.

2min