# Why KSP

## The problem KSP solves

Compiler plugins are powerful metaprogramming tools that can greatly enhance
how you write code. Compiler plugins call compilers directly as libraries to
analyze and edit input programs. These plugins can also generate output for
various uses. For example, they can generate boilerplate code, and they can
even generate full implementations for specially-marked program elements,
such as `Parcelable`. Plugins have a variety of other uses and can even be used
to implement and fine-tune features that are not provided directly in a
language.

While compiler plugins are powerful, this power comes at a price. To write
even the simplest plugin, you need to have some compiler background
knowledge, as well as a certain level of familiarity with the
implementation details of your specific compiler. Another practical
issue is that plugins are often closely tied to specific compiler
versions, meaning you might need to update your plugin each time you
want to support a newer version of the compiler.

## KSP makes creating lightweight compiler plugins easier

KSP is designed to hide compiler changes, minimizing maintenance
efforts for processors that use it. KSP is designed not to be tied to the
JVM so that it can be adapted to other platforms more easily in the future.
KSP is also designed to minimize build times. For some processors, such as
[Room](https://developer.android.com/training/data-storage/room), KSP is up to 2x faster than KAPT.

KSP is itself implemented as a compiler plugin. There are prebuilt packages
on Google's Maven repository that you can download and use without having
to build the project yourself.

## Comparison to `kotlinc` compiler plugins

`kotlinc` compiler plugins have access to almost everything from the compiler
and therefore have maximum power and flexibility. On the other hand, because
these plugins can potentially depend on anything in the compiler, they are
sensitive to compiler changes and need to be maintained frequently. These plugins
also require a deep understanding of `kotlinc`’s implementation, so the learning
curve can be steep.

KSP aims to hide most compiler changes through a well-defined API, though major
changes in compiler or even the Kotlin language might still require to be
exposed to API users.

KSP tries to fulfill common use cases by providing an API that trades power for
simplicity. Its capability is a strict subset of a general `kotlinc` plugin.
For example, while `kotlinc` can examine expressions and statements and can even
modify code, KSP cannot.

While writing a `kotlinc` plugin can be a lot of fun, it can also take a lot of
time. If you aren't in a position to learn `kotlinc`’s implementation and do
not need to modify source code or read expressions, KSP might be a good fit.

## Comparison to reflection

KSP's API looks similar to `kotlin.reflect`. The major difference between
them is that type references in KSP need to be resolved explicitly. This is
one of the reasons why the interfaces are not shared.

## Comparison to KAPT

[KAPT](https://kotlinlang.org/docs/reference/kapt.html) is a remarkable
solution which makes a large amount of Java annotation processors work
for Kotlin programs out-of-box. The major advantages of KSP over KAPT are
improved build performance, not tied to JVM, a more idiomatic Kotlin
API, and the ability to understand Kotlin-only symbols.

To run Java annotation processors unmodified, KAPT compiles Kotlin code
into Java stubs that retain information that Java annotation processors
care about. To create these stubs, KAPT needs to resolve all symbols in
the Kotlin program. The stub generation costs roughly 1/3 of a full
`kotlinc` analysis and the same order of `kotlinc` code-generation. For
many annotation processors, this is much longer than the time spent in
the processors themselves. For example, Glide looks at a very limited
number of classes with a predefined annotation, and its code generation
is fairly quick. Almost all of the build overhead resides in the stub
generation phase. Switching to KSP would immediately reduce the time
spent in the compiler by 25%.

For performance evaluation, we implemented a
[simplified version](https://github.com/google/ksp/releases/download/1.4.10-dev-experimental-20200924/miniGlide.zip)
of [Glide](https://github.com/bumptech/glide) in KSP to make it generate code
for the [Tachiyomi](https://github.com/inorichi/tachiyomi) project. While
the total Kotlin compilation time of the project is 21.55 seconds on our
test device, it took 8.67 seconds for KAPT to generate the code, and it
took 1.15 seconds for our KSP implementation to generate the code.

Unlike KAPT, processors in KSP do not see input programs from Java's point
of view. The API is more natural to Kotlin, especially for Kotlin-specific
features such as top-level functions. Because KSP doesn't delegate to
`javac` like KAPT, it doesn't assume JVM-specific behaviors and can be
used with other platforms potentially.

## Limitations

While KSP tries to be a simple solution for most common use cases, it has made
several trade-offs compared to other plugin solutions. The following are not
goals of KSP:

* Examining expression-level information of source code.
* Modifying source code.
* 100% compatibility with the Java Annotation Processing API.

We are also exploring several additional features. Note that these features are
currently unavailable:

* IDE integration: Currently IDEs know nothing about the generated code.
