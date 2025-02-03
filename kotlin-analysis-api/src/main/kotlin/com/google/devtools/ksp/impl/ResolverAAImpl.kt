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
@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.google.devtools.ksp.impl

import com.google.devtools.ksp.*
import com.google.devtools.ksp.common.*
import com.google.devtools.ksp.common.impl.*
import com.google.devtools.ksp.common.visitor.CollectAnnotatedSymbolsVisitor
import com.google.devtools.ksp.impl.symbol.java.KSAnnotationJavaImpl
import com.google.devtools.ksp.impl.symbol.kotlin.*
import com.google.devtools.ksp.impl.symbol.util.*
import com.google.devtools.ksp.impl.symbol.util.DeclarationOrdering
import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.file.impl.JavaFileManager
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.components.buildSubstitutor
import org.jetbrains.kotlin.analysis.api.fir.symbols.KaFirSymbol
import org.jetbrains.kotlin.analysis.api.fir.types.KaFirType
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.analysis.decompiler.stub.file.ClsKotlinBinaryClassCache
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.getFirResolveSession
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.findFacadeClass
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCliJavaFileManagerImpl
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.types.isRaw
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.load.kotlin.JvmPackagePartSource
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.load.kotlin.getOptimalModeForReturnType
import org.jetbrains.kotlin.load.kotlin.getOptimalModeForValueParameter
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.FqNameUnsafe
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.org.objectweb.asm.Opcodes

@Suppress("MemberVisibilityCanBePrivate")
@OptIn(KspExperimental::class)
class ResolverAAImpl(
    val allKSFiles: List<KSFile>,
    val newKSFiles: List<KSFile>,
    val deferredSymbols: Map<SymbolProcessor, List<Restorable>>,
    val project: Project,
    val incrementalContext: IncrementalContextAA,
) : Resolver {
    companion object {
        private val instance_prop: ThreadLocal<ResolverAAImpl> = ThreadLocal()
        private val ktModule_prop: ThreadLocal<KaSourceModule> = ThreadLocal()
        var instance: ResolverAAImpl
            get() = instance_prop.get()
            set(value) {
                instance_prop.set(value)
            }
        var ktModule: KaSourceModule
            get() = ktModule_prop.get()
            set(value) {
                ktModule_prop.set(value)
            }
    }
    lateinit var propertyAsMemberOfCache: MutableMap<Pair<KSPropertyDeclaration, KSType>, KSType>
    lateinit var functionAsMemberOfCache: MutableMap<Pair<KSFunctionDeclaration, KSType>, KSFunction>
    val javaFileManager = project.getService(JavaFileManager::class.java) as KotlinCliJavaFileManagerImpl
    private val classBinaryCache = ClsKotlinBinaryClassCache()
    private val packageInfoFiles by lazy {
        allKSFiles.filter { it.fileName == "package-info.java" }.asSequence().memoized()
    }

    // TODO: fix in upstream for builtin types.
    override val builtIns: KSBuiltIns by lazy {
        val builtIns = analyze { useSiteSession.builtinTypes }
        object : KSBuiltIns {
            override val anyType: KSType by lazy { KSTypeImpl.getCached(builtIns.any) }
            override val nothingType: KSType by lazy { KSTypeImpl.getCached(builtIns.nothing) }
            override val unitType: KSType by lazy { KSTypeImpl.getCached(builtIns.unit) }
            override val numberType: KSType by lazy {
                getClassDeclarationByName("kotlin.Number")!!.asStarProjectedType()
            }
            override val byteType: KSType by lazy { KSTypeImpl.getCached(builtIns.byte) }
            override val shortType: KSType by lazy { KSTypeImpl.getCached(builtIns.short) }
            override val intType: KSType by lazy { KSTypeImpl.getCached(builtIns.int) }
            override val longType: KSType by lazy { KSTypeImpl.getCached(builtIns.long) }
            override val floatType: KSType by lazy { KSTypeImpl.getCached(builtIns.float) }
            override val doubleType: KSType by lazy { KSTypeImpl.getCached(builtIns.double) }
            override val charType: KSType by lazy { KSTypeImpl.getCached(builtIns.char) }
            override val booleanType: KSType by lazy { KSTypeImpl.getCached(builtIns.boolean) }
            override val stringType: KSType by lazy { KSTypeImpl.getCached(builtIns.string) }
            override val iterableType: KSType by lazy {
                getClassDeclarationByName("kotlin.collections.Iterable")!!.asStarProjectedType()
            }
            override val annotationType: KSType by lazy {
                getClassDeclarationByName("kotlin.Annotation")!!.asStarProjectedType()
            }
            override val arrayType: KSType by lazy {
                getClassDeclarationByName("kotlin.Array")!!.asStarProjectedType()
            }
        }
    }

    override fun createKSTypeReferenceFromKSType(type: KSType): KSTypeReference {
        return KSTypeReferenceSyntheticImpl.getCached(type, null)
    }

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
                (declaration as? KSClassDeclarationImpl)?.let {
                    analyze {
                        if (
                            it.ktClassOrObjectSymbol.staticMemberScope
                                .declarations.contains(declaration.ktDeclarationSymbol)
                        )
                            modifiers.add(Modifier.JAVA_STATIC)
                    }
                }

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
                }
            }
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                if (declaration.hasAnnotation(JVM_STATIC_ANNOTATION_FQN)) {
                    modifiers.add(Modifier.JAVA_STATIC)
                }
                addVisibilityModifiers()
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
                }
            }
            else -> Unit
        }
        return modifiers
    }

    internal val KSPropertyDeclaration.jvmAccessFlag: Int
        get() = when (origin) {
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                val fileManager = instance.javaFileManager
                val parentClass = this.findParentOfType<KSClassDeclaration>()
                val classId = (parentClass as KSClassDeclarationImpl).ktClassOrObjectSymbol.classId!!
                BinaryClassInfoCache.getCached(classId, fileManager)
                    ?.fieldAccFlags?.get(this.simpleName.asString()) ?: 0
            }
            else -> throw IllegalStateException("this function expects only KOTLIN_LIB or JAVA_LIB")
        }

    internal val KSFunctionDeclaration.jvmAccessFlag: Int
        get() = when (origin) {
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                val jvmDesc = mapToJvmSignatureInternal(this)
                val fileManager = instance.javaFileManager
                val parentClass = this.findParentOfType<KSClassDeclaration>()
                val classId = (parentClass as KSClassDeclarationImpl).ktClassOrObjectSymbol.classId!!
                BinaryClassInfoCache.getCached(classId, fileManager)
                    ?.methodAccFlags?.get(this.simpleName.asString() + jvmDesc) ?: 0
            }
            else -> throw IllegalStateException("this function expects only KOTLIN_LIB or JAVA_LIB")
        }

    override fun getAllFiles(): Sequence<KSFile> {
        return allKSFiles.asSequence()
    }

    override fun getClassDeclarationByName(name: KSName): KSClassDeclaration? {
        fun findClass(name: KSName): KaSymbol? {
            if (name.asString() == "") {
                return null
            }
            val parent = name.getQualifier()
            val simpleName = name.getShortName()
            val classId = ClassId(FqName(parent), Name.identifier(simpleName))
            analyze {
                classId.toKtClassSymbol()
            }?.let { return it }
            return (findClass(KSNameImpl.getCached(name.getQualifier())) as? KaNamedClassSymbol)?.let {
                analyze {
                    (
                        it.staticMemberScope.classifiers { it.asString() == simpleName }.singleOrNull()
                            ?: it.memberScope.classifiers { it.asString() == simpleName }.singleOrNull()
                        ) as? KaNamedClassSymbol
                        ?: it.staticMemberScope.callables { it.asString() == simpleName }.singleOrNull()
                            as? KaEnumEntrySymbol
                }
            }
        }
        return findClass(name)?.let {
            when (it) {
                is KaNamedClassSymbol -> KSClassDeclarationImpl.getCached(it)
                is KaEnumEntrySymbol -> KSClassDeclarationEnumEntryImpl.getCached(it)
                else -> throw IllegalStateException()
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    @KspExperimental
    override fun getDeclarationsFromPackage(packageName: String): Sequence<KSDeclaration> {
        return analyze {
            findPackage(FqName(packageName))?.packageScope?.declarations?.distinct()?.mapNotNull { symbol ->
                when (symbol) {
                    is KaNamedClassSymbol -> KSClassDeclarationImpl.getCached(symbol)
                    is KaFunctionSymbol -> KSFunctionDeclarationImpl.getCached(symbol)
                    is KaPropertySymbol -> KSPropertyDeclarationImpl.getCached(symbol)
                    is KaTypeAliasSymbol -> KSTypeAliasImpl.getCached(symbol)
                    is KaJavaFieldSymbol -> KSPropertyDeclarationJavaImpl.getCached(symbol)
                    else -> null
                }
            }?.asSequence() ?: emptySequence()
        }
    }

    override fun getDeclarationsInSourceOrder(container: KSDeclarationContainer): Sequence<KSDeclaration> {
        if (container.origin != Origin.KOTLIN_LIB) {
            return container.declarations
        }

        // TODO: multiplatform
        if (!isJvm) {
            return container.declarations
        }

        require(container is AbstractKSDeclarationImpl)
        val fileManager = instance.javaFileManager
        var parentClass: KSNode = container
        while (parentClass.parent != null &&
            (parentClass !is KSClassDeclarationImpl || parentClass.ktClassOrObjectSymbol.classId == null)
        ) {
            parentClass = parentClass.parent!!
        }

        if (parentClass !is KSClassDeclarationImpl) {
            return container.declarations
        }

        // Members of Foo's companion object are compiled into Foo and Foo$Companion. Total ordering is not recoverable
        // from class files. Let's give up and rely on AA for now.
        if (parentClass.isCompanionObject) {
            return container.declarations
        }

        val classId = parentClass.ktClassOrObjectSymbol.classId ?: return container.declarations
        val virtualFile = classId.getVirtualFile(fileManager) ?: return container.declarations
        val kotlinClass = classBinaryCache.getKotlinBinaryClass(virtualFile) ?: return container.declarations
        val declarationOrdering = DeclarationOrdering(kotlinClass)

        @Suppress("UNCHECKED_CAST")
        return (container.declarations as? Sequence<AbstractKSDeclarationImpl>)
            ?.sortedWith(declarationOrdering.comparator) ?: container.declarations
    }

    override fun getFunctionDeclarationsByName(
        name: KSName,
        includeTopLevel: Boolean
    ): Sequence<KSFunctionDeclaration> {
        val qualifier = name.getQualifier()
        val functionName = name.getShortName()
        val nonTopLevelResult = this.getClassDeclarationByName(qualifier)?.getDeclaredFunctions()
            ?.filter { it.simpleName.asString() == functionName }?.asSequence() ?: emptySequence()
        return if (!includeTopLevel) nonTopLevelResult else {
            nonTopLevelResult.plus(
                analyze {
                    findTopLevelCallables(FqName(qualifier), Name.identifier(functionName))
                        .filterIsInstance<KaFunctionSymbol>()
                        .map {
                            KSFunctionDeclarationImpl.getCached(it)
                        }
                }
            )
        }
    }

    override fun getJavaWildcard(reference: KSTypeReference): KSTypeReference {
        val (ref, indexes) = reference.findOuterMostRef()
        val type = ref.resolve()
        if (type.isError)
            return reference
        val position = findRefPosition(ref)
        val ktType = (type as KSTypeImpl).type.fullyExpand()
        // cast to FIR internal needed due to missing support in AA for type mapping mode
        // and corresponding type mapping APIs.
        val coneType = (ktType as KaFirType).coneType
        val mode = analyze {
            val typeContext = analyze { useSiteModule.getFirResolveSession(project).useSiteFirSession.typeContext }
            when (position) {
                RefPosition.RETURN_TYPE -> typeContext.getOptimalModeForReturnType(
                    coneType,
                    reference.isReturnTypeOfAnnotationMethod()
                )
                RefPosition.SUPER_TYPE -> TypeMappingMode.SUPER_TYPE
                RefPosition.PARAMETER_TYPE -> typeContext.getOptimalModeForValueParameter(coneType)
            }.updateFromParents(ref)
        }
        return analyze {
            ktType.toWildcard(mode).let {
                var candidate: KaType = it
                for (i in indexes.reversed()) {
                    candidate = candidate.typeArguments()[i].type!!
                }
                KSTypeReferenceSyntheticImpl.getCached(KSTypeImpl.getCached(candidate), null)
            }
        }
    }

    override fun getJvmCheckedException(accessor: KSPropertyAccessor): Sequence<KSType> {
        return when (accessor.origin) {
            Origin.KOTLIN, Origin.SYNTHETIC -> {
                extractThrowsAnnotation(accessor)
            }
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                val fileManager = javaFileManager
                val parentClass = accessor.findParentOfType<KSClassDeclaration>()
                val classId = (parentClass as KSClassDeclarationImpl).ktClassOrObjectSymbol.classId
                    ?: return emptySequence()
                val virtualFileContent = classId.getFileContent(fileManager) ?: return emptySequence()
                val jvmDesc = this.mapToJvmSignatureInternal(accessor)
                extractThrowsFromClassFile(
                    virtualFileContent,
                    jvmDesc,
                    (if (accessor is KSPropertyGetter) "get" else "set") +
                        accessor.receiver.simpleName.asString().replaceFirstChar(Char::uppercaseChar)
                )
            }
            else -> emptySequence()
        }
    }

    @OptIn(KaExperimentalApi::class)
    override fun getJvmCheckedException(function: KSFunctionDeclaration): Sequence<KSType> {
        return when (function.origin) {
            Origin.JAVA -> {
                val psi = (function as KSFunctionDeclarationImpl).ktFunctionSymbol.psi as PsiMethod
                psi.throwsList.referencedTypes.asSequence().mapNotNull {
                    analyze {
                        it.asKaType(psi)?.let { KSTypeImpl.getCached(it) }
                    }
                }
            }
            Origin.KOTLIN -> {
                extractThrowsAnnotation(function)
            }
            Origin.KOTLIN_LIB, Origin.JAVA_LIB -> {
                val fileManager = javaFileManager
                val parentClass = function.findParentOfType<KSClassDeclaration>()
                val classId = (parentClass as KSClassDeclarationImpl).ktClassOrObjectSymbol.classId
                    ?: return emptySequence()
                val virtualFileContent = classId.getFileContent(fileManager) ?: return emptySequence()
                val jvmDesc = this.mapToJvmSignature(function)
                extractThrowsFromClassFile(virtualFileContent, jvmDesc, function.simpleName.asString())
            }
            else -> emptySequence()
        }
    }

    // TODO: handle library symbols
    override fun getJvmName(accessor: KSPropertyAccessor): String {
        val symbol: KaPropertyAccessorSymbol? = when (accessor) {
            is KSPropertyAccessorImpl -> accessor.ktPropertyAccessorSymbol
            else -> null
        }

        symbol?.explictJvmName()?.let {
            return it
        }

        if (accessor.receiver.closestClassDeclaration()?.classKind == ClassKind.ANNOTATION_CLASS) {
            return accessor.receiver.simpleName.asString()
        }

        val name = accessor.receiver.simpleName.asString()
        val uppercasedName = name.replaceFirstChar(Char::uppercaseChar)
        // https://kotlinlang.org/docs/java-to-kotlin-interop.html#properties
        val prefixedName = when (accessor) {
            is KSPropertyGetter -> if (name.startsWith("is")) name else "get$uppercasedName"
            is KSPropertySetter -> if (name.startsWith("is")) "set${name.removePrefix("is")}" else "set$uppercasedName"
            else -> ""
        }

        val inlineSuffix = symbol?.inlineSuffix ?: ""
        val mangledName = symbol?.internalSuffix ?: ""
        return "$prefixedName$inlineSuffix$mangledName"
    }

    // TODO: handle library symbols
    override fun getJvmName(declaration: KSFunctionDeclaration): String {
        val symbol: KaFunctionSymbol? = when (declaration) {
            is KSFunctionDeclarationImpl -> declaration.ktFunctionSymbol
            else -> null
        }

        symbol?.explictJvmName()?.let {
            return it
        }

        val inlineSuffix = symbol?.inlineSuffix ?: ""
        val mangledName = symbol?.internalSuffix ?: ""
        return declaration.simpleName.asString() + inlineSuffix + mangledName
    }

    override fun getKSNameFromString(name: String): KSName {
        return KSNameImpl.getCached(name)
    }

    // FIXME: correct implementation after incremental is ready.
    override fun getNewFiles(): Sequence<KSFile> {
        return newKSFiles.asSequence()
    }

    private fun getOwnerJvmClassNameHelper(declaration: KSDeclaration): String? {
        @Suppress("UNCHECKED_CAST")
        return declaration.closestClassDeclaration()?.let {
            // Find classId for JvmClassName for member callables.
            (it as? KSClassDeclarationImpl)?.ktClassOrObjectSymbol?.classId
                ?.asString()?.replace(".", "$")?.replace("/", ".")
        } ?: declaration.containingFile?.let {
            // Find containing file facade class name from file symbol
            (it as? KSFileImpl)?.let {
                ((it.ktFileSymbol.psi as? KtFile)?.findFacadeClass() as? KtLightClassForFacade)
                    ?.facadeClassFqName?.asString()
            }
            // Down cast to fir symbol for library symbols as light facade class for libraries not available in AA.
        } ?: (
            ((declaration as? AbstractKSDeclarationImpl)?.ktDeclarationSymbol as? KaFirSymbol<FirCallableSymbol<*>>)
                ?.firSymbol?.containerSource as? JvmPackagePartSource
            )?.classId?.asFqNameString()
    }

    override fun getOwnerJvmClassName(declaration: KSFunctionDeclaration): String? {
        return getOwnerJvmClassNameHelper(declaration)
    }

    override fun getOwnerJvmClassName(declaration: KSPropertyDeclaration): String? {
        return getOwnerJvmClassNameHelper(declaration)
    }

    override fun getPropertyDeclarationByName(name: KSName, includeTopLevel: Boolean): KSPropertyDeclaration? {
        val qualifier = name.getQualifier()
        val propertyName = name.getShortName()
        val nonTopLevelResult = this.getClassDeclarationByName(qualifier)?.getDeclaredProperties()
            ?.singleOrNull { it.simpleName.asString() == propertyName }
        return if (!includeTopLevel) {
            nonTopLevelResult
        } else {
            val topLevelResult = (
                analyze {
                    findTopLevelCallables(FqName(qualifier), Name.identifier(propertyName)).singleOrNull {
                        it is KaPropertySymbol
                    }?.toKSDeclaration() as? KSPropertyDeclaration
                }
                )
            if (topLevelResult != null && nonTopLevelResult != null) {
                throw IllegalStateException("Found multiple properties with same qualified name")
            }
            nonTopLevelResult ?: topLevelResult
        }
    }

    override fun getSymbolsWithAnnotation(annotationName: String, inDepth: Boolean): Sequence<KSAnnotated> {
        val expandedIfAlias = analyze {
            val classId = ClassId.fromString(annotationName)
            findTypeAlias(classId)?.expandedType?.symbol?.classId?.asFqNameString()
        }
        val realAnnotationName = expandedIfAlias ?: annotationName

        fun checkAnnotated(annotated: KSAnnotated): Boolean {
            return annotated.annotations.any {
                val kaType = (it.annotationType.resolve() as? KSTypeImpl)?.type ?: return@any false
                kaType.fullyExpand().symbol?.classId?.asFqNameString() == realAnnotationName
            }
        }

        val newSymbols = if (inDepth) newAnnotatedSymbolsWithLocals else newAnnotatedSymbols
        return (newSymbols + deferredSymbolsRestored).asSequence().filter(::checkAnnotated)
    }

    private fun collectAnnotatedSymbols(inDepth: Boolean): Collection<KSAnnotated> {
        val visitor = CollectAnnotatedSymbolsVisitor(inDepth)

        for (file in newKSFiles) {
            file.accept(visitor, Unit)
        }

        return visitor.symbols
    }

    private val deferredSymbolsRestored: Set<KSAnnotated> by lazy {
        deferredSymbols.values.flatten().mapNotNull { it.restore() }.toSet()
    }

    private val newAnnotatedSymbols: Collection<KSAnnotated> by lazy {
        collectAnnotatedSymbols(false)
    }

    private val newAnnotatedSymbolsWithLocals: Collection<KSAnnotated> by lazy {
        collectAnnotatedSymbols(true)
    }

    override fun getTypeArgument(typeRef: KSTypeReference, variance: Variance): KSTypeArgument {
        return KSTypeArgumentLiteImpl.getCached(typeRef, variance)
    }

    override fun isJavaRawType(type: KSType): Boolean {
        return type is KSTypeImpl && (type.type as KaFirType).coneType.isRaw()
    }

    internal fun KSFile.getPackageAnnotations() = (this as? KSFileJavaImpl)?.psi?.packageStatement?.annotationList
        ?.annotations?.map { KSAnnotationJavaImpl.getCached(it, this) } ?: emptyList<KSAnnotation>()

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

    @OptIn(KaExperimentalApi::class)
    @KspExperimental
    override fun getModuleName(): KSName {
        return KSNameImpl.getCached((ktModule.stableModuleName ?: ktModule.name).removeSurrounding("<", ">"))
    }

    @KspExperimental
    override fun mapJavaNameToKotlin(javaName: KSName): KSName? {
        return JavaToKotlinClassMap.mapJavaToKotlin(FqName(javaName.asString()))?.toKSName()
    }

    @KspExperimental
    override fun mapKotlinNameToJava(kotlinName: KSName): KSName? {
        return JavaToKotlinClassMap.mapKotlinToJava(FqNameUnsafe(kotlinName.asString()))?.toKSName()
    }

    @KspExperimental
    override fun mapToJvmSignature(declaration: KSDeclaration): String? {
        return mapToJvmSignatureInternal(declaration)
    }

    internal fun mapToJvmSignature(accessor: KSPropertyAccessor): String {
        return mapToJvmSignatureInternal(accessor) ?: ""
    }

    override fun overrides(overrider: KSDeclaration, overridee: KSDeclaration): Boolean {
        val overriderSymbol = when (overrider) {
            is KSFunctionDeclarationImpl -> if (overrider.ktFunctionSymbol is KaPropertyAccessorSymbol) {
                (overrider.parent as KSPropertyDeclarationImpl).ktPropertySymbol
            } else {
                overrider.ktFunctionSymbol
            }
            is KSPropertyDeclarationImpl -> overrider.ktPropertySymbol
            else -> return false
        }
        val overrideeSymbol = when (overridee) {
            is KSFunctionDeclarationImpl -> overridee.ktFunctionSymbol
            is KSPropertyDeclarationImpl -> overridee.ktPropertySymbol
            else -> return false
        }
        overrider.closestClassDeclaration()?.asStarProjectedType()?.let {
            recordLookupWithSupertypes((it as KSTypeImpl).type)
        }
        recordLookupForPropertyOrMethod(overrider)
        recordLookupForPropertyOrMethod(overridee)
        if (!overridee.isVisibleFrom(overrider))
            return false
        return analyze {
            overriderSymbol.allOverriddenSymbols.contains(overrideeSymbol) ||
                overriderSymbol.intersectionOverriddenSymbols.contains(overrideeSymbol)
        }
    }

    override fun overrides(
        overrider: KSDeclaration,
        overridee: KSDeclaration,
        containingClass: KSClassDeclaration
    ): Boolean {
        recordLookupForPropertyOrMethod(overrider)
        recordLookupForPropertyOrMethod(overridee)
        return when (overrider) {
            is KSPropertyDeclaration -> containingClass.getAllProperties().singleOrNull {
                recordLookupForPropertyOrMethod(it)
                it.simpleName.asString() == overrider.simpleName.asString()
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
                    ).any { overrides(it, overridee) }
                } else {
                    candidates.any {
                        recordLookupForPropertyOrMethod(it)
                        overrides(it, overridee)
                    }
                }
            }
            else -> false
        }
    }

    @OptIn(KaExperimentalApi::class)
    internal fun mapToJvmSignatureInternal(ksAnnotated: KSAnnotated): String? {
        fun KaType.toSignature(): String {
            return analyze {
                this@toSignature.mapToJvmType().descriptor.let {
                    when (it) {
                        "Ljava.lang.Void;" -> "Ljava/lang/Void;"
                        "Lkotlin/Unit;" -> "V"
                        else -> it
                    }
                }
            }
        }

        fun KSType.toSignature(): String {
            return if (this is KSTypeImpl) {
                analyze {
                    val decl = (this@toSignature.declaration as? KSClassDeclaration)
                    // Force inline value class to be unwrapped.
                    // Do not remove until AA fixes this.
                    decl?.primaryConstructor
                    type.toSignature()
                }
            } else {
                "<ERROR>"
            }
        }

        return when (ksAnnotated) {
            is KSClassDeclaration -> analyze {
                (ksAnnotated.asStarProjectedType() as KSTypeImpl).toSignature()
            }
            is KSFunctionDeclarationImpl -> {
                analyze {
                    buildString {
                        append("(")
                        ksAnnotated.parameters.forEach { append(it.type.resolve().toSignature()) }
                        append(")")
                        if (ksAnnotated.isConstructor()) {
                            append("V")
                        } else {
                            append(ksAnnotated.returnType!!.resolve().toSignature())
                        }
                    }
                }
            }
            is KSPropertyDeclaration -> {
                analyze {
                    ksAnnotated.type.resolve().toSignature()
                }
            }
            is KSPropertyAccessorImpl -> {
                analyze {
                    buildString {
                        append("(")
                        ksAnnotated.ktPropertyAccessorSymbol.valueParameters.forEach {
                            append(it.returnType.toSignature())
                        }
                        append(")")
                        append(ksAnnotated.ktPropertyAccessorSymbol.returnType.toSignature())
                    }
                }
            }
            else -> null
        }
    }

    @OptIn(KaExperimentalApi::class)
    internal fun computeAsMemberOf(property: KSPropertyDeclaration, containing: KSType): KSType {
        val declaredIn = property.closestClassDeclaration()
            ?: throw IllegalArgumentException(
                "Cannot call asMemberOf with a property that is not declared in a class or an interface"
            )
        val key = property to containing
        return propertyAsMemberOfCache.getOrPut(key) {
            val resolved = property.type.resolve()
            if (containing is KSTypeImpl && resolved is KSTypeImpl) {
                recordLookupWithSupertypes(containing.type)
                recordLookupForPropertyOrMethod(property)
                val isSubTypeOf = analyze {
                    (declaredIn.asStarProjectedType() as? KSTypeImpl)?.type?.let {
                        containing.type.isSubtypeOf(it)
                    } ?: false
                }
                if (!isSubTypeOf) {
                    throw IllegalArgumentException(
                        "$containing is not a sub type of the class/interface that contains `$property` ($declaredIn)"
                    )
                }
                analyze {
                    buildSubstitutor {
                        fillInDeepSubstitutor(containing.type, this@buildSubstitutor)
                    }.let {
                        // recursively substitute to ensure transitive substitution works.
                        // should fix in upstream as well.
                        var result = resolved.type
                        var cnt = 0
                        while (it.substitute(result) != result) {
                            if (cnt > 100) {
                                throw IllegalStateException(
                                    "Potential infinite loop in type substitution for computeAsMemberOf"
                                )
                            }
                            result = it.substitute(result)
                            cnt += 1
                        }
                        KSTypeImpl.getCached(result)
                    }
                }
            } else {
                return if (resolved.isError) resolved else KSErrorType(resolved.toString())
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    internal fun computeAsMemberOf(function: KSFunctionDeclaration, containing: KSType): KSFunction {
        val propertyDeclaredIn = function.closestClassDeclaration()
            ?: throw IllegalArgumentException(
                "Cannot call asMemberOf with a function that is not declared in a class or an interface"
            )
        val key = function to containing
        return functionAsMemberOfCache.getOrPut(key) {
            if (containing is KSTypeImpl) {
                recordLookupWithSupertypes(containing.type)
                recordLookupForPropertyOrMethod(function)
                val isSubTypeOf = analyze {
                    (propertyDeclaredIn.asStarProjectedType() as? KSTypeImpl)?.type?.let {
                        containing.type.isSubtypeOf(it)
                    } ?: false
                }
                if (!isSubTypeOf) {
                    throw IllegalArgumentException(
                        "$containing is not a sub type of the class/interface that contains `$function` " +
                            "($propertyDeclaredIn)"
                    )
                }
                analyze {
                    buildSubstitutor {
                        fillInDeepSubstitutor(containing.type, this@buildSubstitutor)
                    }.let {
                        // recursively substitute to ensure transitive substitution works.
                        // should fix in upstream as well.
                        // for functions we need to test both returnType and value parameters converges.
                        var funcToSub = (function as KSFunctionDeclarationImpl).ktFunctionSymbol.substitute(it)
                        var next = funcToSub.substitute(it)
                        var cnt = 0
                        while (
                            funcToSub.returnType != next.returnType ||
                            funcToSub.valueParameters.zip(next.valueParameters)
                                .any { it.first.returnType != it.second.returnType } ||
                            funcToSub.receiverType != next.receiverType
                        ) {
                            if (cnt > 100) {
                                throw IllegalStateException(
                                    "Potential infinite loop in type substitution for computeAsMemberOf"
                                )
                            }
                            funcToSub = next
                            next = funcToSub.substitute(it)
                            cnt += 1
                        }
                        KSFunctionImpl(funcToSub, it)
                    }
                }
            } else KSFunctionErrorImpl(function)
        }
    }

    internal val isJvm = ktModule.targetPlatform.all { it is JvmPlatform }
}
