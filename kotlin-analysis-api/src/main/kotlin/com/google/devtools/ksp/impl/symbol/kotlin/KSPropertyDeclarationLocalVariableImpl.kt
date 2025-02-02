package com.google.devtools.ksp.impl.symbol.kotlin

import com.google.devtools.ksp.common.KSObjectCache
import com.google.devtools.ksp.impl.symbol.kotlin.resolved.KSTypeReferenceResolvedImpl
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSExpectActual
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.google.devtools.ksp.symbol.KSPropertySetter
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSVisitor
import org.jetbrains.kotlin.analysis.api.symbols.KaLocalVariableSymbol
import org.jetbrains.kotlin.psi.KtProperty

class KSPropertyDeclarationLocalVariableImpl private constructor(
    private val ktLocalVariableSymbol: KaLocalVariableSymbol
) : KSPropertyDeclaration,
    AbstractKSDeclarationImpl(ktLocalVariableSymbol),
    KSExpectActual by KSExpectActualImpl(ktLocalVariableSymbol) {
    companion object : KSObjectCache<KaLocalVariableSymbol, KSPropertyDeclarationLocalVariableImpl>() {
        fun getCached(ktLocalVariableSymbol: KaLocalVariableSymbol) =
            cache.getOrPut(ktLocalVariableSymbol) { KSPropertyDeclarationLocalVariableImpl(ktLocalVariableSymbol) }
    }

    override fun asKSDeclaration(): KSDeclaration = this

    override val getter: KSPropertyGetter? = null

    override val setter: KSPropertySetter? = null

    override val extensionReceiver: KSTypeReference? = null

    override val type: KSTypeReference by lazy {
        (ktLocalVariableSymbol.psiIfSource() as? KtProperty)?.typeReference
            ?.let { KSTypeReferenceImpl.getCached(it, this) }
            ?: KSTypeReferenceResolvedImpl.getCached(ktLocalVariableSymbol.returnType, this)
    }

    override val isMutable: Boolean = !ktLocalVariableSymbol.isVal

    override val hasBackingField: Boolean = false

    override fun isDelegated(): Boolean = false

    override fun findOverridee() = null

    override fun asMemberOf(containing: KSType): KSType {
        TODO("Not yet implemented")
    }

    override val qualifiedName: KSName? = null

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertyDeclaration(this, data)
    }

    override fun defer(): Restorable? {
        return ktLocalVariableSymbol.defer(::getCached)
    }
}
