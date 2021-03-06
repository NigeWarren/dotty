---
layout: doc-page
title: "Import Implicit"
---

A special form of import is used to import implicit values. Example:
```scala
object A {
  class TC
  implicit tc for TC
  def f given TC = ???
}
object B {
  import A._
  import implicit A._
}
```
In the code above, the `import A._` clause of object `B` will import all members
of `A` _except_ the implicit `tc`. Conversely, the second import `import implicit A._` will import _only_ that implicit.

Generally, a normal import clause brings all members except implicit values into scope whereas an `import implicit` clause brings only implicit values into scope.

There are two main benefits arising from these rules:

 - It is made clearer where implicit values in scope are coming from. In particular, it is not possible to hide imported implicit values in a long list of regular imports.
 - It enables importing all implicit values
   without importing anything else. This is particularly important since implicit
   values can be anonymous, so the usual recourse of using named imports is not
   practical.

### Relationship with Old-Style Implicits

The rules of "import implicit" above have the consequence that a library
would have to migrate in lockstep with all its users from old style implicit definitions and
normal imports to new style implicit definitions and `import implicit`.

The following modifications avoid this hurdle to migration.

 1. An `import implicit` also brings old style implicits into scope. So, in Scala 3.0
    an old-style implicit definition can be brought into scope either by a normal import
    or by an `import implicit`.

 2. In Scala 3.1, an old-style implicits accessed implicitly through a normal import
    will give a deprecation warning.

 3. In some version after 3.1, an old-style implicits accessed implicitly through a normal import
    will give a compiler error.

These rules mean that library users can use `import implicit` to access old-style implicits in Scala 3.0,
and will be gently nudged and then forced to do so in later versions. Libraries can then switch to
new-style implicit definitions once their user base has migrated.
