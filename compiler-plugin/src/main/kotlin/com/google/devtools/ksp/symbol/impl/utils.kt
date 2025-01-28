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
package com.google.devtools.ksp.symbol.impl

import com.google.devtools.ksp.ExceptionMessage
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.common.errorTypeOnInconsistentArguments
import com.google.devtools.ksp.common.impl.KSNameImpl
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.symbol.impl.binary.KSClassDeclarationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.binary.KSDeclarationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.binary.KSFunctionDeclarationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.binary.KSPropertyDeclarationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.binary.KSTypeArgumentDescriptorImpl
import com.google.devtools.ksp.symbol.impl.java.*
import com.google.devtools.ksp.symbol.impl.kotlin.*
import com.google.devtools.ksp.symbol.impl.synthetic.KSPropertyGetterSyntheticImpl
import com.google.devtools.ksp.symbol.impl.synthetic.KSPropertySetterSyntheticImpl
import com.google.devtools.ksp.symbol.impl.synthetic.KSValueParameterSyntheticImpl
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightMethod
import com.intellij.psi.impl.source.PsiClassImpl
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMapper
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.load.java.descriptors.JavaClassConstructorDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.load.java.lazy.ModuleClassResolver
import org.jetbrains.kotlin.load.java.sources.JavaSourceElement
import org.jetbrains.kotlin.load.java.structure.impl.JavaConstructorImpl
import org.jetbrains.kotlin.load.java.structure.impl.JavaMethodImpl
import org.jetbrains.kotlin.load.java.structure.impl.classFiles.BinaryJavaField
import org.jetbrains.kotlin.load.java.structure.impl.classFiles.BinaryJavaMethodBase
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinaryClass
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.load.kotlin.getContainingKotlinJvmBinaryClass
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getOwnerForEffectiveDispatchReceiverParameter
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedClassDescriptor
import org.jetbrains.kotlin.types.*
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayDeque

fun PsiElement.findParentAnnotated(): KSAnnotated? {
    var parent = when (this) {
        // Unfortunately, LightMethod doesn't implement parent.
        is LightMethod -> this.containingClass
        is PsiMethod -> this.containingClass
        else -> this.parent
    }

    while (parent != null && parent !is KtDeclaration && parent !is KtFile && parent !is PsiClass &&
        parent !is PsiMethod && parent !is PsiJavaFile
    ) {
        parent = parent.parent
    }

    return when (parent) {
        is KtClassOrObject -> KSClassDeclarationImpl.getCached(parent)
        is KtFile -> KSFileImpl.getCached(parent)
        is KtFunction -> KSFunctionDeclarationImpl.getCached(parent)
        is PsiClass -> KSClassDeclarationJavaImpl.getCached(parent)
        is PsiJavaFile -> KSFileJavaImpl.getCached(parent)
        is PsiMethod -> KSFunctionDeclarationJavaImpl.getCached(parent)
        is KtProperty -> KSPropertyDeclarationImpl.getCached(parent)
        is KtPropertyAccessor -> if (parent.isGetter) {
            KSPropertyGetterImpl.getCached(parent)
        } else {
            KSPropertySetterImpl.getCached(parent)
        }
        is KtTypeAlias -> KSTypeAliasImpl.getCached(parent)
        else -> null
    }
}

fun PsiElement.findParentDeclaration(): KSDeclaration? {
    return this.findParentAnnotated() as? KSDeclaration
}

fun PsiElement.toLocation(): Location {
    val file = this.containingFile
    val document = ResolverImpl.instance!!.psiDocumentManager.getDocument(file) ?: return NonExistLocation
    return FileLocation(file.virtualFile.path, document.getLineNumber(this.textOffset) + 1)
}

// TODO: handle local functions/classes correctly
fun Sequence<KtElement>.getKSDeclarations(): Sequence<KSDeclaration> =
    this.mapNotNull {
        when (it) {
            is KtFunction -> KSFunctionDeclarationImpl.getCached(it)
            is KtProperty -> KSPropertyDeclarationImpl.getCached(it)
            is KtClassOrObject -> KSClassDeclarationImpl.getCached(it)
            is KtTypeAlias -> KSTypeAliasImpl.getCached(it)
            else -> null
        }
    }

fun List<PsiElement>.getKSJavaDeclarations() =
    this.mapNotNull {
        when (it) {
            is PsiClass -> KSClassDeclarationJavaImpl.getCached(it)
            is PsiMethod -> KSFunctionDeclarationJavaImpl.getCached(it)
            is PsiField -> KSPropertyDeclarationJavaImpl.getCached(it)
            else -> null
        }
    }

fun org.jetbrains.kotlin.types.Variance.toKSVariance(): Variance {
    return when (this) {
        org.jetbrains.kotlin.types.Variance.IN_VARIANCE -> Variance.CONTRAVARIANT
        org.jetbrains.kotlin.types.Variance.OUT_VARIANCE -> Variance.COVARIANT
        org.jetbrains.kotlin.types.Variance.INVARIANT -> Variance.INVARIANT
        else -> throw IllegalStateException("Unexpected variance value $this, $ExceptionMessage")
    }
}

private fun KSTypeReference.toKotlinType() = (resolve() as? KSTypeImpl)?.kotlinType

// returns KSErrorType if error
internal fun KotlinType.replaceTypeArguments(
    newArguments: List<KSTypeArgument>,
    annotations: Sequence<KSAnnotation> = emptySequence(),
): KSType {
    errorTypeOnInconsistentArguments(
        arguments = newArguments,
        placeholdersProvider = { arguments.map { KSTypeArgumentDescriptorImpl.getCached(it, Origin.SYNTHETIC, null) } },
        withCorrectedArguments = ::replaceTypeArguments,
        errorType = ::KSErrorType,
    )?.let { error -> return error }

    return replace(
        newArguments.mapIndexed { index, ksTypeArgument ->
            val variance = when (ksTypeArgument.variance) {
                Variance.INVARIANT -> org.jetbrains.kotlin.types.Variance.INVARIANT
                Variance.COVARIANT -> org.jetbrains.kotlin.types.Variance.OUT_VARIANCE
                Variance.CONTRAVARIANT -> org.jetbrains.kotlin.types.Variance.IN_VARIANCE
                Variance.STAR -> return@mapIndexed StarProjectionImpl(constructor.parameters[index])
            }

            val type = when (ksTypeArgument) {
                is KSTypeArgumentKtImpl, is KSTypeArgumentJavaImpl, is KSTypeArgumentLiteImpl -> ksTypeArgument.type!!
                is KSTypeArgumentDescriptorImpl -> return@mapIndexed ksTypeArgument.descriptor
                else -> throw IllegalStateException(
                    "Unexpected psi for type argument: ${ksTypeArgument.javaClass}, $ExceptionMessage"
                )
            }.let { it.toKotlinType() ?: return KSErrorType.fromReferenceBestEffort(it) }

            TypeProjectionImpl(variance, type)
        }
    ).let { KSTypeImpl.getCached(it, newArguments, annotations) }
}

internal fun FunctionDescriptor.toKSDeclaration(): KSDeclaration {
    if (this.kind != CallableMemberDescriptor.Kind.DECLARATION)
        return KSFunctionDeclarationDescriptorImpl.getCached(this)
    val psi = this.findPsi() ?: return KSFunctionDeclarationDescriptorImpl.getCached(this)
    // Java default constructor has a kind DECLARATION of while still being synthetic.
    if (psi is PsiClassImpl && this is JavaClassConstructorDescriptor) {
        return KSFunctionDeclarationDescriptorImpl.getCached(this)
    }
    return when (psi) {
        is KtFunction -> KSFunctionDeclarationImpl.getCached(psi)
        is PsiMethod -> KSFunctionDeclarationJavaImpl.getCached(psi)
        is KtProperty -> KSPropertyDeclarationImpl.getCached(psi)
        else -> throw IllegalStateException("unexpected psi: ${psi.javaClass}")
    }
}

internal fun PropertyDescriptor.toKSPropertyDeclaration(): KSPropertyDeclaration {
    if (this.kind != CallableMemberDescriptor.Kind.DECLARATION)
        return KSPropertyDeclarationDescriptorImpl.getCached(this)
    val psi = this.findPsi() ?: return KSPropertyDeclarationDescriptorImpl.getCached(this)
    return when (psi) {
        is KtProperty -> KSPropertyDeclarationImpl.getCached(psi)
        is KtParameter -> KSPropertyDeclarationParameterImpl.getCached(psi)
        is PsiField -> KSPropertyDeclarationJavaImpl.getCached(psi)
        is PsiMethod -> {
            // happens when a java class implements a kotlin interface that declares properties.
            KSPropertyDeclarationDescriptorImpl.getCached(this)
        }
        else -> throw IllegalStateException("unexpected psi: ${psi.javaClass}")
    }
}

/**
 * @see KSFunctionDeclaration.findOverridee / [KSPropertyDeclaration.findOverridee] for docs.
 */
internal inline fun <reified T : CallableMemberDescriptor> T.findClosestOverridee(): T? {
    // When there is an intermediate class between the overridden and our function, we might receive
    // a FAKE_OVERRIDE function which is not desired as we are trying to find the actual
    // declared method.

    // we also want to return the closes function declaration. That is either the closest
    // class / interface method OR in case of equal distance (e.g. diamon dinheritance), pick the
    // one declared first in the code.

    (getOwnerForEffectiveDispatchReceiverParameter() as? ClassDescriptor)?.defaultType?.let {
        ResolverImpl.instance!!.incrementalContext.recordLookupWithSupertypes(it)
    }

    val queue = ArrayDeque<T>()
    queue.add(this)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        ResolverImpl.instance!!.incrementalContext.recordLookupForCallableMemberDescriptor(current.original)
        val overriddenDescriptors: Collection<T> = current.original.overriddenDescriptors.filterIsInstance<T>()
        overriddenDescriptors.firstOrNull {
            it.kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE
        }?.let {
            ResolverImpl.instance!!.incrementalContext.recordLookupForCallableMemberDescriptor(it.original)
            return it.original as T?
        }
        // if all methods are fake, add them to the queue
        queue.addAll(overriddenDescriptors)
    }
    return null
}

internal fun ModuleClassResolver.resolveContainingClass(psiMethod: PsiMethod): ClassDescriptor? {
    return if (psiMethod.isConstructor) {
        resolveClass(JavaConstructorImpl(psiMethod).containingClass)
    } else {
        resolveClass(JavaMethodImpl(psiMethod).containingClass)
    }
}

internal fun getInstanceForCurrentRound(node: KSNode): KSNode? {
    return when (node.origin) {
        Origin.KOTLIN_LIB, Origin.JAVA_LIB -> null
        else -> when (node) {
            is KSClassDeclarationImpl -> KSClassDeclarationImpl.getCached(node.ktClassOrObject)
            is KSFileImpl -> KSFileImpl.getCached(node.file)
            is KSFunctionDeclarationImpl -> KSFunctionDeclarationImpl.getCached(node.ktFunction)
            is KSPropertyDeclarationImpl -> KSPropertyDeclarationImpl.getCached(node.ktProperty)
            is KSPropertyGetterImpl -> KSPropertyGetterImpl.getCached(node.ktPropertyAccessor)
            is KSPropertySetterImpl -> KSPropertySetterImpl.getCached(node.ktPropertyAccessor)
            is KSTypeAliasImpl -> KSTypeAliasImpl.getCached(node.ktTypeAlias)
            is KSTypeArgumentLiteImpl -> KSTypeArgumentLiteImpl.getCached(node.type, node.variance)
            is KSTypeArgumentKtImpl -> KSTypeArgumentKtImpl.getCached(node.ktTypeArgument)
            is KSTypeParameterImpl -> KSTypeParameterImpl.getCached(node.ktTypeParameter)
            is KSTypeReferenceImpl -> KSTypeReferenceImpl.getCached(node.ktTypeReference)
            is KSValueParameterImpl -> KSValueParameterImpl.getCached(node.ktParameter)
            is KSClassDeclarationJavaEnumEntryImpl -> KSClassDeclarationJavaEnumEntryImpl.getCached(node.psi)
            is KSClassDeclarationJavaImpl -> KSClassDeclarationJavaImpl.getCached(node.psi)
            is KSFileJavaImpl -> KSFileJavaImpl.getCached(node.psi)
            is KSFunctionDeclarationJavaImpl -> KSFunctionDeclarationJavaImpl.getCached(node.psi)
            is KSPropertyDeclarationJavaImpl -> KSPropertyDeclarationJavaImpl.getCached(node.psi)
            is KSTypeArgumentJavaImpl -> KSTypeArgumentJavaImpl.getCached(node.psi, node.parent)
            is KSTypeParameterJavaImpl -> KSTypeParameterJavaImpl.getCached(node.psi)
            is KSTypeReferenceJavaImpl ->
                KSTypeReferenceJavaImpl.getCached(node.psi, (node.parent as? KSAnnotated)?.getInstanceForCurrentRound())
            is KSValueParameterJavaImpl -> KSValueParameterJavaImpl.getCached(node.psi, node.parent)
            is KSPropertyGetterSyntheticImpl -> KSPropertyGetterSyntheticImpl.getCached(node.ksPropertyDeclaration)
            is KSPropertySetterSyntheticImpl -> KSPropertySetterSyntheticImpl.getCached(node.ksPropertyDeclaration)
            is KSValueParameterSyntheticImpl ->
                KSPropertySetterImpl.getCached(node.owner as KtPropertyAccessor).parameter
            is KSAnnotationJavaImpl -> KSAnnotationJavaImpl.getCached(node.psi)
            is KSAnnotationImpl -> KSAnnotationImpl.getCached(node.ktAnnotationEntry)
            is KSClassifierReferenceJavaImpl -> KSClassifierReferenceJavaImpl.getCached(node.psi, node.parent)
            is KSValueArgumentJavaImpl ->
                KSValueArgumentJavaImpl.getCached(node.name, node.value, getInstanceForCurrentRound(node.parent!!))
            else -> null
        }
    }
}

internal fun KSAnnotated.getInstanceForCurrentRound(): KSAnnotated? = getInstanceForCurrentRound(this) as? KSAnnotated

/**
 * Helper class to read the order of fields/methods in a .class file compiled from Kotlin.
 *
 * When a compiled Kotlin class is read from descriptors, the order of fields / methods do not match
 * the order in the original source file (or the .class file).
 * This helper class reads the order from the binary class (using the visitor API) and allows
 * [KSClassDeclarationDescriptorImpl] to sort its declarations based on the .class file.
 *
 * Note that the ordering is relevant only for fields and methods. For any other declaration, the
 * order that was returned from the descriptor API is kept.
 *
 * see: https://github.com/google/ksp/issues/250
 */
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

    val comparator = Comparator<KSDeclarationDescriptorImpl> { first, second ->
        getOrder(first).compareTo(getOrder(second))
    }

    private fun getOrder(decl: KSDeclarationDescriptorImpl): Int {
        return declOrdering.getOrPut(decl.asKSDeclaration()) {
            when (decl) {
                is KSPropertyDeclarationDescriptorImpl -> {
                    fieldOrdering[decl.simpleName.asString()]?.let {
                        return@getOrPut it
                    }
                    // might be a property without backing field. Use method ordering instead
                    decl.getter?.let { getter ->
                        return@getOrPut findMethodOrder(
                            ResolverImpl.instance!!.getJvmName(getter).toString()
                        ) {
                            ResolverImpl.instance!!.mapToJvmSignature(getter)
                        }
                    }
                    decl.setter?.let { setter ->
                        return@getOrPut findMethodOrder(
                            ResolverImpl.instance!!.getJvmName(setter).toString()
                        ) {
                            ResolverImpl.instance!!.mapToJvmSignature(setter)
                        }
                    }
                    orderProvider.next(decl)
                }
                is KSFunctionDeclarationDescriptorImpl -> {
                    findMethodOrder(
                        ResolverImpl.instance!!.getJvmName(decl).toString()
                    ) {
                        ResolverImpl.instance!!.mapToJvmSignature(decl).toString()
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
        }.put(desc, orderProvider.next(name))
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
         * @param ref Used for logging if the data is sealed and we shouldn't provide a new order.
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

/**
 * Same as KSDeclarationContainer.declarations, but sorted by declaration order in the source.
 *
 * Note that this is SLOW. AVOID IF POSSIBLE.
 */
@Suppress("UNCHECKED_CAST")
@KspExperimental
internal val KSDeclarationContainer.declarationsInSourceOrder: Sequence<KSDeclaration>
    get() {
        // Only Kotlin libs can be out of order.
        if (this !is KSClassDeclarationDescriptorImpl || origin != Origin.KOTLIN_LIB)
            return declarations

        val declarationOrdering = (
            (descriptor as? DeserializedClassDescriptor)?.source as? KotlinJvmBinarySourceElement
            )?.binaryClass?.let {
            DeclarationOrdering(it)
        } ?: return declarations

        return declarations.sortedWith(compareBy(declarationOrdering.comparator) { it as KSDeclarationDescriptorImpl })
    }

internal val KSPropertyDeclaration.jvmAccessFlag: Int
    get() = when (origin) {
        Origin.KOTLIN_LIB -> {
            val descriptor = (this as KSPropertyDeclarationDescriptorImpl).descriptor
            val kotlinBinaryJavaClass = descriptor.getContainingKotlinJvmBinaryClass()
            // 0 if no backing field
            kotlinBinaryJavaClass?.let {
                BinaryClassInfoCache.getCached(it).fieldAccFlags.get(this.simpleName.asString()) ?: 0
            } ?: 0
        }
        Origin.JAVA_LIB -> {
            val descriptor = (this as KSPropertyDeclarationDescriptorImpl).descriptor
            ((descriptor.source as? JavaSourceElement)?.javaElement as? BinaryJavaField)?.access ?: 0
        }
        else -> throw IllegalStateException("this function expects only KOTLIN_LIB or JAVA_LIB")
    }

internal val KSFunctionDeclaration.jvmAccessFlag: Int
    get() = when (origin) {
        Origin.KOTLIN_LIB -> {
            val jvmDesc = ResolverImpl.instance!!.mapToJvmSignatureInternal(this)
            val descriptor = (this as KSFunctionDeclarationDescriptorImpl).descriptor
            // Companion.<init> doesn't have containing KotlinJvmBinaryClass.
            val kotlinBinaryJavaClass = descriptor.getContainingKotlinJvmBinaryClass()
            kotlinBinaryJavaClass?.let {
                BinaryClassInfoCache.getCached(it).methodAccFlags.get(this.simpleName.asString() + jvmDesc) ?: 0
            } ?: 0
        }
        Origin.JAVA_LIB -> {
            val descriptor = (this as KSFunctionDeclarationDescriptorImpl).descriptor
            // Some functions, like `equals` in builtin types, doesn't have source.
            ((descriptor.source as? JavaSourceElement)?.javaElement as? BinaryJavaMethodBase)?.access ?: 0
        }
        else -> throw IllegalStateException("this function expects only KOTLIN_LIB or JAVA_LIB")
    }

// Compiler subtype checking does not convert Java types to Kotlin types, while getting super types
// from a java type does the conversion, therefore resulting in subtype checking for Java types to fail.
// Check if candidate super type is a Java type, convert to Kotlin type for subtype checking.
// Also, if the type is a generic deserialized type, that actually represents a function type,
// a conversion is also required to yield a type with a correctly recognised descriptor.
internal fun KotlinType.convertKotlinType(): KotlinType {
    val declarationDescriptor = this.constructor.declarationDescriptor
    val base = if (declarationDescriptor?.shouldMapToKotlinForAssignabilityCheck() == true) {
        JavaToKotlinClassMapper
            .mapJavaToKotlin(declarationDescriptor.fqNameSafe, ResolverImpl.instance!!.module.builtIns)
            ?.defaultType
            ?.replace(this.arguments)
            ?: this
    } else this
    val newarguments =
        base.arguments.map { if (it !is StarProjectionImpl) it.replaceType(it.type.convertKotlinType()) else it }
    val upperBound = if (base.unwrap() is FlexibleType) {
        (base.unwrap() as FlexibleType).upperBound.arguments
            .map { if (it !is StarProjectionImpl) it.replaceType(it.type.convertKotlinType()) else it }
    } else newarguments
    return base.replace(
        newarguments,
        annotations,
        upperBound
    )
}

private fun ClassifierDescriptor.shouldMapToKotlinForAssignabilityCheck(): Boolean {
    return when (this) {
        is JavaClassDescriptor -> true // All java types need to be mapped to kotlin
        is DeserializedDescriptor -> {
            // If this is a generic deserialized type descriptor, which actually is a kotlin function type.
            // This may be the case if the client explicitly mapped a kotlin function type to the JVM one.
            // Such types need to be remapped to be represented by a correct function class descriptor.
            fqNameSafe.parent().asString() == "kotlin.jvm.functions"
        }
        else -> false
    }
}

fun DeclarationDescriptor.findPsi(): PsiElement? {
    // For synthetic members.
    if ((this is CallableMemberDescriptor) && this.kind != CallableMemberDescriptor.Kind.DECLARATION) return null
    val psi = (this as? DeclarationDescriptorWithSource)?.source?.getPsi() ?: return null
    if (psi is KtElement) return psi

    // Find Java PSIs loaded by KSP
    val containingFile = ResolverImpl.instance!!.findPsiJavaFile(psi.containingFile.virtualFile.path) ?: return null
    val leaf = containingFile.findElementAt(psi.textOffset) ?: return null
    return leaf.parentsWithSelf.firstOrNull { psi.manager.areElementsEquivalent(it, psi) }
}

fun DeclarationDescriptor.correspondsTo(candidate: PsiElement): Boolean {
    val psi = (this as? DeclarationDescriptorWithSource)?.source?.getPsi() ?: return false
    return psi.manager.areElementsEquivalent(psi, candidate)
}

internal fun KSFile.getPackageAnnotations() = (this as? KSFileJavaImpl)?.psi?.packageStatement
    ?.annotationList?.annotations?.map { KSAnnotationJavaImpl.getCached(it) } ?: emptyList<KSAnnotation>()

fun ClassId.toKSName() = KSNameImpl.getCached(asSingleFqName().toString())
