/*
 * Copyright 2023 Realm Inc.
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

package io.github.barqdb.kotlin.compiler.fir.model

import io.github.barqdb.kotlin.compiler.Names
import io.github.barqdb.kotlin.compiler.fir.BarqPluginGeneratorKey
import io.github.barqdb.kotlin.compiler.isBaseBarqObject
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

/**
 * Name of methods for which we will generate default implementations if the users have not
 * defined their own.
 */
private val barqObjectDefaultMethods = setOf(
    Names.BARQ_OBJECT_TO_STRING_METHOD,
    Names.BARQ_OBJECT_EQUALS,
    Names.BARQ_OBJECT_HASH_CODE,
)

/**
 * Fir extension that adds `toString`, `equals` and `hashCode` to BarqObject-classes.
 */
class ObjectExtension(session: FirSession) : FirDeclarationGenerationExtension(session) {
    override fun getCallableNamesForClass(
        classSymbol: FirClassSymbol<*>,
        context: MemberGenerationContext
    ): Set<Name> {
        return if (classSymbol.isBaseBarqObject) {
            val methodNames: List<Name> = classSymbol.declarationSymbols.filterIsInstance<FirNamedFunctionSymbol>().map { it.name }
            barqObjectDefaultMethods.filter { !methodNames.contains(it) }.toSet()
        } else {
            emptySet()
        }
    }

    override fun generateFunctions(
        callableId: CallableId,
        context: MemberGenerationContext?
    ): List<FirNamedFunctionSymbol> {
        val owner = context?.owner ?: return emptyList()
        return when (callableId.callableName) {
            Names.BARQ_OBJECT_TO_STRING_METHOD ->
                listOf(
                    createMemberFunction(
                        owner,
                        BarqPluginGeneratorKey,
                        callableId.callableName,
                        session.builtinTypes.stringType.type,
                    ) {
                        modality = Modality.OPEN
                    }.symbol
                )
            Names.BARQ_OBJECT_EQUALS ->
                listOf(
                    createMemberFunction(
                        owner,
                        BarqPluginGeneratorKey,
                        callableId.callableName,
                        session.builtinTypes.booleanType.type,
                    ) {
                        modality = Modality.OPEN
                        valueParameter(Name.identifier("other"), session.builtinTypes.nullableAnyType.type)
                    }.symbol
                )
            Names.BARQ_OBJECT_HASH_CODE ->
                listOf(
                    createMemberFunction(
                        owner,
                        BarqPluginGeneratorKey,
                        callableId.callableName,
                        session.builtinTypes.intType.type,
                    ) {
                        modality = Modality.OPEN
                    }.symbol
                )
            else -> emptyList()
        }
    }
}
