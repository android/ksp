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

package com.google.devtools.ksp.processing.impl

import com.google.devtools.ksp.*
import com.google.devtools.ksp.common.JVM_DEFAULT_ANNOTATION_FQN
import com.google.devtools.ksp.common.JVM_DEFAULT_WITHOUT_COMPATIBILITY_ANNOTATION_FQN
import com.google.devtools.ksp.common.JVM_STATIC_ANNOTATION_FQN
import com.google.devtools.ksp.common.JVM_STRICTFP_ANNOTATION_FQN
import com.google.devtools.ksp.common.JVM_SYNCHRONIZED_ANNOTATION_FQN
import com.google.devtools.ksp.common.JVM_TRANSIENT_ANNOTATION_FQN
import com.google.devtools.ksp.common.JVM_VOLATILE_ANNOTATION_FQN
import com.google.devtools.ksp.common.extractThrowsAnnotation
import com.google.devtools.ksp.common.impl.KSNameImpl
import com.google.devtools.ksp.common.impl.KSTypeReferenceSyntheticImpl
import com.google.devtools.ksp.common.impl.RefPosition
import com.google.devtools.ksp.common.impl.findOuterMostRef
import com.google.devtools.ksp.common.impl.findRefPosition
import com.google.devtools.ksp.common.impl.isReturnTypeOfAnnotationMethod
import com.google.devtools.ksp.common.javaModifiers
import com.google.devtools.ksp.common.memoized
import com.google.devtools.ksp.common.visitor.CollectAnnotatedSymbolsVisitor
import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.symbol.impl.*
import com.google.devtools.ksp.symbol.impl.binary.*
import com.google.devtools.ksp.symbol.impl.declarationsInSourceOrder
import com.google.devtools.ksp.symbol.impl.getInstanceForCurrentRound
import com.google.devtools.ksp.symbol.impl.java.*
import com.google.devtools.ksp.symbol.impl.kotlin.*
import com.google.devtools.ksp.symbol.impl.synthetic.KSConstructorSyntheticImpl
import com.google.devtools.ksp.symbol.impl.synthetic.KSPropertyGetterSyntheticImpl
import com.google.devtools.ksp.symbol.impl.synthetic.KSPropertySetterSyntheticImpl
import com.google.devtools.ksp.symbol.impl.synthetic.KSValueParameterSyntheticImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.codegen.signature.BothSignatureWriter
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.container.tryGetService
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.java.components.TypeUsage
import org.jetbrains.kotlin.load.java.descriptors.JavaForKotlinOverridePropertyDescriptor
import org.jetbrains.kotlin.load.java.descriptors.getImplClassNameForDeserialized
import org.jetbrains.kotlin.load.java.lazy.*
import org.jetbrains.kotlin.load.java.lazy.descriptors.LazyJavaTypeParameterDescriptor
import org.jetbrains.kotlin.load.java.lazy.types.JavaTypeResolver
import org.jetbrains.kotlin.load.java.lazy.types.toAttributes
import org.jetbrains.kotlin.load.java.sources.JavaSourceElement
import org.jetbrains.kotlin.load.java.structure.impl.*
import org.jetbrains.kotlin.load.java.structure.impl.classFiles.BinaryJavaClass
import org.jetbrains.kotlin.load.java.structure.impl.classFiles.BinaryJavaMethod
import org.jetbrains.kotlin.load.java.structure.impl.classFiles.BinaryJavaMethodBase
import org.jetbrains.kotlin.load.kotlin.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.JvmStandardClassIds.JVM_SUPPRESS_WILDCARDS_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.name.JvmStandardClassIds.JVM_WILDCARD_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.calls.inference.components.NewTypeSubstitutor
import org.jetbrains.kotlin.resolve.calls.inference.components.composeWith
import org.jetbrains.kotlin.resolve.calls.inference.substitute
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.descriptorUtil.propertyIfAccessor
import org.jetbrains.kotlin.resolve.lazy.DeclarationScopeProvider
import org.jetbrains.kotlin.resolve.lazy.ForceResolveUtil
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.multiplatform.findActuals
import org.jetbrains.kotlin.resolve.multiplatform.findExpects
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DescriptorWithContainerSource
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections
import org.jetbrains.kotlin.types.typeUtil.substitute
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.util.containingNonLocalDeclaration
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import java.io.File
import java.util.*

class ResolverImpl(
    val module: ModuleDescriptor,
    val allKSFiles: Collection<KSFile>,
    val newKSFiles: Collection<KSFile>,
    private val deferredSymbols: Map<SymbolProcessor, List<KSAnnotated>>,
    val bindingTrace: BindingTrace,
    project: Project,
    componentProvider: ComponentProvider,
    val incrementalContext: IncrementalContext,
    val options: KspOptions,
) : Resolver {
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    private val nameToKSMap: MutableMap<KSName, KSClassDeclaration>
    private val javaTypeParameterMap: MutableMap<LazyJavaTypeParameterDescriptor, PsiTypeParameter> = mutableMapOf()
    private val packageInfoFiles by lazy {
        allKSFiles.filter { it.fileName == "package-info.java" }.asSequence().memoized()
    }

    /**
     * Checking as member of is an expensive operation, hence we cache result values in this map.
     */
    private val functionAsMemberOfCache: MutableMap<Pair<KSFunctionDeclaration, KSType>, KSFunction>
    private val propertyAsMemberOfCache: MutableMap<Pair<KSPropertyDeclaration, KSType>, KSType>

    private val moduleIdentifier = module.name.getNonSpecialIdentifier()
    private val typeMapper = KotlinTypeMapper(
        moduleIdentifier,
        LanguageVersionSettingsImpl(LanguageVersion.KOTLIN_1_9, ApiVersion.KOTLIN_1_9),
        true
    )
    private val qualifiedExpressionResolver = QualifiedExpressionResolver(LanguageVersionSettingsImpl.DEFAULT)

    private val aliasingFqNs: MutableMap<String, KSTypeAlias> = mutableMapOf()
    private val aliasingNames: MutableSet<String> = mutableSetOf()
    private val topDownAnalysisContext by lazy {
        TopDownAnalysisContext(TopDownAnalysisMode.TopLevelDeclarations, DataFlowInfo.EMPTY, declarationScopeProvider)
    }

    companion object {
        var instance: ResolverImpl? = null
    }

    var resolveSession: ResolveSession
    var bodyResolver: BodyResolver
    var constantExpressionEvaluator: ConstantExpressionEvaluator
    var declarationScopeProvider: DeclarationScopeProvider

    lateinit var moduleClassResolver: ModuleClassResolver
    lateinit var javaTypeResolver: JavaTypeResolver
    lateinit var lazyJavaResolverContext: LazyJavaResolverContext

    init {
        resolveSession = componentProvider.get()
        bodyResolver = componentProvider.get()
        declarationScopeProvider = componentProvider.get()
        constantExpressionEvaluator = componentProvider.get()

        componentProvider.tryGetService(JavaResolverComponents::class.java)?.let {
            lazyJavaResolverContext = LazyJavaResolverContext(it, TypeParameterResolver.EMPTY) { null }
            javaTypeResolver = lazyJavaResolverContext.typeResolver
            moduleClassResolver = lazyJavaResolverContext.components.moduleClassResolver
        }
        instance = this

        nameToKSMap = mutableMapOf()
        functionAsMemberOfCache = mutableMapOf()
        propertyAsMemberOfCache = mutableMapOf()

        val visitor = object : KSVisitorVoid() {
            override fun visitFile(file: KSFile, data: Unit) {
                file.declarations.forEach { it.accept(this, data) }

                // TODO: evaluate with benchmarks: cost of getContainingFile v.s. name collision
                // Import aliases are file-scoped. `aliasingNamesByFile` could be faster
                (file as? KSFileImpl)?.file?.importDirectives?.forEach {
                    it.aliasName?.let { aliasingNames.add(it) }
                }
            }

            override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
                val qualifiedName = classDeclaration.qualifiedName
                if (qualifiedName != null) {
                    nameToKSMap[qualifiedName] = classDeclaration
                }
                classDeclaration.declarations.forEach { it.accept(this, data) }
            }

            override fun visitTypeAlias(typeAlias: KSTypeAlias, data: Unit) {
                typeAlias.qualifiedName?.asString()?.let { fqn ->
                    aliasingFqNs[fqn] = typeAlias
                    aliasingNames.add(fqn.substringAfterLast('.'))
                }
            }
        }

        // FIXME: reuse results from previous rounds and only loop through newKSFiles.
        allKSFiles.forEach { it.accept(visitor, Unit) }
    }

    // Mitigation for processors with memory leaks
    // https://github.com/google/ksp/issues/1063
    // https://github.com/google/ksp/issues/1653
    fun tearDown() {
        KSObjectCacheManager.clear()
        com.google.devtools.ksp.common.KSObjectCacheManager.clear()
        instance = null
    }

    override fun getNewFiles(): Sequence<KSFile> {
        return newKSFiles.asSequence()
    }

    override fun getAllFiles(): Sequence<KSFile> {
        return allKSFiles.asSequence()
    }

    override fun getClassDeclarationByName(name: KSName): KSClassDeclaration? {
        nameToKSMap[name]?.let { return it }

        return module.resolveClassByFqName(FqName(name.asString()), NoLookupLocation.WHEN_FIND_BY_FQNAME)
            ?.let {
                val psi = it.findPsi()
                if (psi != null) {
                    when (psi) {
                        is KtClassOrObject -> KSClassDeclarationImpl.getCached(psi)
                        is PsiClass -> KSClassDeclarationJavaImpl.getCached(psi)
                        else -> throw IllegalStateException("unexpected psi: ${psi.javaClass}")
                    }
                } else {
                    KSClassDeclarationDescriptorImpl.getCached(it)
                }
            }
    }

    override fun getFunctionDeclarationsByName(
        name: KSName,
        includeTopLevel: Boolean,
    ): Sequence<KSFunctionDeclaration> {
        val qualifier = name.getQualifier()
        val functionName = name.getShortName()
        val nonTopLevelResult = this.getClassDeclarationByName(qualifier)?.getDeclaredFunctions()
            ?.filter { it.simpleName.asString() == functionName }?.asSequence() ?: emptySequence()
        return if (!includeTopLevel) nonTopLevelResult else {
            nonTopLevelResult.plus(
                module.getPackage(FqName(qualifier))
                    .memberScope.getContributedDescriptors(DescriptorKindFilter.FUNCTIONS) {
                        it.asString() == functionName
                    }
                    .filterIsInstance<MemberDescriptor>().mapNotNull { it.toKSDeclaration() as? KSFunctionDeclaration }
            )
        }
    }

    override fun getPropertyDeclarationByName(name: KSName, includeTopLevel: Boolean): KSPropertyDeclaration? {
        val qualifier = name.getQualifier()
        val propertyName = name.getShortName()
        val nonTopLevelResult = this.getClassDeclarationByName(qualifier)?.getDeclaredProperties()
            ?.singleOrNull { it.simpleName.asString() == propertyName }
        return if (!includeTopLevel) nonTopLevelResult else {
            val topLevelResult = (
                module.getPackage(FqName(qualifier))
                    .memberScope.getContributedDescriptors(DescriptorKindFilter.VARIABLES) {
                        it.asString() == propertyName
                    }
                    .also {
                        if (it.size > 1) {
                            throw IllegalStateException("Found multiple properties with same qualified name")
                        }
                    }
                    .singleOrNull() as? MemberDescriptor
                )?.toKSDeclaration() as? KSPropertyDeclaration
            if (topLevelResult != null && nonTopLevelResult != null) {
                throw IllegalStateException("Found multiple properties with same qualified name")
            }
            nonTopLevelResult ?: topLevelResult
        }
    }

    internal fun checkAnnotation(annotation: KSAnnotation, ksName: KSName, shortName: String): Boolean {
        val annotationType = annotation.annotationType
        val referencedName = (annotationType.element as? KSClassifierReference)?.referencedName()
        val simpleName = referencedName?.substringAfterLast('.')
        return (simpleName == shortName || simpleName in aliasingNames) &&
            annotationType.resolveToUnderlying().declaration.qualifiedName == ksName
    }

    override fun getSymbolsWithAnnotation(annotationName: String, inDepth: Boolean): Sequence<KSAnnotated> {
        // If annotationName is a typealias, resolve to underlying type.
        val realAnnotationName =
            aliasingFqNs[annotationName]?.type?.resolveToUnderlying()?.declaration?.qualifiedName?.asString()
                ?: annotationName

        val ksName = KSNameImpl.getCached(realAnnotationName)
        val shortName = ksName.getShortName()

        fun checkAnnotated(annotated: KSAnnotated): Boolean {
            return annotated.annotations.any {
                checkAnnotation(it, ksName, shortName)
            }
        }

        val allAnnotated = if (inDepth) newAnnotatedSymbolsWithLocals else newAnnotatedSymbols
        return allAnnotated.asSequence().filter(::checkAnnotated)
    }

    private fun collectAnnotatedSymbols(inDepth: Boolean): Collection<KSAnnotated> {
        val visitor = CollectAnnotatedSymbolsVisitor(inDepth)

        for (file in newKSFiles) {
            file.accept(visitor, Unit)
        }

        return visitor.symbols
    }

    private val deferredSymbolsUpdated: Collection<KSAnnotated> by lazy {
        deferredSymbols.values.flatten().mapNotNull { it.getInstanceForCurrentRound() }
    }

    private val newAnnotatedSymbols: Collection<KSAnnotated> by lazy {
        collectAnnotatedSymbols(false) + deferredSymbolsUpdated
    }

    private val newAnnotatedSymbolsWithLocals: Collection<KSAnnotated> by lazy {
        collectAnnotatedSymbols(true) + deferredSymbolsUpdated
    }

    override fun getKSNameFromString(name: String): KSName {
        return KSNameImpl.getCached(name)
    }

    override fun createKSTypeReferenceFromKSType(type: KSType): KSTypeReference {
        return KSTypeReferenceSyntheticImpl.getCached(type, null)
    }

    @KspExperimental
    override fun mapToJvmSignature(declaration: KSDeclaration): String? = mapToJvmSignatureInternal(declaration)

    internal fun mapToJvmSignatureInternal(declaration: KSDeclaration): String? = when (declaration) {
        is KSClassDeclaration -> resolveClassDeclaration(declaration)?.let {
            typeMapper.mapType(it.defaultType).descriptor
        }
        is KSFunctionDeclaration -> resolveFunctionDeclaration(declaration)?.let {
            when (it) {
                is FunctionDescriptor -> typeMapper.mapAsmMethod(it).descriptor
                is PropertyDescriptor -> typeMapper.mapPropertySignature(it)
                else -> throw IllegalStateException("Unexpected descriptor type for declaration: $declaration")
            }
        }
        is KSPropertyDeclaration -> resolvePropertyDeclaration(declaration)?.let {
            typeMapper.mapPropertySignature(it)
        }
        else -> null
    }

    private fun KotlinTypeMapper.mapPropertySignature(descriptor: PropertyDescriptor): String? {
        val sw = BothSignatureWriter(BothSignatureWriter.Mode.TYPE)
        writeFieldSignature(descriptor.type, descriptor, sw)
        return sw.makeJavaGenericSignature() ?: mapType(descriptor.type).descriptor
    }

    override fun overrides(overrider: KSDeclaration, overridee: KSDeclaration): Boolean {
        fun resolveForOverride(declaration: KSDeclaration): DeclarationDescriptor? {
            return when (declaration) {
                is KSPropertyDeclaration -> resolvePropertyDeclaration(declaration)
                is KSFunctionDeclarationJavaImpl -> resolveJavaDeclaration(declaration.psi)
                is KSFunctionDeclaration -> resolveFunctionDeclaration(declaration)
                else -> null
            }
        }

        if (!overridee.isOpen())
            return false
        if (!overridee.isVisibleFrom(overrider))
            return false
        if (!(
            overridee is KSFunctionDeclaration || overrider is KSFunctionDeclaration ||
                (overridee is KSPropertyDeclaration && overrider is KSPropertyDeclaration)
            )
        )
            return false

        if (overrider is KSPropertyDeclarationJavaImpl)
            return false

        val superDescriptor = resolveForOverride(overridee) as? CallableMemberDescriptor ?: return false
        val subDescriptor = resolveForOverride(overrider) as? CallableMemberDescriptor ?: return false
        val subClassDescriptor = overrider.closestClassDeclaration()?.let {
            resolveClassDeclaration(it)
        } ?: return false
        val superClassDescriptor = overridee.closestClassDeclaration()?.let {
            resolveClassDeclaration(it)
        } ?: return false

        incrementalContext.recordLookupWithSupertypes(subClassDescriptor.defaultType)

        val typeOverride = subClassDescriptor.getAllSuperClassifiers()
            .filter { it != subClassDescriptor } // exclude subclass itself as it cannot override its own methods
            .any {
                it == superClassDescriptor
            }
        if (!typeOverride) return false

        incrementalContext.recordLookupForDeclaration(overrider)
        incrementalContext.recordLookupForDeclaration(overridee)

        return OverridingUtil.overrides(subDescriptor, superDescriptor, true, true)
    }

    // check if the candidate is overridden from the original declaration.
    private fun isOriginal(original: KSDeclaration, candidate: KSDeclaration): Boolean {
        incrementalContext.recordLookupForDeclaration(original)
        incrementalContext.recordLookupForDeclaration(candidate)
        val originalDescriptor = when (original) {
            is KSPropertyDeclaration -> resolvePropertyDeclaration(original)
            is KSFunctionDeclaration ->
                (resolveFunctionDeclaration(original) as? FunctionDescriptor)?.propertyIfAccessor
            else -> return false
        }

        val candidateDescriptors = when (candidate) {
            is KSPropertyDeclaration -> resolvePropertyDeclaration(candidate)?.overriddenDescriptors
            is KSFunctionDeclaration -> resolveFunctionDeclaration(candidate)?.overriddenDescriptors
            else -> return false
        }
        return candidateDescriptors?.any { it == originalDescriptor } ?: false
    }

    override fun overrides(
        overrider: KSDeclaration,
        overridee: KSDeclaration,
        containingClass: KSClassDeclaration
    ): Boolean {
        incrementalContext.recordLookupForDeclaration(containingClass)
        return when (overrider) {
            is KSPropertyDeclaration -> containingClass.getAllProperties().singleOrNull {
                it.simpleName.asString() == overrider.simpleName.asString() && isOriginal(overrider, it)
            }?.let { overrides(it, overridee) } ?: false
            is KSFunctionDeclaration -> {
                val candidates = containingClass.getAllFunctions().filter {
                    it.simpleName.asString() == overridee.simpleName.asString()
                }
                if (overrider.simpleName.asString().startsWith("get") ||
                    overrider.simpleName.asString().startsWith("set")
                ) {
                    candidates.plus(
                        containingClass.getAllProperties().filter {
                            val overriderName = overrider.simpleName.asString().substring(3)
                                .replaceFirstChar { it.lowercase() }
                            it.simpleName.asString() == overriderName ||
                                it.simpleName.asString().replaceFirstChar { it.lowercase() } == overriderName
                        }
                        // TODO: It is currently not possible to do the overridden descriptor optimization for java overrides.
                    ).any { overrides(it, overridee) }
                } else {
                    candidates.singleOrNull { isOriginal(overrider, it) }?.let { overrides(it, overridee) } ?: false
                }
            }
            else -> false
        }
    }

    fun evaluateConstant(expression: KtExpression?, expectedType: KotlinType): ConstantValue<*>? {
        return expression?.let {
            if (it is KtClassLiteralExpression && it.receiverExpression != null) {
                val parent = KtStubbedPsiUtil.getPsiOrStubParent(it, KtPrimaryConstructor::class.java, false)
                val scope = resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(parent!!)
                val result = qualifiedExpressionResolver
                    .resolveDescriptorForDoubleColonLHS(it.receiverExpression!!, scope, bindingTrace, false)
                val classifier = result.classifierDescriptor ?: return null
                val typeResolutionContext = TypeResolutionContext(scope, bindingTrace, true, true, false)
                val possiblyBareType = resolveSession.typeResolver
                    .resolveTypeForClassifier(typeResolutionContext, classifier, result, it, Annotations.EMPTY)
                var actualType = if (possiblyBareType.isBare)
                    possiblyBareType.bareTypeConstructor.declarationDescriptor!!.defaultType
                else possiblyBareType.actualType
                var arrayDimension = 0
                while (KotlinBuiltIns.isArray(actualType)) {
                    actualType = actualType.arguments.single().type
                    arrayDimension += 1
                }
                KClassValue(actualType.constructor.declarationDescriptor.classId!!, arrayDimension)
            } else {
                constantExpressionEvaluator.evaluateExpression(it, bindingTrace)?.toConstantValue(expectedType) ?: run {
                    val parent = KtStubbedPsiUtil
                        .getPsiOrStubParent(expression, KtPrimaryConstructor::class.java, false)
                    val scope = resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(parent!!)
                    qualifiedExpressionResolver
                        .resolvePackageHeader(expression.containingKtFile.packageDirective!!, module, bindingTrace)
                    bodyResolver.resolveConstructorParameterDefaultValues(
                        topDownAnalysisContext.outerDataFlowInfo, bindingTrace,
                        parent, (scope.ownerDescriptor as ClassDescriptor).constructors.first(), scope,
                        resolveSession.inferenceSession
                    )
                    constantExpressionEvaluator.evaluateExpression(it, bindingTrace)?.toConstantValue(expectedType)
                }
            }
        }
    }

    fun resolveDeclaration(declaration: KtDeclaration): DeclarationDescriptor? {
        return if (KtPsiUtil.isLocal(declaration)) {
            resolveDeclarationForLocal(declaration)
            bindingTrace.bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, declaration)
        } else {
            resolveSession.resolveToDescriptor(declaration)
        }
    }

    private val synthesizedPropPrefix = mapOf<String, (PropertyDescriptor?) -> PropertyAccessorDescriptor?>(
        "set" to { it?.setter },
        "get" to { it?.getter },
        "is" to { it?.getter },
    )

    // TODO: Resolve Java variables is not supported by this function. Not needed currently.
    fun resolveJavaDeclaration(psi: PsiElement): DeclarationDescriptor? {
        return when (psi) {
            is PsiClass -> moduleClassResolver.resolveClass(JavaClassImpl(psi))
            is PsiMethod -> {
                val property = synthesizedPropPrefix.keys.firstOrNull {
                    psi.name.startsWith(it) && psi.name.length > it.length && psi.name[it.length].isUpperCase()
                }?.let { prefix ->
                    val propName = psi.name.substring(prefix.length).replaceFirstChar(Char::lowercaseChar)
                    moduleClassResolver.resolveContainingClass(psi)?.findEnclosedDescriptor(
                        kindFilter = DescriptorKindFilter.VARIABLES,
                        name = propName
                    ) {
                        synthesizedPropPrefix[prefix]!!(it as? PropertyDescriptor)?.correspondsTo(psi) == true
                    }
                }
                property ?: moduleClassResolver
                    .resolveContainingClass(psi)?.let { containingClass ->
                        val filter = if (psi is SyntheticElement) {
                            { declaration: DeclarationDescriptor -> declaration.name.asString() == psi.name }
                        } else {
                            { declaration: DeclarationDescriptor -> declaration.correspondsTo(psi) }
                        }
                        containingClass.findEnclosedDescriptor(
                            kindFilter = DescriptorKindFilter.FUNCTIONS,
                            name = if (psi.name == containingClass.name.asString()) "<init>" else psi.name,
                            filter = filter
                        )
                    }
            }
            is PsiField -> {
                moduleClassResolver
                    .resolveClass(JavaFieldImpl(psi).containingClass)
                    ?.findEnclosedDescriptor(
                        kindFilter = DescriptorKindFilter.VARIABLES,
                        name = psi.name,
                        filter = { it.correspondsTo(psi) }
                    )
            }
            else -> throw IllegalStateException("unhandled psi element kind: ${psi.javaClass}")
        }
    }

    fun resolveClassDeclaration(classDeclaration: KSClassDeclaration): ClassDescriptor? {
        return when (classDeclaration) {
            is KSClassDeclarationImpl -> resolveDeclaration(classDeclaration.ktClassOrObject)
            is KSClassDeclarationDescriptorImpl -> classDeclaration.descriptor
            is KSClassDeclarationJavaImpl -> resolveJavaDeclaration(classDeclaration.psi)
            is KSClassDeclarationJavaEnumEntryImpl -> resolveJavaDeclaration(classDeclaration.psi)
            else -> throw IllegalStateException("unexpected class: ${classDeclaration.javaClass}")
        } as ClassDescriptor?
    }

    fun resolveFunctionDeclaration(function: KSFunctionDeclaration): CallableDescriptor? {
        return when (function) {
            is KSFunctionDeclarationImpl -> resolveDeclaration(function.ktFunction)
            is KSFunctionDeclarationDescriptorImpl -> function.descriptor
            is KSFunctionDeclarationJavaImpl -> {
                val descriptor = resolveJavaDeclaration(function.psi)
                if (descriptor is JavaForKotlinOverridePropertyDescriptor) {
                    if (function.simpleName.asString().startsWith("set")) {
                        descriptor.setter
                    } else {
                        descriptor.getter
                    }
                } else {
                    descriptor
                }
            }
            is KSConstructorSyntheticImpl -> {
                // we might create synthetic constructor when it is not declared in code
                // it is either for kotlin, where we can use primary constructor, or for java
                // where we can use the only available constructor
                val resolved = resolveClassDeclaration(function.ksClassDeclaration)
                resolved?.unsubstitutedPrimaryConstructor ?: resolved?.constructors?.singleOrNull()
            }
            else -> throw IllegalStateException("unexpected class: ${function.javaClass}")
        } as? CallableDescriptor
    }

    fun resolvePropertyDeclaration(property: KSPropertyDeclaration): PropertyDescriptor? {
        return when (property) {
            is KSPropertyDeclarationImpl -> resolveDeclaration(property.ktProperty)
            is KSPropertyDeclarationParameterImpl -> resolveDeclaration(property.ktParameter)
            is KSPropertyDeclarationDescriptorImpl -> property.descriptor
            is KSPropertyDeclarationJavaImpl -> resolveJavaDeclaration(property.psi)
            else -> throw IllegalStateException("unexpected class: ${property.javaClass}")
        } as PropertyDescriptor?
    }

    fun resolvePropertyAccessorDeclaration(accessor: KSPropertyAccessor): PropertyAccessorDescriptor? {
        return when (accessor) {
            is KSPropertyAccessorDescriptorImpl -> accessor.descriptor
            is KSPropertyAccessorImpl -> resolveDeclaration(accessor.ktPropertyAccessor)
            is KSPropertySetterSyntheticImpl -> resolvePropertyDeclaration(accessor.receiver)?.setter
            is KSPropertyGetterSyntheticImpl -> resolvePropertyDeclaration(accessor.receiver)?.getter
            else -> throw IllegalStateException("unexpected class: ${accessor.javaClass}")
        } as PropertyAccessorDescriptor?
    }

    fun resolveJavaType(psi: PsiType, parentTypeReference: KSTypeReference? = null): KotlinType {
        incrementalContext.recordLookup(psi)
        val javaType = JavaTypeImpl.create(psi)

        var parent: KSNode? = parentTypeReference

        val stack = Stack<KSNode>()
        while (parent != null) {
            if (parent is KSFunctionDeclarationJavaImpl || parent is KSClassDeclarationJavaImpl) {
                stack.push(parent)
            }
            parent = parent.parent
        }
        // Construct resolver context for the PsiType
        var resolverContext = lazyJavaResolverContext

        for (e in stack) {
            when (e) {
                is KSFunctionDeclarationJavaImpl -> {
                    // Non-physical methods have no interesting scope and may have no containing class
                    if (!e.psi.isPhysical || e.psi.containingClass == null)
                        continue
                    resolverContext = resolverContext
                        .childForMethod(
                            resolveJavaDeclaration(e.psi)!!,
                            if (e.psi.isConstructor) JavaConstructorImpl(e.psi) else JavaMethodImpl(e.psi)
                        )
                }
                is KSClassDeclarationJavaImpl -> {
                    resolverContext = resolverContext
                        .childForClassOrPackage(resolveJavaDeclaration(e.psi) as ClassDescriptor, JavaClassImpl(e.psi))
                }
                is KSClassDeclaration, is KSFunctionDeclaration, is KSPropertyDeclaration, is KSTypeAlias,
                is KSTypeParameter, is KSFile, is KSPropertyGetter, is KSPropertySetter, is KSTypeArgument,
                is KSTypeReference, is KSValueArgument, is KSValueParameter, is KSAnnotation, is KSCallableReference,
                is KSClassifierReference, is KSDefNonNullReference, is KSDynamicReference, is KSParenthesizedReference,
                -> Unit
            }
        }
        return if (javaType is JavaArrayTypeImpl)
            resolverContext
                .typeResolver.transformArrayType(javaType, TypeUsage.COMMON.toAttributes(), psi is PsiEllipsisType)
        else
            resolverContext.typeResolver.transformJavaType(javaType, TypeUsage.COMMON.toAttributes())
    }

    /*
     * Don't map Java types in annotation parameters
     *
     * Users may specify Java types explicitly by instances of `Class<T>`.
     * The situation is similar to `getClassDeclarationByName` where we have
     * decided to keep those Java types not mapped.
     *
     * It would be troublesome if users try to use reflection on types that
     * were mapped to Kotlin builtins, becuase some of those builtins don't
     * even exist in classpath.
     *
     * Therefore, ResolverImpl.resolveJavaType cannot be used.
     */
    fun resolveJavaTypeInAnnotations(psiType: PsiType): KSType = if (options.mapAnnotationArgumentsInJava) {
        getKSTypeCached(resolveJavaType(psiType))
    } else {
        when (psiType) {
            is PsiPrimitiveType -> {
                getClassDeclarationByName(psiType.boxedTypeName!!)!!.asStarProjectedType()
            }
            is PsiArrayType -> {
                val componentType = resolveJavaTypeInAnnotations(psiType.componentType)
                val componentTypeRef = createKSTypeReferenceFromKSType(componentType)
                val typeArgs = listOf(getTypeArgument(componentTypeRef, Variance.INVARIANT))
                builtIns.arrayType.replace(typeArgs)
            }
            else -> {
                getClassDeclarationByName(psiType.canonicalText)?.asStarProjectedType()
                    ?: KSErrorType(psiType.canonicalText)
            }
        }
    }

    fun KotlinType.expandNonRecursively(): KotlinType =
        (constructor.declarationDescriptor as? TypeAliasDescriptor)?.expandedType?.withAbbreviation(this as SimpleType)
            ?: this

    fun TypeProjection.expand(): TypeProjection {
        val expandedType = type.expand()
        return if (expandedType == type) this else substitute { expandedType }
    }

    // TODO: Is this the most efficient way?
    fun KotlinType.expand(): KotlinType =
        replace(arguments.map { it.expand() }).expandNonRecursively()

    fun KtTypeReference.lookup(): KotlinType? =
        bindingTrace.get(BindingContext.ABBREVIATED_TYPE, this)?.expand() ?: bindingTrace.get(BindingContext.TYPE, this)

    fun resolveUserType(type: KSTypeReference): KSType {
        when (type) {
            is KSTypeReferenceImpl -> {
                val typeReference = type.ktTypeReference
                typeReference.lookup()?.let {
                    return getKSTypeCached(it, type.element.typeArguments)
                }
                KtStubbedPsiUtil.getContainingDeclaration(typeReference)?.let { containingDeclaration ->
                    resolveDeclaration(containingDeclaration)?.let {
                        // TODO: only resolve relevant branch.
                        ForceResolveUtil.forceResolveAllContents(it)
                    }
                    // TODO: Fix resolution look up to avoid fallback to file scope.
                    typeReference.lookup()?.let {
                        return getKSTypeCached(it, type.element.typeArguments)
                    }
                }
                val scope = typeReference.findLexicalScope()
                return resolveSession.typeResolver.resolveType(scope, typeReference, bindingTrace, false).let {
                    getKSTypeCached(it, type.element.typeArguments)
                }
            }
            is KSTypeReferenceDescriptorImpl -> {
                return getKSTypeCached(type.kotlinType)
            }
            is KSTypeReferenceJavaImpl -> {
                val psi = (type.psi as? PsiClassReferenceType)?.resolve()
                if (psi is PsiTypeParameter) {
                    (type.psi as PsiClassReferenceType).typeArguments().forEach {
                        if (it is PsiType) {
                            incrementalContext.recordLookup(it)
                        }
                    }
                    val containingDeclaration = if (psi.owner is PsiClass) {
                        moduleClassResolver.resolveClass(JavaClassImpl(psi.owner as PsiClass))
                    } else {
                        val owner = psi.owner
                        check(owner is PsiMethod) {
                            "unexpected owner type: $owner / ${owner?.javaClass}"
                        }
                        moduleClassResolver.resolveContainingClass(owner)
                            ?.findEnclosedDescriptor(
                                kindFilter = DescriptorKindFilter.FUNCTIONS,
                                name = owner.name,
                                filter = { it.correspondsTo(owner) }
                            ) as FunctionDescriptor
                    } as DeclarationDescriptor
                    val typeParameterDescriptor = LazyJavaTypeParameterDescriptor(
                        lazyJavaResolverContext,
                        JavaTypeParameterImpl(psi),
                        psi.index,
                        containingDeclaration
                    )
                    javaTypeParameterMap[typeParameterDescriptor] = psi
                }
                val resolved = getKSTypeCached(
                    resolveJavaType(type.psi, type),
                    type.element.typeArguments
                )
                return if (type.psi is PsiArrayType) {
                    resolved
                } else {
                    // Replacing with error argument results in error type, fall back to original logic.
                    resolved.replace(type.element.typeArguments).let {
                        if (it.isError) resolved else it
                    }
                }
            }
            else -> throw IllegalStateException("Unable to resolve type for $type, $ExceptionMessage")
        }
    }

    fun findDeclaration(kotlinType: KotlinType): KSDeclaration {
        val descriptor = kotlinType.constructor.declarationDescriptor
        val psi = descriptor?.findPsi()
        return if (psi != null) {
            when (psi) {
                is KtClassOrObject -> KSClassDeclarationImpl.getCached(psi)
                is PsiClass -> KSClassDeclarationJavaImpl.getCached(psi)
                is KtTypeAlias -> KSTypeAliasImpl.getCached(psi)
                is KtTypeParameter -> KSTypeParameterImpl.getCached(psi)
                is PsiEnumConstant -> KSClassDeclarationJavaEnumEntryImpl.getCached(psi)
                else -> throw IllegalStateException("Unexpected psi type: ${psi.javaClass}, $ExceptionMessage")
            }
        } else {
            when (descriptor) {
                is ClassDescriptor -> KSClassDeclarationDescriptorImpl.getCached(descriptor)
                // LazyJavaTypeParameterDescriptor has `source` overridden to `NO_SOURCE`, therefore
                // need to look up psi within KSP.
                is TypeParameterDescriptor -> if (descriptor in javaTypeParameterMap) {
                    KSTypeParameterJavaImpl.getCached(javaTypeParameterMap[descriptor]!!)
                } else {
                    KSTypeParameterDescriptorImpl.getCached(descriptor)
                }
                is TypeAliasDescriptor -> KSTypeAliasDescriptorImpl.getCached(descriptor)
                null -> throw IllegalStateException("Failed to resolve descriptor for $kotlinType")
                else -> throw IllegalStateException(
                    "Unexpected descriptor type: ${descriptor.javaClass}, $ExceptionMessage"
                )
            }
        }
    }

    // Finds closest non-local scope.
    fun KtElement.findLexicalScope(): LexicalScope {
        val ktDeclaration = KtStubbedPsiUtil.getPsiOrStubParent(this, KtDeclaration::class.java, false)
            ?: return resolveSession.fileScopeProvider.getFileResolutionScope(this.containingKtFile)
        var parentDeclaration = KtStubbedPsiUtil.getContainingDeclaration(ktDeclaration)

        if (ktDeclaration is KtPropertyAccessor && parentDeclaration != null) {
            parentDeclaration = KtStubbedPsiUtil.getContainingDeclaration(
                parentDeclaration,
                KtDeclaration::class.java
            )
        }
        if (parentDeclaration == null) {
            return resolveSession.fileScopeProvider.getFileResolutionScope(this.containingKtFile)
        }
        return if (parentDeclaration is KtClassOrObject) {
            resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(this)
        } else {
            containingNonLocalDeclaration()?.let {
                resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(it)
            } ?: resolveSession.fileScopeProvider.getFileResolutionScope(this.containingKtFile)
        }
    }

    fun resolveAnnotationEntry(ktAnnotationEntry: KtAnnotationEntry): AnnotationDescriptor? {
        bindingTrace.get(BindingContext.ANNOTATION, ktAnnotationEntry)?.let { return it }
        KtStubbedPsiUtil.getContainingDeclaration(ktAnnotationEntry)?.let { containingDeclaration ->
            if (KtPsiUtil.isLocal(containingDeclaration)) {
                resolveDeclarationForLocal(containingDeclaration)
            } else {
                resolveSession.resolveToDescriptor(containingDeclaration).annotations.forEach {}
            }
        } ?: ktAnnotationEntry.containingKtFile.let {
            resolveSession.getFileAnnotations(it).forEach {}
        }
        return bindingTrace.get(BindingContext.ANNOTATION, ktAnnotationEntry)
    }

    fun resolveDeclarationForLocal(localDeclaration: KtDeclaration) {
        var declaration = KtStubbedPsiUtil.getContainingDeclaration(localDeclaration) ?: return
        while (KtPsiUtil.isLocal(declaration))
            declaration = KtStubbedPsiUtil.getContainingDeclaration(declaration)!!

        val containingFD = resolveSession.resolveToDescriptor(declaration).also {
            ForceResolveUtil.forceResolveAllContents(it)
        }

        if (declaration is KtNamedFunction) {
            val dataFlowInfo = DataFlowInfo.EMPTY
            val scope = resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(declaration)
            bodyResolver.resolveFunctionBody(
                dataFlowInfo,
                bindingTrace,
                declaration,
                containingFD as FunctionDescriptor,
                scope,
                null
            )
        }
    }

    @KspExperimental
    override fun getJvmName(accessor: KSPropertyAccessor): String? {
        return resolvePropertyAccessorDeclaration(accessor)?.let(typeMapper::mapFunctionName)
    }

    @KspExperimental
    override fun getJvmName(declaration: KSFunctionDeclaration): String? {
        // function names might be mangled if they receive inline class parameters or they are internal
        return (resolveFunctionDeclaration(declaration) as? FunctionDescriptor)?.let(typeMapper::mapFunctionName)
    }

    @KspExperimental
    override fun getOwnerJvmClassName(declaration: KSPropertyDeclaration): String? {
        val descriptor = resolvePropertyDeclaration(declaration) ?: return null
        return getJvmOwnerQualifiedName(descriptor)
    }

    @KspExperimental
    override fun getOwnerJvmClassName(declaration: KSFunctionDeclaration): String? {
        val descriptor = resolveFunctionDeclaration(declaration) ?: return null
        return getJvmOwnerQualifiedName(descriptor)
    }

    private fun getJvmOwnerQualifiedName(descriptor: DeclarationDescriptor): String? {
        return typeMapper.mapJvmImplementationOwner(descriptor)?.className
    }

    private fun KotlinTypeMapper.mapJvmImplementationOwner(descriptor: DeclarationDescriptor): Type? {
        if (descriptor is ConstructorDescriptor) {
            return mapClass(descriptor.constructedClass)
        }

        return when (val container = descriptor.containingDeclaration) {
            is PackageFragmentDescriptor ->
                internalNameForPackageMemberOwner(descriptor as CallableMemberDescriptor)?.let(Type::getObjectType)
            is ClassDescriptor ->
                mapClass(container)
            else -> null
        }
    }

    private fun internalNameForPackageMemberOwner(descriptor: CallableMemberDescriptor): String? {
        val file = DescriptorToSourceUtils.getContainingFile(descriptor)
        if (file != null) {
            return JvmFileClassUtil.getFileClassInternalName(file)
        }

        val directMember = DescriptorUtils.getDirectMember(descriptor)
        if (directMember is DescriptorWithContainerSource) {
            return directMember.getImplClassNameForDeserialized()?.internalName
        }

        return null
    }

    // TODO: refactor and reuse BinaryClassInfoCache
    @KspExperimental
    override fun getJvmCheckedException(function: KSFunctionDeclaration): Sequence<KSType> {
        return when (function.origin) {
            Origin.JAVA -> {
                val psi = (function as KSFunctionDeclarationJavaImpl).psi
                psi.throwsList.referencedTypes.asSequence()
                    .map { KSTypeReferenceJavaImpl.getCached(it, function).resolve() }
            }
            Origin.KOTLIN -> {
                extractThrowsAnnotation(function)
            }
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                val descriptor = (function as KSFunctionDeclarationDescriptorImpl).descriptor
                val jvmDesc = this.mapToJvmSignature(function)
                val virtualFileContent = if (function.origin == Origin.KOTLIN_LIB) {
                    (descriptor.getContainingKotlinJvmBinaryClass() as? VirtualFileKotlinClass)?.file
                        ?.contentsToByteArray()
                } else {
                    (
                        ((descriptor.source as? JavaSourceElement)?.javaElement as? BinaryJavaMethodBase)
                            ?.containingClass as? BinaryJavaClass
                        )?.virtualFile?.contentsToByteArray()
                }
                if (virtualFileContent == null) {
                    return emptySequence()
                }
                extractThrowsFromClassFile(virtualFileContent, jvmDesc, function.simpleName.asString())
            }
            else -> emptySequence()
        }
    }

    @KspExperimental
    override fun getJvmCheckedException(accessor: KSPropertyAccessor): Sequence<KSType> {
        return when (accessor.origin) {
            Origin.KOTLIN, Origin.SYNTHETIC -> {
                extractThrowsAnnotation(accessor)
            }
            Origin.KOTLIN_LIB -> {
                val descriptor = (accessor as KSPropertyAccessorDescriptorImpl).descriptor
                val jvmDesc = typeMapper.mapAsmMethod(descriptor).descriptor
                val virtualFileContent = if (accessor.origin == Origin.KOTLIN_LIB) {
                    (descriptor.correspondingProperty.getContainingKotlinJvmBinaryClass() as? VirtualFileKotlinClass)
                        ?.file?.contentsToByteArray()
                } else {
                    (
                        ((descriptor.source as? JavaSourceElement)?.javaElement as? BinaryJavaMethod)?.containingClass
                            as? BinaryJavaClass
                        )?.virtualFile?.contentsToByteArray()
                }
                if (virtualFileContent == null) {
                    return emptySequence()
                }
                extractThrowsFromClassFile(virtualFileContent, jvmDesc, getJvmName(accessor))
            }
            else -> emptySequence()
        }
    }

    private val javaPackageToClassMap: Map<String, List<KSDeclaration>> by lazy {
        val packageToClassMapping = mutableMapOf<String, List<KSDeclaration>>()
        allKSFiles
            .filter { file ->
                file.origin == Origin.JAVA &&
                    options.javaSourceRoots.any { root ->
                        file.filePath.startsWith(root.absolutePath) &&
                            file.filePath.substringAfter(root.absolutePath)
                            .dropLastWhile { c -> c != File.separatorChar }.dropLast(1).drop(1)
                            .replace(File.separatorChar, '.') == file.packageName.asString()
                    }
            }
            .forEach {
                packageToClassMapping.put(
                    it.packageName.asString(),
                    packageToClassMapping.getOrDefault(it.packageName.asString(), emptyList())
                        .plus(
                            it.declarations.filterNot {
                                it.containingFile?.fileName?.split(".")?.first() == it.simpleName.asString()
                            }
                        )
                )
            }
        packageToClassMapping
    }

    @KspExperimental
    override fun getDeclarationsFromPackage(packageName: String): Sequence<KSDeclaration> {
        val noPackageFilter = DescriptorKindFilter.ALL.withoutKinds(DescriptorKindFilter.PACKAGES_MASK)
        return module.getPackage(FqName(packageName))
            .memberScope.getContributedDescriptors(noPackageFilter)
            .asSequence()
            .mapNotNull { (it as? MemberDescriptor)?.toKSDeclaration() }
            .plus(javaPackageToClassMap.getOrDefault(packageName, emptyList()).asSequence())
    }

    override fun getTypeArgument(typeRef: KSTypeReference, variance: Variance): KSTypeArgument {
        return KSTypeArgumentLiteImpl.getCached(typeRef, variance)
    }

    internal fun asMemberOf(
        property: KSPropertyDeclaration,
        containing: KSType,
    ): KSType {
        val key = property to containing
        return propertyAsMemberOfCache.getOrPut(key) {
            computeAsMemberOf(property, containing)
        }
    }

    private fun computeAsMemberOf(
        property: KSPropertyDeclaration,
        containing: KSType,
    ): KSType {
        val propertyDeclaredIn = property.closestClassDeclaration()
            ?: throw IllegalArgumentException(
                "Cannot call asMemberOf with a property that is " +
                    "not declared in a class or an interface"
            )
        val declaration = resolvePropertyDeclaration(property)
        if (declaration != null && containing is KSTypeImpl && !containing.isError) {
            incrementalContext.recordLookupWithSupertypes(containing.kotlinType)
            incrementalContext.recordLookupForDeclaration(property)
            if (!containing.kotlinType.isSubtypeOf(propertyDeclaredIn)) {
                throw IllegalArgumentException(
                    "$containing is not a sub type of the class/interface that contains `$property` " +
                        "($propertyDeclaredIn)"
                )
            }
            val typeSubstitutor = containing.kotlinType.createTypeSubstitutor()
            val substituted = declaration.substitute(typeSubstitutor) as? ValueDescriptor
            substituted?.let {
                return getKSTypeCached(substituted.type)
            }
        }
        // if substitution fails, fallback to the type from the property
        return KSErrorType.fromReferenceBestEffort(property.type)
    }

    internal fun asMemberOf(
        function: KSFunctionDeclaration,
        containing: KSType,
    ): KSFunction {
        val key = function to containing
        return functionAsMemberOfCache.getOrPut(key) {
            computeAsMemberOf(function, containing)
        }
    }

    private fun computeAsMemberOf(
        function: KSFunctionDeclaration,
        containing: KSType,
    ): KSFunction {
        val functionDeclaredIn = function.closestClassDeclaration()
            ?: throw IllegalArgumentException(
                "Cannot call asMemberOf with a function that is " +
                    "not declared in a class or an interface"
            )
        val declaration = resolveFunctionDeclaration(function)
        if (declaration != null && containing is KSTypeImpl && !containing.isError) {
            incrementalContext.recordLookupWithSupertypes(containing.kotlinType)
            incrementalContext.recordLookupForDeclaration(function)
            if (!containing.kotlinType.isSubtypeOf(functionDeclaredIn)) {
                throw IllegalArgumentException(
                    "$containing is not a sub type of the class/interface that contains " +
                        "`$function` ($functionDeclaredIn)"
                )
            }
            val typeSubstitutor = containing.kotlinType.createTypeSubstitutor()
            if (declaration is PropertyAccessorDescriptor) {
                val substitutedProperty = (declaration.correspondingProperty).substitute(typeSubstitutor)
                // TODO: Fix in upstream for property accessors: https://github.com/JetBrains/kotlin/blob/master/core/descriptors/src/org/jetbrains/kotlin/descriptors/impl/PropertyAccessorDescriptorImpl.java#L122
                return KSFunctionImpl(
                    (substitutedProperty as PropertyDescriptor).accessors.single {
                        it.name == declaration.name
                    }
                )
            }
            val substituted = declaration.substitute(typeSubstitutor)
            return KSFunctionImpl(substituted)
        }
        // if substitution fails, return an error function that resembles the original declaration
        return KSFunctionErrorImpl(function)
    }

    private fun KotlinType.isSubtypeOf(declaration: KSClassDeclaration): Boolean {
        val classDeclaration = resolveClassDeclaration(declaration)
        if (classDeclaration == null) {
            throw IllegalArgumentException(
                "Cannot find the declaration for class $classDeclaration"
            )
        }
        return constructor
            .declarationDescriptor
            ?.getAllSuperClassifiers()
            ?.any { it == classDeclaration } == true
    }

    override val builtIns: KSBuiltIns by lazy {
        val builtIns = module.builtIns
        object : KSBuiltIns {
            override val anyType: KSType by lazy { getKSTypeCached(builtIns.anyType) }
            override val nothingType by lazy { getKSTypeCached(builtIns.nothingType) }
            override val unitType: KSType by lazy { getKSTypeCached(builtIns.unitType) }
            override val numberType: KSType by lazy { getKSTypeCached(builtIns.numberType) }
            override val byteType: KSType by lazy { getKSTypeCached(builtIns.byteType) }
            override val shortType: KSType by lazy { getKSTypeCached(builtIns.shortType) }
            override val intType: KSType by lazy { getKSTypeCached(builtIns.intType) }
            override val longType: KSType by lazy { getKSTypeCached(builtIns.longType) }
            override val floatType: KSType by lazy { getKSTypeCached(builtIns.floatType) }
            override val doubleType: KSType by lazy { getKSTypeCached(builtIns.doubleType) }
            override val charType: KSType by lazy { getKSTypeCached(builtIns.charType) }
            override val booleanType: KSType by lazy { getKSTypeCached(builtIns.booleanType) }
            override val stringType: KSType by lazy { getKSTypeCached(builtIns.stringType) }
            override val iterableType: KSType by lazy {
                getKSTypeCached(builtIns.iterableType.replaceArgumentsWithStarProjections())
            }
            override val annotationType: KSType by lazy { getKSTypeCached(builtIns.annotationType) }
            override val arrayType: KSType by lazy {
                getKSTypeCached(builtIns.array.defaultType.replaceArgumentsWithStarProjections())
            }
        }
    }

    internal val mockSerializableType = module.builtIns.numberType.supertypes().singleOrNull {
        it.constructor.declarationDescriptor?.name?.asString() == "Serializable"
    }

    internal val javaSerializableType = module.resolveClassByFqName(
        FqName("java.io.Serializable"), NoLookupLocation.WHEN_FIND_BY_FQNAME
    )?.defaultType

    @KspExperimental
    override fun mapJavaNameToKotlin(javaName: KSName): KSName? =
        JavaToKotlinClassMap.mapJavaToKotlin(FqName(javaName.asString()))?.toKSName()

    @KspExperimental
    override fun mapKotlinNameToJava(kotlinName: KSName): KSName? =
        JavaToKotlinClassMap.mapKotlinToJava(FqNameUnsafe(kotlinName.asString()))?.toKSName()

    @KspExperimental
    internal fun mapToJvmSignature(accessor: KSPropertyAccessor): String {
        return resolvePropertyAccessorDeclaration(accessor)?.let {
            typeMapper.mapAsmMethod(it).descriptor
        } ?: ""
    }

    @KspExperimental
    override fun getDeclarationsInSourceOrder(container: KSDeclarationContainer): Sequence<KSDeclaration> {
        return container.declarationsInSourceOrder
    }

    @KspExperimental
    override fun effectiveJavaModifiers(declaration: KSDeclaration): Set<Modifier> {
        val modifiers = HashSet<Modifier>(declaration.modifiers.filter { it in javaModifiers })

        // This is only needed by sources.
        // PUBLIC, PRIVATE, PROTECTED are already handled in descriptor based impls.
        fun addVisibilityModifiers() {
            when {
                declaration.isPublic() -> modifiers.add(Modifier.PUBLIC)
                declaration.isPrivate() -> modifiers.add(Modifier.PRIVATE)
                declaration.isProtected() -> modifiers.add(Modifier.PROTECTED)
            }
        }

        when (declaration.origin) {
            Origin.JAVA -> {
                addVisibilityModifiers()
                if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.INTERFACE)
                    modifiers.add(Modifier.ABSTRACT)
            }
            Origin.KOTLIN -> {
                addVisibilityModifiers()
                if (!declaration.isOpen())
                    modifiers.add(Modifier.FINAL)
                if (declaration.hasAnnotation(JVM_STATIC_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_STATIC)
                if (declaration.hasAnnotation(JVM_DEFAULT_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_DEFAULT)
                if (declaration.hasAnnotation(JVM_DEFAULT_WITHOUT_COMPATIBILITY_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_DEFAULT)
                if (declaration.hasAnnotation(JVM_STRICTFP_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_STRICT)
                if (declaration.hasAnnotation(JVM_SYNCHRONIZED_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_SYNCHRONIZED)
                if (declaration.hasAnnotation(JVM_TRANSIENT_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_TRANSIENT)
                if (declaration.hasAnnotation(JVM_VOLATILE_ANNOTATION_FQN))
                    modifiers.add(Modifier.JAVA_VOLATILE)
                when (declaration) {
                    is KSClassDeclaration -> {
                        if (declaration.isCompanionObject)
                            modifiers.add(Modifier.JAVA_STATIC)
                        if (declaration.classKind == ClassKind.INTERFACE)
                            modifiers.add(Modifier.ABSTRACT)
                    }
                    is KSPropertyDeclaration -> {
                        if (declaration.isAbstract())
                            modifiers.add(Modifier.ABSTRACT)
                    }
                    is KSFunctionDeclaration -> {
                        if (declaration.isAbstract)
                            modifiers.add(Modifier.ABSTRACT)
                    }
                    is KSTypeAlias, is KSTypeParameter -> Unit
                }
            }
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                when (declaration) {
                    is KSPropertyDeclaration -> {
                        if (declaration.jvmAccessFlag and Opcodes.ACC_TRANSIENT != 0)
                            modifiers.add(Modifier.JAVA_TRANSIENT)
                        if (declaration.jvmAccessFlag and Opcodes.ACC_VOLATILE != 0)
                            modifiers.add(Modifier.JAVA_VOLATILE)
                    }
                    is KSFunctionDeclaration -> {
                        if (declaration.jvmAccessFlag and Opcodes.ACC_STRICT != 0)
                            modifiers.add(Modifier.JAVA_STRICT)
                        if (declaration.jvmAccessFlag and Opcodes.ACC_SYNCHRONIZED != 0)
                            modifiers.add(Modifier.JAVA_SYNCHRONIZED)
                    }
                    is KSClassDeclaration, is KSTypeAlias, is KSTypeParameter -> Unit
                }
            }
            else -> Unit
        }
        return modifiers
    }

    // Convert type arguments for Java wildcard, recursively.
    private fun KotlinType.toWildcard(mode: TypeMappingMode): Result<KotlinType> {
        val parameters = constructor.parameters
        val arguments = arguments

        val wildcardArguments = parameters.zip(arguments).map { (parameter, argument) ->
            if (!argument.isStarProjection &&
                parameter.variance != argument.projectionKind &&
                parameter.variance != org.jetbrains.kotlin.types.Variance.INVARIANT &&
                argument.projectionKind != org.jetbrains.kotlin.types.Variance.INVARIANT
            ) {
                return Result.failure(
                    IllegalArgumentException(
                        "Conflicting variance: variance '${parameter.variance.label}' vs projection " +
                            "'${argument.projectionKind.label}'"
                    )
                )
            }

            val argMode = mode.updateFromAnnotations(argument.type)
            val variance = KotlinTypeMapper.getVarianceForWildcard(parameter, argument, argMode)
            val genericMode = argMode.toGenericArgumentMode(
                getEffectiveVariance(parameter.variance, argument.projectionKind)
            )
            TypeProjectionImpl(variance, argument.type.toWildcard(genericMode).getOrElse { return Result.failure(it) })
        }

        return Result.success(replace(wildcardArguments))
    }

    private val JVM_SUPPRESS_WILDCARDS_NAME = KSNameImpl.getCached("kotlin.jvm.JvmSuppressWildcards")
    private val JVM_SUPPRESS_WILDCARDS_SHORT = "JvmSuppressWildcards"
    private fun KSTypeReference.findJvmSuppressWildcards(): Boolean? {
        var candidate: KSNode? = this

        while (candidate != null) {
            if ((candidate is KSTypeReference || candidate is KSDeclaration)) {
                (
                    (candidate as KSAnnotated).annotations.firstOrNull {
                        checkAnnotation(it, JVM_SUPPRESS_WILDCARDS_NAME, JVM_SUPPRESS_WILDCARDS_SHORT)
                    }?.arguments?.firstOrNull()?.value as? Boolean
                    )?.let {
                    // KSAnnotated.getAnnotationsByType is handy but it uses reflection.
                    return it
                }
            }
            candidate = candidate.parent
        }

        return null
    }

    private fun TypeMappingMode.updateFromAnnotations(
        type: KotlinType
    ): TypeMappingMode {
        (
            type.annotations.findAnnotation(JVM_SUPPRESS_WILDCARDS_ANNOTATION_FQ_NAME)
                ?.argumentValue("suppress")?.value as? Boolean
            )?.let {
            return this.suppressJvmWildcards(it)
        }

        if (type.annotations.hasAnnotation(JVM_WILDCARD_ANNOTATION_FQ_NAME)) {
            return TypeMappingMode.createWithConstantDeclarationSiteWildcardsMode(
                skipDeclarationSiteWildcards = false,
                isForAnnotationParameter = isForAnnotationParameter,
                fallbackMode = this,
                needInlineClassWrapping = needInlineClassWrapping,
                mapTypeAliases = mapTypeAliases
            )
        }

        return this
    }

    private fun TypeMappingMode.suppressJvmWildcards(
        suppress: Boolean
    ): TypeMappingMode {
        return TypeMappingMode.createWithConstantDeclarationSiteWildcardsMode(
            skipDeclarationSiteWildcards = suppress,
            isForAnnotationParameter = isForAnnotationParameter,
            needInlineClassWrapping = needInlineClassWrapping,
            mapTypeAliases = mapTypeAliases
        )
    }

    private fun TypeMappingMode.updateFromParents(
        ref: KSTypeReference
    ): TypeMappingMode {
        ref.findJvmSuppressWildcards()?.let {
            return this.suppressJvmWildcards(it)
        }

        return this
    }

    // Type arguments need to be resolved recursively in a top-down manner. So we find and resolve the outer most
    // reference that contains this argument. Then locate and return the argument.
    @KspExperimental
    override fun getJavaWildcard(reference: KSTypeReference): KSTypeReference {
        // If the outer-most reference cannot be found, e.g., when this reference is nested in KSType.arguments,
        // fallback to PARAMETER_TYPE effectively.
        val (ref, indexes) = reference.findOuterMostRef()

        val type = ref.resolve()
        if (type.isError)
            return reference

        val position = findRefPosition(ref)
        val kotlinType = (type as KSTypeImpl).kotlinType

        val typeSystem = SimpleClassicTypeSystemContext
        val typeMappingMode = when (position) {
            RefPosition.PARAMETER_TYPE -> typeSystem.getOptimalModeForValueParameter(kotlinType)
            RefPosition.RETURN_TYPE ->
                typeSystem.getOptimalModeForReturnType(kotlinType, ref.isReturnTypeOfAnnotationMethod())

            RefPosition.SUPER_TYPE -> TypeMappingMode.SUPER_TYPE
        }.updateFromParents(ref)

        kotlinType.arguments.forEach { argument ->
            if (position == RefPosition.SUPER_TYPE &&
                argument.projectionKind != org.jetbrains.kotlin.types.Variance.INVARIANT
            ) {
                val errorType = KSErrorType(
                    name = type.toString(),
                    message = "Type projection isn't allowed in immediate arguments to supertypes"
                )
                return KSTypeReferenceSyntheticImpl.getCached(errorType, null)
            }
        }

        val wildcardType = kotlinType.toWildcard(typeMappingMode).let {
            var candidate: KotlinType = it.getOrElse { error ->
                val errorType = KSErrorType(name = type.toString(), message = error.message)
                return KSTypeReferenceSyntheticImpl.getCached(errorType, null)
            }
            for (i in indexes.reversed()) {
                candidate = candidate.arguments[i].type
            }
            getKSTypeCached(candidate)
        }

        return KSTypeReferenceSyntheticImpl.getCached(wildcardType, null)
    }

    @KspExperimental
    override fun isJavaRawType(type: KSType): Boolean {
        return type is KSTypeImpl && type.kotlinType.unwrap() is RawType
    }

    @KspExperimental
    override fun getPackageAnnotations(packageName: String): Sequence<KSAnnotation> {
        return packageInfoFiles.singleOrNull { it.packageName.asString() == packageName }
            ?.getPackageAnnotations()?.asSequence() ?: emptySequence()
    }

    @KspExperimental
    override fun getPackagesWithAnnotation(annotationName: String): Sequence<String> {
        return packageInfoFiles.filter {
            it.getPackageAnnotations().any {
                (it.annotationType.element as? KSClassifierReference)?.referencedName()
                    ?.substringAfterLast(".") == annotationName.substringAfterLast(".") &&
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName
            }
        }.map { it.packageName.asString() }
    }

    @KspExperimental
    override fun getModuleName(): KSName = KSNameImpl.getCached(moduleIdentifier)

    private val psiJavaFiles = allKSFiles.filterIsInstance<KSFileJavaImpl>().map {
        Pair(it.psi.virtualFile.path, it.psi)
    }.toMap()

    internal fun findPsiJavaFile(path: String): PsiFile? = psiJavaFiles.get(path)

    // Always construct the key on the fly, so that it and value can be reclaimed any time.
    private val contributedDescriptorsCache =
        WeakHashMap<Pair<MemberScope, DescriptorKindFilter>, Map<String, List<DeclarationDescriptor>>>()

    private inline fun MemberScope.findEnclosedDescriptor(
        kindFilter: DescriptorKindFilter,
        name: String,
        crossinline filter: (DeclarationDescriptor) -> Boolean,
    ): DeclarationDescriptor? {
        val nameToDescriptors = contributedDescriptorsCache.computeIfAbsent(Pair(this, kindFilter)) {
            getContributedDescriptors(kindFilter).groupBy { it.name.asString() }
        }
        return nameToDescriptors.get(name)?.firstOrNull(filter)
    }

    private inline fun ClassDescriptor.findEnclosedDescriptor(
        kindFilter: DescriptorKindFilter,
        name: String,
        crossinline filter: (DeclarationDescriptor) -> Boolean,
    ): DeclarationDescriptor? {
        return this.unsubstitutedMemberScope.findEnclosedDescriptor(
            kindFilter = kindFilter,
            name = name,
            filter = filter
        ) ?: this.staticScope.findEnclosedDescriptor(
            kindFilter = kindFilter,
            name = name,
            filter = filter
        ) ?: constructors.firstOrNull {
            kindFilter.accepts(it) && filter(it)
        }
    }
}

// TODO: cross module resolution
fun DeclarationDescriptor.findExpectsInKSDeclaration(): Sequence<KSDeclaration> =
    findExpects().asSequence().map {
        it.toKSDeclaration()
    }

// TODO: cross module resolution
fun DeclarationDescriptor.findActualsInKSDeclaration(): Sequence<KSDeclaration> =
    findActuals(this.module).asSequence().map {
        it.toKSDeclaration()
    }

fun MemberDescriptor.toKSDeclaration(): KSDeclaration =
    when (val psi = findPsi()) {
        is KtClassOrObject -> KSClassDeclarationImpl.getCached(psi)
        is KtFunction -> KSFunctionDeclarationImpl.getCached(psi)
        is KtProperty -> KSPropertyDeclarationImpl.getCached((psi))
        is KtTypeAlias -> KSTypeAliasImpl.getCached(psi)
        is PsiClass -> KSClassDeclarationJavaImpl.getCached(psi)
        is PsiMethod -> KSFunctionDeclarationJavaImpl.getCached(psi)
        is PsiField -> KSPropertyDeclarationJavaImpl.getCached(psi)
        else -> when (this) {
            is ClassDescriptor -> KSClassDeclarationDescriptorImpl.getCached(this)
            is FunctionDescriptor -> KSFunctionDeclarationDescriptorImpl.getCached(this)
            is PropertyDescriptor -> KSPropertyDeclarationDescriptorImpl.getCached(this)
            is TypeAliasDescriptor -> KSTypeAliasDescriptorImpl.getCached(this)
            else -> throw IllegalStateException("Unknown expect/actual implementation")
        }
    }

/**
 * [NewTypeSubstitutor] handles variance better than [TypeSubstitutor] so we use it when subtituting
 * types in [ResolverImpl.asMemberOf] implementations.
 */
private fun TypeSubstitutor.toNewSubstitutor() = composeWith(
    org.jetbrains.kotlin.resolve.calls.inference.components.EmptySubstitutor
)

private fun KotlinType.createTypeSubstitutor(): NewTypeSubstitutor {
    return SubstitutionUtils.buildDeepSubstitutor(this).toNewSubstitutor()
}

/**
 * Extracts the identifier from a module Name.
 *
 * One caveat here is that kotlin passes a special name into the plugin which cannot be used as an identifier.
 * On the other hand, to construct the correct TypeMapper, we need a non-special name.
 * This function extracts the non-special name from a given name if it is special.
 *
 * @see: https://github.com/JetBrains/kotlin/blob/master/compiler/cli/src/org/jetbrains/kotlin/cli/jvm/compiler/TopDownAnalyzerFacadeForJVM.kt#L305
 */
private fun Name.getNonSpecialIdentifier(): String {
    // the analyzer might pass down a special name which will break type mapper name computations.
    // If it is a special name, we turn it back to an id
    if (!isSpecial || asString().isBlank()) {
        return asString()
    }
    // special names starts with a `<` and usually end with `>`
    return if (asString().last() == '>') {
        asString().substring(1, asString().length - 1)
    } else {
        asString().substring(1)
    }
}

internal fun KSAnnotated.findAnnotationFromUseSiteTarget(): Sequence<KSAnnotation> {
    return when (this) {
        is KSPropertyGetter -> (this.receiver as? KSDeclarationImpl)?.let {
            it.originalAnnotations.asSequence().filter { it.useSiteTarget == AnnotationUseSiteTarget.GET }
        }
        is KSPropertySetter -> (this.receiver as? KSDeclarationImpl)?.let {
            it.originalAnnotations.asSequence().filter { it.useSiteTarget == AnnotationUseSiteTarget.SET }
        }
        is KSValueParameter -> {
            var parent = when (this) {
                is KSValueParameterSyntheticImpl -> this.owner
                is KSValueParameterImpl -> this.ktParameter.findParentAnnotated()
                else -> null
            }
            // TODO: eliminate annotationsFromParents to make this fully sequence.
            val annotationsFromParents = mutableListOf<KSAnnotation>()
            (parent as? KSPropertyAccessorImpl)?.let {
                annotationsFromParents.addAll(
                    it.originalAnnotations.asSequence()
                        .filter { it.useSiteTarget == AnnotationUseSiteTarget.SETPARAM }
                )
                parent = (parent as KSPropertyAccessorImpl).receiver
            }
            (parent as? KSPropertyDeclarationImpl)?.let {
                annotationsFromParents.addAll(
                    it.originalAnnotations.asSequence()
                        .filter { it.useSiteTarget == AnnotationUseSiteTarget.SETPARAM }
                )
            }
            annotationsFromParents.asSequence()
        }
        else -> emptySequence()
    } ?: emptySequence()
}

// Resolve to underlying type.
// Only slightly slower than resolving a plain type, because everything is resolved and cached in the first resolve().
// FIXME: add a resolution mode in resolveUserType() to resolve to underlying type directly.
internal fun KSTypeReference.resolveToUnderlying(): KSType {
    var candidate = resolve()
    var declaration = candidate.declaration
    while (declaration is KSTypeAlias) {
        candidate = declaration.type.resolve()
        declaration = candidate.declaration
    }
    return candidate
}
