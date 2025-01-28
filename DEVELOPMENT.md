# Development HowTos
This doc describes how KSP is implemented and how to test and debug it, as well as processors. Unless otherwise noted, it assumes running in Gradle.

## KSP Processes / JVM Instances
The Kotlin compiler has two main parts: Kotlin Gradle Plugin (KGP) and [the compiler itself](https://central.sonatype.com/artifact/org.jetbrains.kotlin/kotlin-compiler-embeddable).

KSP 1.x is also comprised of 2 parts similarly:
* [KSP Gradle plugin](gradle-plugin/src/main/kotlin/com/google/devtools/ksp/gradle/KspSubplugin.kt) (KSPGP), which works with Kotlin Gradle Plugin, KGP, to get compilation information and create KSP tasks (e.g., `kspKotlin`, `kspTestKotlin`, `kspDebugKotlin`, etc.). These tasks invoke Kotlin compiler with the following KSP compiler plugin specified in the compiler plugin classpath.
* [KSP Compiler plugin](compiler-plugin/src/main/kotlin/com/google/devtools/ksp/KotlinSymbolProcessingPlugin.kt) (KSPCP) is a plugin to the Kotlin compiler. When the compiler runs, it loads KSP compiler plugin which in turn runs processors.

By default, KGP and KSPGP are invoked by Gradle and run in Gradle daemon. They create `compileKotlin` and `kspKotlin` tasks that send requests to `KotlinCompileDaemon` which runs in a separate JVM instance in another process.

## Debug A Processor and/or KSP
Because **processors, KSP and Kotlin Compiler run in `KotlinCompileDaemon`, not Gradle daemon**, the *debug* button in IDE, which usually debugs the Gradle process, doesn't work. The process to debug is `KotlinCompileDaemon`:
1. Make sure `KotlinCompileDaemon` is attachable / debuggable by specifying deubg options in `kotlin.daemon.jvm.options`. For example, when invoking Gradle build from command line:
```
$ ./gradlew :app:kspDebugKotlin --rerun-tasks -Dkotlin.daemon.jvm.options="-Xdebug,-Xrunjdwp:transport=dt_socket\,address=8765\,server=y\,suspend=n"
```
2. Find the `KotlinCompileDaemon`. There can be multiple instances in the system, especially when IDE is being used or there are other Gradle Kotlin projects that are being built. Look for the PID using this command:
```
$ ps ax | grep 8765 | grep KotlinCompileDaemon
```
3. Then, attach the debugger to the process with the PID we just found.
4. Because of the `suspend=n` in the above example, the compilation will just keep running without waiting for the debugger. On the other hand, because the daemon will be alive for a while (3 hours by default), the process is now attached and we can set break points.
5. Run the above gradle build command again. The daemon should now stop at some break point, if they are hit.

If you can't find or attach to the `KotlinCompileDaemon`, try killing it first. Stopping Gradle daemon sometimes helps, too. For example
```
$ ./gradlew --stop; pkill -f KotlinCompileDaemon
```

## Profiling
Same to debugging, the target process is `KotlinCompileDaemon`, rather than the Gradle daemon.

## Kotlin Compile Testing
When developing a processor, [Kotlin Compile Testing](https://github.com/tschuchortdev/kotlin-compile-testing) can be very useful. It calls into the compiler directly from test code or main function instead of through Gradle and `KotlinCompileDaemon`, so IDE's debugger usually works out of box.

Another major advantage is testing. It's much simpler and faster to write, run and debug tests than the full-fledged [Gradle TestKit]([url](https://docs.gradle.org/current/userguide/test_kit.html)).

See their [documentation](https://github.com/tschuchortdev/kotlin-compile-testing#kotlin-symbol-processing-api-support) for more details.

## Other Scenarios
### Running From Command Line
If you're running the compiler from command line, there will be no `KotlinCompileDaemon`. The debug options should be passed directly to the `java` command, instead of through `-Dkotlin.daemon.jvm.options`.

### Running Kotlin Compiler in Gradle Daemon
Instead of the dedicated `KotlinCompileDaemon`, the Kotlin compiler can also run in Gradle daemon directly with the Gradle property `kotlin.compiler.execution.strategy=in-process`. You should be able to debug the Gradle build directly from the IDE.

Note that running compiler in this way suffers from several performance and correctness issues. It should only be used when necessary, at your own discretion.

## If You Have Any Questions
For debugging, please check [this](https://github.com/google/ksp/issues/31) issue first and comment if the question isn't already known.
