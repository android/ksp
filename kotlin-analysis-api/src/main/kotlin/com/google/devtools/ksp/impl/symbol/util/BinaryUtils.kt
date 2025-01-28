/*
 * Copyright 2022 Google LLC
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
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

package com.google.devtools.ksp.impl.symbol.util

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.common.KSObjectCache
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.impl.ResolverAAImpl
import com.google.devtools.ksp.impl.symbol.kotlin.AbstractKSDeclarationImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSFunctionDeclarationImpl
import com.google.devtools.ksp.impl.symbol.kotlin.KSPropertyDeclarationImpl
import com.google.devtools.ksp.impl.symbol.kotlin.analyze
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCliJavaFileManagerImpl
import org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.FieldVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.util.IdentityHashMap

data class BinaryClassInfo(
    val fieldAccFlags: Map<String, Int>,
    val methodAccFlags: Map<String, Int>
)

/**
 * Lookup cache for field names for deserialized classes.
 * To check if a field has backing field, we need to look for binary field names, hence they are cached here.
 */
object BinaryClassInfoCache : KSObjectCache<ClassId, BinaryClassInfo?>() {
    fun getCached(classId: ClassId, fileManager: KotlinCliJavaFileManagerImpl) = cache.getOrPut(classId) {
        val fieldAccFlags = mutableMapOf<String, Int>()
        val methodAccFlags = mutableMapOf<String, Int>()
        val virtualFileContent = classId.getFileContent(fileManager) ?: return@getOrPut null
        ClassReader(virtualFileContent).accept(
            object : ClassVisitor(Opcodes.API_VERSION) {
                override fun visitField(
                    access: Int,
                    name: String?,
                    descriptor: String?,
                    signature: String?,
                    value: Any?
                ): FieldVisitor? {
                    if (name != null) {
                        fieldAccFlags[name] = access
                    }
                    return null
                }

                override fun visitMethod(
                    access: Int,
                    name: String?,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?
                ): MethodVisitor? {
                    if (name != null) {
                        methodAccFlags[name + descriptor] = access
                    }
                    return null
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
        )
        BinaryClassInfo(fieldAccFlags, methodAccFlags)
    }
}

fun KSAnnotated.hasAnnotation(fqn: String): Boolean =
    annotations.any {
        fqn.endsWith(it.shortName.asString()) &&
            it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn
    }

fun Resolver.extractThrowsFromClassFile(
    virtualFileContent: ByteArray,
    jvmDesc: String?,
    simpleName: String?
): Sequence<KSType> {
    val exceptionNames = mutableListOf<String>()
    ClassReader(virtualFileContent).accept(
        object : ClassVisitor(Opcodes.API_VERSION) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor {
                if (name == simpleName && jvmDesc == descriptor) {
                    exceptions?.toList()?.let { exceptionNames.addAll(it) }
                }
                return object : MethodVisitor(Opcodes.API_VERSION) {
                }
            }
        },
        ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
    )
    return exceptionNames.mapNotNull {
        this.getClassDeclarationByName(it.replace("/", "."))?.asStarProjectedType()
    }.asSequence()
}

@KspExperimental
internal class DeclarationOrdering(
    binaryClass: KotlinJvmBinaryClass
) : KotlinJvmBinaryClass.MemberVisitor {
    // Map of fieldName -> Order
    private val fieldOrdering = mutableMapOf<String, Int>()
    // Map of method name to (jvm desc -> Order) map
    // multiple methods might have the same name, hence we need to use signature matching for
    // methods. That being said, we only do it when we find multiple methods with the same name
    // otherwise, there is no reason to compute the jvm signature.
    private val methodOrdering = mutableMapOf<String, MutableMap<String, Int>>()
    // This map is built while we are sorting to ensure for the same declaration, we return the same
    // order, in case it is not found in fields / methods.
    private val declOrdering = IdentityHashMap<KSDeclaration, Int>()
    // Helper class to generate ids that can be used for comparison.
    private val orderProvider = OrderProvider()

    init {
        binaryClass.visitMembers(this, null)
        orderProvider.seal()
    }

    val comparator = Comparator<AbstractKSDeclarationImpl> { first, second ->
        getOrder(first).compareTo(getOrder(second))
    }

    private fun getOrder(decl: AbstractKSDeclarationImpl): Int {
        return declOrdering.getOrPut(decl.asKSDeclaration()) {
            when (decl) {
                is KSPropertyDeclarationImpl -> {
                    fieldOrdering[decl.simpleName.asString()]?.let {
                        return@getOrPut it
                    }
                    // might be a property without backing field. Use method ordering instead
                    decl.getter?.let { getter ->
                        return@getOrPut findMethodOrder(
                            ResolverAAImpl.instance.getJvmName(getter)
                        ) {
                            ResolverAAImpl.instance.mapToJvmSignature(getter)
                        }
                    }
                    decl.setter?.let { setter ->
                        return@getOrPut findMethodOrder(
                            ResolverAAImpl.instance.getJvmName(setter)
                        ) {
                            ResolverAAImpl.instance.mapToJvmSignature(setter)
                        }
                    }
                    orderProvider.next(decl)
                }
                is KSFunctionDeclarationImpl -> {
                    findMethodOrder(
                        ResolverAAImpl.instance.getJvmName(decl)
                    ) {
                        ResolverAAImpl.instance.mapToJvmSignature(decl).toString()
                    }
                }
                else -> orderProvider.nextIgnoreSealed()
            }
        }
    }

    private inline fun findMethodOrder(
        jvmName: String,
        crossinline getJvmDesc: () -> String
    ): Int {
        val methods = methodOrdering[jvmName]
        // if there is 1 method w/ that name, just return.
        // otherwise, we need signature matching
        return when {
            methods == null -> {
                orderProvider.next(jvmName)
            }
            methods.size == 1 -> {
                // only 1 method with this name, return it, no reason to resolve jvm
                // signature
                methods.values.first()
            }
            else -> {
                // need to match using the jvm signature
                val jvmDescriptor = getJvmDesc()
                methods.getOrPut(jvmDescriptor) {
                    orderProvider.next(jvmName)
                }
            }
        }
    }

    override fun visitField(
        name: Name,
        desc: String,
        initializer: Any?
    ): KotlinJvmBinaryClass.AnnotationVisitor? {
        fieldOrdering.getOrPut(name.asString()) {
            orderProvider.next(name)
        }
        return null
    }

    override fun visitMethod(
        name: Name,
        desc: String
    ): KotlinJvmBinaryClass.MethodAnnotationVisitor? {
        methodOrdering.getOrPut(name.asString()) {
            mutableMapOf()
        }[desc] = orderProvider.next(name)
        return null
    }

    /**
     * Helper class to generate order values for items.
     * Each time we see a new declaration, we give it an increasing order.
     *
     * This provider can also run in STRICT MODE to ensure that if we don't find an expected value
     * during sorting, we can crash instead of picking the next ID. For now, it is only used for
     * testing.
     */
    private class OrderProvider {
        private var nextId = 0
        private var sealed = false

        /**
         * Seals the provider, preventing it from generating new IDs if [STRICT_MODE] is enabled.
         */
        fun seal() {
            sealed = true
        }

        /**
         * Returns the next available order value.
         *
         * @param ref Used for logging if the data is sealed, and we shouldn't provide a new order.
         */
        fun next(ref: Any): Int {
            check(!sealed || !STRICT_MODE) {
                "couldn't find item $ref"
            }
            return nextId ++
        }

        /**
         * Returns the next ID without checking whether the model is sealed or not. This is useful
         * for declarations where we don't care about the order (e.g. inner class declarations).
         */
        fun nextIgnoreSealed(): Int {
            return nextId ++
        }
    }
    companion object {
        /**
         * Used in tests to prevent fallback behavior of creating a new ID when we cannot find the
         * order.
         */
        var STRICT_MODE = false
    }
}

// Expensive; Use with caution.
internal fun ClassId.getFileContent(fileManager: KotlinCliJavaFileManagerImpl): ByteArray? =
    getVirtualFile(fileManager)?.contentsToByteArray()

internal fun ClassId.getVirtualFile(fileManager: KotlinCliJavaFileManagerImpl): VirtualFile? =
    analyze {
        (fileManager.findClass(this@getVirtualFile, analysisScope) as? JavaClassImpl)?.virtualFile
    }
