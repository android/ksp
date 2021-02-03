package com.google.devtools.ksp.processor

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSTopDownVisitor

class AnnotationsInDependenciesProcessor : AbstractTestProcessor() {
    private val results = mutableListOf<String>()
    override fun toResult() = results

    override fun process(resolver: Resolver) {
        // NOTE: There are two cases this test ignores.
        // a) For property annotations with target, they get added to the property getter/setter whereas it would show
        //    on the property if it was in kotlin source. This test ignores it by using the property name as key for
        //    property getters/setters
        // b) When an annotation without a target is used in a constructor (with field), that annotation is not copied
        //    to the backing field for .class files. The assertion line in test ignores it (see the NoTargetAnnotation
        //    output difference for the DataClass)
        addToResults(resolver, "main.KotlinClass")
        addToResults(resolver, "lib.KotlinClass")
        addToResults(resolver, "main.DataClass")
        addToResults(resolver, "lib.DataClass")
    }

    private fun addToResults(resolver: Resolver, qName: String) {
        results.add("$qName ->")
        val collected = collectAnnotations(resolver, qName)
        val signatures = collected.flatMap {(annotated, annotations) ->
            val annotatedSignature = annotated.toSignature()
            annotations.map {
                "$annotatedSignature : ${it.toSignature()}"
            }
        }.sorted()
        results.addAll(signatures)
    }

    private fun collectAnnotations(resolver: Resolver, qName: String) : Map<KSAnnotated, List<KSAnnotation>> {
        val output = mutableMapOf<KSAnnotated, List<KSAnnotation>>()
        resolver.getClassDeclarationByName(qName)?.accept(
            AnnotationVisitor(),
            output
        )
        return output
    }

    private fun KSAnnotated.toSignature(): String {
        return when(this) {
            is KSClassDeclaration -> "class " + (qualifiedName ?: simpleName).asString()
            is KSPropertyDeclaration -> "property ${simpleName.asString()}"
            is KSFunctionDeclaration -> "function ${simpleName.asString()}"
            is KSValueParameter -> name?.let {
                "parameter ${it.asString()}"
            } ?: "no-name-value-parameter"
            is KSPropertyAccessor -> receiver.toSignature()
            else -> {
                error("unexpected annotated")
            }
        }
    }

    private fun KSAnnotation.toSignature(): String {
        val type = this.annotationType.resolve().declaration.let {
            (it.qualifiedName ?: it.simpleName).asString()
        }
        val args = this.arguments.map {
            "[${it.name?.asString()} = ${it.value}]"
        }.joinToString(",")
        return "$type{$args}"
    }

    class AnnotationVisitor : KSTopDownVisitor<MutableMap<KSAnnotated, List<KSAnnotation>>, Unit>() {
        override fun defaultHandler(node: KSNode, data: MutableMap<KSAnnotated, List<KSAnnotation>>) {
        }

        override fun visitAnnotated(annotated: KSAnnotated, data: MutableMap<KSAnnotated, List<KSAnnotation>>) {
            if (annotated.annotations.isNotEmpty()) {
                data[annotated] = annotated.annotations
            }
            super.visitAnnotated(annotated, data)
        }

        override fun visitTypeReference(typeReference: KSTypeReference, data: MutableMap<KSAnnotated, List<KSAnnotation>>) {
            // don't traverse type references
        }
    }
}
