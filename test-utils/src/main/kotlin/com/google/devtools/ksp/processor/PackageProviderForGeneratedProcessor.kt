package com.google.devtools.ksp.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated

class PackageProviderForGeneratedProcessor : AbstractTestProcessor() {
    val result = mutableListOf<String>()
    override fun toResult(): List<String> {
        return result
    }

    var round = 0

    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (round == 0) {
            createJavaFile("foo.bar", "MyGeneratedJavaClass")
        }
        result.add("Processing round $round")
        val getByName = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString("foo.bar.MyGeneratedJavaClass")
        )?.qualifiedName?.asString()
        result.add("resolver.getClassDeclarationByName: $getByName")
        val getByPackage = resolver.getDeclarationsFromPackage("foo.bar").toList().map { it.qualifiedName?.asString() }
        result.add("resolver.getDeclarationsFromPackage: $getByPackage")
        round++
        return emptyList()
    }

    lateinit var env: SymbolProcessorEnvironment

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        env = environment
        return this
    }

    private fun createJavaFile(packageName: String, className: String) {
        val dependencies = Dependencies(aggregating = false, sources = arrayOf())
        env.codeGenerator.createNewFile(dependencies, packageName, className, "java").use {
            it.write("package foo.bar;\nclass MyGeneratedJavaClass{}".toByteArray())
        }
    }
}
