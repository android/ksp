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

package com.google.devtools.ksp

import com.google.devtools.ksp.common.AnyChanges
import com.google.devtools.ksp.common.copyWithTimestamp
import com.google.devtools.ksp.common.findLocationString
import com.google.devtools.ksp.common.impl.CodeGeneratorImpl
import com.google.devtools.ksp.common.impl.JsPlatformInfoImpl
import com.google.devtools.ksp.common.impl.JvmPlatformInfoImpl
import com.google.devtools.ksp.common.impl.KSPCompilationError
import com.google.devtools.ksp.common.impl.NativePlatformInfoImpl
import com.google.devtools.ksp.common.impl.UnknownPlatformInfoImpl
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.PlatformInfo
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.processing.impl.KSObjectCacheManager
import com.google.devtools.ksp.processing.impl.MessageCollectorBasedKSPLogger
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclarationContainer
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.Visibility
import com.google.devtools.ksp.symbol.impl.java.KSFileJavaImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSFileImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.file.impl.JavaFileManager
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCliJavaFileManagerImpl
import org.jetbrains.kotlin.config.JvmAnalysisFlags
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.load.java.components.FilesByFacadeFqNameIndexer
import org.jetbrains.kotlin.platform.JsPlatform
import org.jetbrains.kotlin.platform.jvm.JdkPlatform
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.util.ServiceLoaderLite
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLClassLoader
import java.nio.file.Files

class KotlinSymbolProcessingExtension(
    options: KspOptions,
    logger: KSPLogger,
    val testProcessor: SymbolProcessorProvider? = null,
) : AbstractKotlinSymbolProcessingExtension(options, logger, testProcessor != null) {
    override fun loadProviders(rootDisposable: Disposable): List<SymbolProcessorProvider> {
        if (!initialized) {
            providers = if (testProcessor != null) {
                listOf(testProcessor)
            } else {
                val processingClasspath = options.processingClasspath
                val classLoader =
                    URLClassLoader(processingClasspath.map { it.toURI().toURL() }.toTypedArray(), javaClass.classLoader)

                Disposer.register(rootDisposable) {
                    classLoader.close()
                }

                ServiceLoaderLite.loadImplementations(SymbolProcessorProvider::class.java, classLoader).filter {
                    (options.processors.isEmpty() && it.javaClass.name !in options.excludedProcessors) ||
                        it.javaClass.name in options.processors
                }
            }
            if (providers.isEmpty()) {
                logger.error("No providers found in processor classpath.")
            } else {
                logger.info(
                    "loaded provider(s): " +
                        "${providers.joinToString(separator = ", ", prefix = "[", postfix = "]") { it.javaClass.name }}"
                )
            }
        }
        return providers
    }
}

abstract class AbstractKotlinSymbolProcessingExtension(
    val options: KspOptions,
    val logger: KSPLogger,
    val testMode: Boolean,
) :
    AnalysisHandlerExtension {
    var initialized = false
    var finished = false
    val deferredSymbols = mutableMapOf<SymbolProcessor, List<KSAnnotated>>()
    lateinit var providers: List<SymbolProcessorProvider>
    lateinit var processors: List<SymbolProcessor>
    lateinit var incrementalContext: IncrementalContext
    lateinit var dirtyFiles: Set<KSFile>
    lateinit var cleanFilenames: Set<String>
    lateinit var codeGenerator: CodeGeneratorImpl
    var newFileNames: Collection<String> = emptySet()
    var rounds = 0

    companion object {
        private const val KSP_PACKAGE_NAME = "com.google.devtools.ksp"
        private const val KOTLIN_PACKAGE_NAME = "org.jetbrains.kotlin"
        private const val MULTI_ROUND_THRESHOLD = 100
    }

    override fun doAnalysis(
        project: Project,
        module: ModuleDescriptor,
        projectContext: ProjectContext,
        files: Collection<KtFile>,
        bindingTrace: BindingTrace,
        componentProvider: ComponentProvider,
    ): AnalysisResult? {
        // with `withCompilation == true`:
        // * KSP returns AnalysisResult.RetryWithAdditionalRoots in last round of processing, to notify compiler the generated sources.
        // * This function will be called again, and returning null tells compiler to fall through with normal compilation.
        if (finished) {
            if (!options.withCompilation)
                throw IllegalStateException("KSP is re-entered unexpectedly.")
            if (!options.returnOkOnError && logger.hasError()) {
                return AnalysisResult.compilationError(BindingContext.EMPTY)
            }
            // DO NOT updateFromShadow(); withCompilation requires the java output shadows to continue.
            return null
        }

        rounds++
        if (rounds > MULTI_ROUND_THRESHOLD) {
            logger.warn("Current processing rounds exceeds 100, check processors for potential infinite rounds")
        }
        logger.logging("round $rounds of processing")
        val psiManager = PsiManager.getInstance(project)
        if (initialized) {
            psiManager.dropPsiCaches()
            psiManager.dropResolveCaches()
            invalidateKotlinCliJavaFileManagerCache(project)
        } else {
            // In case of broken builds.
            if (javaShadowBase.exists()) {
                javaShadowBase.deleteRecursively()
            }
        }

        val javaShadowRoots = mutableListOf<File>()
        if (javaShadowBase.exists() && javaShadowBase.isDirectory) {
            javaShadowBase.listFiles()?.forEach {
                javaShadowRoots.add(it)
            }
        }
        val localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)
        // FIXME: reuse from previous rounds.
        val javaSourceRoots = options.javaSourceRoots + javaShadowRoots
        val javaFiles = javaSourceRoots
            .sortedBy { Files.isSymbolicLink(it.toPath()) } // Get non-symbolic paths first
            .flatMap { root -> root.walk().filter { it.isFile && it.extension == "java" }.toList() }
            .sortedBy { Files.isSymbolicLink(it.toPath()) } // This time is for .java files
            .distinctBy { it.canonicalPath }
            .mapNotNull { localFileSystem.findFileByPath(it.path)?.let { psiManager.findFile(it) } as? PsiJavaFile }

        val anyChangesWildcard = AnyChanges(options.projectBaseDir)
        val commonSources: Set<String?> = options.commonSources.map { it.canonicalPath }.toSet()
        val ksFiles = files.filterNot { it.virtualFile.canonicalPath in commonSources }
            .map { KSFileImpl.getCached(it) } + javaFiles.map { KSFileJavaImpl.getCached(it) }
        lateinit var newFiles: List<KSFile>

        handleException(module, project) {
            val fileClassProcessor = FilesByFacadeFqNameIndexer(bindingTrace)
            if (!initialized) {
                incrementalContext = IncrementalContext(
                    options, componentProvider,
                    File(anyChangesWildcard.filePath).relativeTo(options.projectBaseDir)
                )
                dirtyFiles = incrementalContext.calcDirtyFiles(ksFiles).toSet()
                cleanFilenames = ksFiles.filterNot { it in dirtyFiles }.map { it.filePath }.toSet()
                newFiles = dirtyFiles.toList()
                // DO NOT filter out common sources.
                files.forEach { fileClassProcessor.preprocessFile(it) }
            } else {
                newFiles = ksFiles.filter {
                    when (it) {
                        is KSFileImpl -> it.file
                        is KSFileJavaImpl -> it.psi
                        else -> null
                    }?.virtualFile?.let { virtualFile ->
                        if (System.getProperty("os.name").startsWith("windows", ignoreCase = true)) {
                            virtualFile.canonicalPath ?: virtualFile.path
                        } else {
                            File(virtualFile.path).canonicalPath
                        }
                    } in newFileNames
                }
                incrementalContext.registerGeneratedFiles(newFiles)
                newFiles.filterIsInstance<KSFileImpl>().forEach { fileClassProcessor.preprocessFile(it.file) }
            }
        }?.let { return@doAnalysis it }

        // dirtyFiles cannot be reused because they are created in the old container.
        val resolver = ResolverImpl(
            module,
            ksFiles.filterNot {
                it.filePath in cleanFilenames
            },
            newFiles, deferredSymbols, bindingTrace, project, componentProvider, incrementalContext, options
        )

        if (!initialized) {
            // Visit constants so that JavaPsiFacade knows them.
            // The annotation visitor in ResolverImpl covered newFiles already.
            // Skip private and local members, which are not visible to Java files.
            ksFiles.filterIsInstance<KSFileImpl>().filter { it !in dirtyFiles }.forEach {
                try {
                    it.accept(
                        object : KSVisitorVoid() {
                            private fun visitDeclarationContainer(container: KSDeclarationContainer) {
                                container.declarations.filterNot {
                                    it.getVisibility() == Visibility.PRIVATE
                                }.forEach {
                                    it.accept(this, Unit)
                                }
                            }

                            override fun visitFile(file: KSFile, data: Unit) =
                                visitDeclarationContainer(file)

                            override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) =
                                visitDeclarationContainer(classDeclaration)

                            override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
                                if (property.modifiers.contains(Modifier.CONST)) {
                                    property.getter // force resolution
                                }
                            }
                        },
                        Unit
                    )
                } catch (_: Exception) {
                    // Do nothing.
                }
            }
        }

        val providers = loadProviders(project)
        if (!initialized) {
            codeGenerator = CodeGeneratorImpl(
                options.classOutputDir,
                { javaShadowDir },
                options.kotlinOutputDir,
                options.resourceOutputDir,
                options.projectBaseDir,
                anyChangesWildcard,
                ksFiles,
                options.incremental
            )
            processors = providers.mapNotNull { provider ->
                var processor: SymbolProcessor? = null
                handleException(module, project) {
                    processor = provider.create(
                        SymbolProcessorEnvironment(
                            options.processingOptions,
                            options.languageVersion,
                            codeGenerator,
                            logger,
                            options.apiVersion,
                            options.compilerVersion,
                            findTargetInfos(options.languageVersionSettings, module),
                            KotlinVersion(1, 0),
                        )
                    )
                }?.let { analysisResult ->
                    resolver.tearDown()
                    return@doAnalysis analysisResult
                }
                if (logger.hasError()) {
                    return@mapNotNull null
                }
                processor?.also { deferredSymbols[it] = mutableListOf() }
            }
            /* Kotlin compiler expects a source dir to exist, but processors might not generate Kotlin source.
               Create it during initialization just in case. */
            options.kotlinOutputDir.mkdirs()
            initialized = true
        }
        if (!logger.hasError()) {
            processors.forEach processing@{ processor ->
                handleException(module, project) {
                    deferredSymbols[processor] =
                        processor.process(resolver).filter { it.origin == Origin.KOTLIN || it.origin == Origin.JAVA }
                }?.let {
                    resolver.tearDown()
                    incrementalContext.closeFiles()
                    return it
                }
                if (logger.hasError()) {
                    return@processing
                }
                if (!deferredSymbols.containsKey(processor) || deferredSymbols[processor]!!.isEmpty()) {
                    deferredSymbols.remove(processor)
                }
            }
        }
        // Post processing.
        newFileNames = codeGenerator.generatedFile.filter { it.extension == "kt" || it.extension == "java" }
            .map { it.canonicalPath.replace(File.separatorChar, '/') }.toSet()

        if (codeGenerator.generatedFile.isEmpty()) {
            finished = true
        }
        KSObjectCacheManager.clear()
        codeGenerator.closeFiles()
        if (logger.hasError()) {
            finished = true
            processors.forEach { processor ->
                handleException(module, project) {
                    processor.onError()
                }?.let {
                    resolver.tearDown()
                    incrementalContext.closeFiles()
                    return it
                }
            }
            incrementalContext.closeFiles()
        } else {
            if (finished) {
                processors.forEach { processor ->
                    handleException(module, project) {
                        processor.finish()
                    }?.let {
                        resolver.tearDown()
                        incrementalContext.closeFiles()
                        return it
                    }
                }
                if (deferredSymbols.isNotEmpty()) {
                    deferredSymbols.map { entry ->
                        logger.warn(
                            "Unable to process:${entry.key::class.qualifiedName}:   ${
                            entry.value.map { it.toString() }.joinToString(";")
                            }"
                        )
                    }
                }
                if (!logger.hasError()) {
                    incrementalContext.updateCachesAndOutputs(
                        dirtyFiles,
                        codeGenerator.outputs,
                        codeGenerator.sourceToOutputs
                    )
                } else {
                    incrementalContext.closeFiles()
                }
            }
        }
        if (finished) {
            logger.reportAll()
        }
        resolver.tearDown()
        if (finished && !options.withCompilation) {
            updateFromShadow()
            return if (!options.returnOkOnError && logger.hasError()) {
                AnalysisResult.compilationError(BindingContext.EMPTY)
            } else {
                AnalysisResult.success(BindingContext.EMPTY, module, shouldGenerateCode = false)
            }
        }
        return AnalysisResult.RetryWithAdditionalRoots(
            BindingContext.EMPTY,
            module,
            listOf(javaShadowDir),
            listOf(options.kotlinOutputDir),
            listOf(options.classOutputDir)
        )
    }

    abstract fun loadProviders(rootDisposable: Disposable): List<SymbolProcessorProvider>

    private var annotationProcessingComplete = false

    private fun setAnnotationProcessingComplete(): Boolean {
        if (annotationProcessingComplete) return true

        annotationProcessingComplete = true
        return false
    }

    private fun KSPLogger.reportAll() {
        (this as MessageCollectorBasedKSPLogger).reportAll()
    }

    private fun KSPLogger.hasError(): Boolean {
        return (this as MessageCollectorBasedKSPLogger).recordedEvents.any {
            it.severity == CompilerMessageSeverity.ERROR || it.severity == CompilerMessageSeverity.EXCEPTION
        }
    }

    private fun handleException(module: ModuleDescriptor, project: Project, call: () -> Unit): AnalysisResult? {
        try {
            call()
        } catch (e: Exception) {
            fun Exception.logToError() {
                val sw = StringWriter()
                printStackTrace(PrintWriter(sw))
                logger.error(sw.toString())
            }

            fun Exception.isNotRecoverable(): Boolean =
                stackTrace.first().className.let {
                    // TODO: convert non-critical exceptions thrown by KSP to recoverable errors.
                    it.startsWith(KSP_PACKAGE_NAME) || it.startsWith(KOTLIN_PACKAGE_NAME)
                }

            // Returning non-null here allows
            // 1. subsequent processing of other processors in current round.
            // 2. processor.onError() be called.
            //
            // In other words, returning non-null let current round finish.
            when {
                e is KSPCompilationError -> {
                    logger.error("${project.findLocationString(e.file, e.offset)}: ${e.message}")
                    logger.reportAll()
                    updateFromShadow()
                    return if (options.returnOkOnError) {
                        AnalysisResult.success(BindingContext.EMPTY, module, shouldGenerateCode = false)
                    } else {
                        AnalysisResult.compilationError(BindingContext.EMPTY)
                    }
                }

                e.isNotRecoverable() -> {
                    e.logToError()
                    logger.reportAll()
                    updateFromShadow()
                    return if (options.returnOkOnError) {
                        AnalysisResult.success(BindingContext.EMPTY, module, shouldGenerateCode = false)
                    } else {
                        AnalysisResult.internalError(BindingContext.EMPTY, e)
                    }
                }

                // Let this round finish.
                else -> {
                    e.logToError()
                }
            }
        }
        return null
    }

    private val javaShadowBase = File(options.javaOutputDir, "byRounds")

    private val javaShadowDir: File
        get() = File(javaShadowBase, "$rounds")

    private fun updateFromShadow() {
        if (javaShadowBase.exists() && javaShadowBase.isDirectory()) {
            javaShadowBase.listFiles()?.forEach { roundDir ->
                if (roundDir.exists() && roundDir.isDirectory()) {
                    roundDir.walkTopDown().forEach {
                        val dst = File(options.javaOutputDir, File(it.path).toRelativeString(roundDir))
                        if (dst.isFile || !dst.exists()) {
                            copyWithTimestamp(it, dst, overwrite = dst.isFile)
                        }
                    }
                }
            }
            javaShadowBase.deleteRecursively()
        }
    }
}

fun findTargetInfos(languageVersionSettings: LanguageVersionSettings, module: ModuleDescriptor): List<PlatformInfo> =
    module.platform?.componentPlatforms?.map { platform ->
        when (platform) {
            is JdkPlatform -> JvmPlatformInfoImpl(
                platformName = platform.platformName,
                jvmTarget = platform.targetVersion.toString(),
                jvmDefaultMode = languageVersionSettings.getFlag(JvmAnalysisFlags.jvmDefaultMode).description
            )
            is JsPlatform -> JsPlatformInfoImpl(
                platformName = platform.platformName
            )
            is NativePlatform -> NativePlatformInfoImpl(
                platformName = platform.platformName,
                targetName = platform.targetName
            )
            // Unknown platform; toString() may be more informative than platformName
            else -> UnknownPlatformInfoImpl(platform.toString())
        }
    } ?: emptyList()

// FIXME: remove as soon as possible.
private fun invalidateKotlinCliJavaFileManagerCache(project: Project): Boolean {
    val javaFileManager = (JavaFileManager.getInstance(project) as? KotlinCliJavaFileManagerImpl) ?: return false
    val privateCacheField = KotlinCliJavaFileManagerImpl::class.java.getDeclaredField("topLevelClassesCache")
    if (!privateCacheField.trySetAccessible())
        return false
    (privateCacheField.get(javaFileManager) as? MutableMap<*, *>)?.clear() ?: return false
    return true
}
