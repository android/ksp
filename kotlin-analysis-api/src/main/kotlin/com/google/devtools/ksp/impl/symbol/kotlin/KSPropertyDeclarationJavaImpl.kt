package com.google.devtools.ksp.impl.symbol.kotlin

import com.google.devtools.ksp.common.KSObjectCache
import com.google.devtools.ksp.common.impl.KSNameImpl
import com.google.devtools.ksp.impl.ResolverAAImpl
import com.google.devtools.ksp.impl.symbol.kotlin.resolved.KSTypeReferenceResolvedImpl
import com.google.devtools.ksp.symbol.*
import org.jetbrains.kotlin.analysis.api.symbols.KaJavaFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility

class KSPropertyDeclarationJavaImpl private constructor(val ktJavaFieldSymbol: KaJavaFieldSymbol) :
    KSPropertyDeclaration,
    AbstractKSDeclarationImpl(ktJavaFieldSymbol),
    KSExpectActual by KSExpectActualImpl(ktJavaFieldSymbol) {
    companion object : KSObjectCache<KaJavaFieldSymbol, KSPropertyDeclaration>() {
        fun getCached(ktJavaFieldSymbol: KaJavaFieldSymbol): KSPropertyDeclaration =
            cache.getOrPut(ktJavaFieldSymbol) { KSPropertyDeclarationJavaImpl(ktJavaFieldSymbol) }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val getter: KSPropertyGetter?
        get() = null

    override val setter: KSPropertySetter?
        get() = null

    override val extensionReceiver: KSTypeReference?
        get() = null

    override val type: KSTypeReference by lazy {
        KSTypeReferenceResolvedImpl.getCached(ktJavaFieldSymbol.returnType, this@KSPropertyDeclarationJavaImpl)
    }

    override val isMutable: Boolean
        get() = !modifiers.contains(Modifier.FINAL)

    override val hasBackingField: Boolean
        get() = true

    override fun isDelegated(): Boolean {
        return false
    }

    override fun findOverridee(): KSPropertyDeclaration? {
        return null
    }

    override fun asMemberOf(containing: KSType): KSType {
        return ResolverAAImpl.instance.computeAsMemberOf(this, containing)
    }

    override val typeParameters: List<KSTypeParameter>
        get() = emptyList()

    override val qualifiedName: KSName? by lazy {
        KSNameImpl.getCached("${this.parentDeclaration!!.qualifiedName!!.asString()}.${simpleName.asString()}")
    }

    override val packageName: KSName
        get() = KSNameImpl.getCached(ktJavaFieldSymbol.callableId?.packageName?.asString() ?: "")

    override val origin: Origin
        get() = mapAAOrigin(ktJavaFieldSymbol)

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertyDeclaration(this, data)
    }

    override fun defer(): Restorable? {
        return ktJavaFieldSymbol.defer(::getCached)
    }
}

internal fun KaJavaFieldSymbol.toModifiers(): Set<Modifier> {
    val result = mutableSetOf<Modifier>()
    if (visibility != KaSymbolVisibility.PACKAGE_PRIVATE) {
        result.add(visibility.toModifier())
    }
    if (isStatic) {
        result.add(Modifier.JAVA_STATIC)
        result.add(Modifier.FINAL)
    }
    // Analysis API returns open for static members which should be ignored.
    if (!isStatic || modality != KaSymbolModality.OPEN) {
        result.add(modality.toModifier())
    }
    return result
}
