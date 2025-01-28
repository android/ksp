/*
 * Copyright 2020 Google LLC
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.devtools.ksp.processing

import com.google.devtools.ksp.symbol.*
import java.io.File
import java.io.OutputStream

/**
 * [CodeGenerator] creates and manages files.
 *
 * Files created by [CodeGenerator] are considered in incremental processing.
 * Kotlin and Java files will be compiled together with other source files in the module.
 * Files created without using this API will not participate in incremental processing nor subsequent compilations.
 */
interface CodeGenerator {
    /**
     * Creates a file which is managed by [CodeGenerator]
     *
     * Sources of corresponding [KSNode]s which are obtained directly from [Resolver] need to be specified.
     * Namely, the containing files of those [KSNode]s who are obtained from:
     *   * [Resolver.getAllFiles]
     *   * [Resolver.getSymbolsWithAnnotation]
     *   * [Resolver.getClassDeclarationByName]
     *
     * Instead of requiring processors to specify all source files which are relevant in generating the given output,
     * KSP traces dependencies automatically and only needs to know those sources that only processors know what they
     * are for. If a [KSFile] is indirectly obtained through other [KSNode]s, it hasn't to be specified for the given
     * output, even if its contents contribute to the generation of the output.
     *
     * For example, a processor generates an output `O` after reading class `A` in `A.kt` and class `B` in `B.kt`,
     * where `A` extends `B`. The processor got `A` by [Resolver.getSymbolsWithAnnotation] and then got `B` by
     * [KSClassDeclaration.superTypes] from `A`. Because the inclusion of `B` is due to `A`, `B.kt` needn't to be
     * specified in [dependencies] for `O`. Note that specifying `B.kt` in this case doesn't hurt, it is only unnecessary.
     *
     * @param dependencies are [KSFile]s from which this output is built. Only those that are obtained directly
     *                     from [Resolver] are required.
     * @param packageName corresponds to the relative path of the generated file; using either '.'or '/' as separator.
     * @param fileName file name
     * @param extensionName If "kt" or "java", this file will participate in subsequent compilation.
     *                      Otherwise its creation is only considered in incremental processing.
     * @return OutputStream for writing into files.
     * @see [CodeGenerator] for more details.
     */
    fun createNewFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        extensionName: String = "kt"
    ): OutputStream

    /**
     * Creates a file which is managed by [CodeGenerator]
     *
     * Sources of corresponding [KSNode]s which are obtained directly from [Resolver] need to be specified.
     * Namely, the containing files of those [KSNode]s who are obtained from:
     *   * [Resolver.getAllFiles]
     *   * [Resolver.getSymbolsWithAnnotation]
     *   * [Resolver.getClassDeclarationByName]
     *
     * Instead of requiring processors to specify all source files which are relevant in generating the given output,
     * KSP traces dependencies automatically and only needs to know those sources that only processors know what they
     * are for. If a [KSFile] is indirectly obtained through other [KSNode]s, it hasn't to be specified for the given
     * output, even if its contents contribute to the generation of the output.
     *
     * For example, a processor generates an output `O` after reading class `A` in `A.kt` and class `B` in `B.kt`,
     * where `A` extends `B`. The processor got `A` by [Resolver.getSymbolsWithAnnotation] and then got `B` by
     * [KSClassDeclaration.superTypes] from `A`. Because the inclusion of `B` is due to `A`, `B.kt` needn't to be
     * specified in [dependencies] for `O`. Note that specifying `B.kt` in this case doesn't hurt, it is only unnecessary.
     *
     * @param dependencies are [KSFile]s from which this output is built. Only those that are obtained directly
     *                     from [Resolver] are required.
     * @param path corresponds to the relative path of the generated file; includes the full file name
     * @param extensionName If "kt" or "java", this file will participate in subsequent compilation.
     *                      Otherwise its creation is only considered in incremental processing.
     * @return OutputStream for writing into files.
     * @see [CodeGenerator] for more details.
     */
    fun createNewFileByPath(
        dependencies: Dependencies,
        path: String,
        extensionName: String = "kt"
    ): OutputStream

    /**
     * Associate [sources] to an output file.
     *
     * @param sources are [KSFile]s from which this output is built. Only those that are obtained directly
     *                     from [Resolver] are required.
     * @param packageName corresponds to the relative path of the generated file; using either '.'or '/' as separator.
     * @param fileName file name
     * @param extensionName If "kt" or "java", this file will participate in subsequent compilation.
     *                      Otherwise its creation is only considered in incremental processing.
     * @see [CodeGenerator] for more details.
     */
    fun associate(sources: List<KSFile>, packageName: String, fileName: String, extensionName: String = "kt")

    /**
     * Associate [sources] to an output file.
     *
     * @param sources are [KSFile]s from which this output is built. Only those that are obtained directly
     *                     from [Resolver] are required.
     * @param path corresponds to the relative path of the generated file; includes the full file name
     * @param extensionName If "kt" or "java", this file will participate in subsequent compilation.
     *                      Otherwise its creation is only considered in incremental processing.
     * @see [CodeGenerator] for more details.
     */
    fun associateByPath(sources: List<KSFile>, path: String, extensionName: String = "kt")

    /**
     * Associate [classes] to an output file.
     *
     * @param classes are [KSClassDeclaration]s from which this output is built. Only those that are obtained directly
     *                     from [Resolver] are required.
     * @param packageName corresponds to the relative path of the generated file; using either '.'or '/' as separator.
     * @param fileName file name
     * @param extensionName If "kt" or "java", this file will participate in subsequent compilation.
     *                      Otherwise its creation is only considered in incremental processing.
     * @see [CodeGenerator] for more details.
     */
    fun associateWithClasses(
        classes: List<KSClassDeclaration>,
        packageName: String,
        fileName: String,
        extensionName: String = "kt"
    )

    val generatedFile: Collection<File>

    /**
     * Associate [functions] to an output file.
     *
     * @param functions are [KSFunctionDeclaration]s from which this output is built. Only those that are obtained
     *              directly from [Resolver] are required.
     * @param packageName corresponds to the relative path of the generated file; using either '.'or '/' as separator.
     * @param fileName file name
     * @param extensionName If "kt" or "java", this file will participate in subsequent compilation.
     *                      Otherwise its creation is only considered in incremental processing.
     * @see [CodeGenerator] for more details.
     */
    fun associateWithFunctions(
        functions: List<KSFunctionDeclaration>,
        packageName: String,
        fileName: String,
        extensionName: String = "kt"
    ) = Unit

    /**
     * Associate [properties] to an output file.
     *
     * @param properties are [KSPropertyDeclaration]s from which this output is built. Only those that are obtained
     *              directly from [Resolver] are required.
     * @param packageName corresponds to the relative path of the generated file; using either '.'or '/' as separator.
     * @param fileName file name
     * @param extensionName If "kt" or "java", this file will participate in subsequent compilation.
     *                      Otherwise its creation is only considered in incremental processing.
     * @see [CodeGenerator] for more details.
     */
    fun associateWithProperties(
        properties: List<KSPropertyDeclaration>,
        packageName: String,
        fileName: String,
        extensionName: String = "kt"
    ) = Unit
}

/**
 * Dependencies of an output file.
 */
class Dependencies private constructor(
    val isAllSources: Boolean,
    val aggregating: Boolean,
    val originatingFiles: List<KSFile>
) {

    /**
     * Create a [Dependencies] to associate with an output.
     *
     * @param aggregating whether the output should be invalidated by a new source file or a change in any of the existing files.
     *                           Namely, whenever there is new information.
     * @param sources Sources for this output to depend on.
     */
    constructor(aggregating: Boolean, vararg sources: KSFile) : this(false, aggregating, sources.toList())

    companion object {
        /**
         * A short-hand to all source files.
         *
         * Associating an output to [ALL_SOURCES] essentially disables incremental processing, as the tiniest change will clobber all files.
         * This should not be used in processors which care about processing speed.
         */
        val ALL_FILES = Dependencies(true, true, emptyList())
    }
}
