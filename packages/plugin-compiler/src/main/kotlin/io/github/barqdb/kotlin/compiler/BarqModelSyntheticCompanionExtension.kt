/*
 * Copyright 2020 Realm Inc.
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

package io.github.barqdb.kotlin.compiler

import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_COMPANION_NEW_INSTANCE_METHOD
import io.github.barqdb.kotlin.compiler.Names.BARQ_OBJECT_COMPANION_SCHEMA_METHOD
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.extensions.SyntheticResolveExtension

/**
 * Triggers generation of companion objects and ensures that the companion object implement the
 * [BarqObjectCompanion] interface for all classes marked with the [BarqObject] interface.
 *
 * TODO We most probably don't need this as the methods are already in the [BarqObjectCompanion]
 *  interface.
 * Also adds the [BarqObjectCompanion] methods as synthetic methods on the companion.
 */
@Suppress("EmptyFunctionBlock")
class BarqModelSyntheticCompanionExtension : SyntheticResolveExtension {

    override fun getSyntheticCompanionObjectNameIfNeeded(thisDescriptor: ClassDescriptor): Name? {
        return if (thisDescriptor.isBaseBarqObject) {
            DEFAULT_NAME_FOR_COMPANION_OBJECT
        } else {
            null
        }
    }

    override fun getSyntheticFunctionNames(thisDescriptor: ClassDescriptor): List<Name> {
        return when {
            thisDescriptor.isBarqObjectCompanion -> {
                listOf(
                    BARQ_OBJECT_COMPANION_SCHEMA_METHOD,
                    BARQ_OBJECT_COMPANION_NEW_INSTANCE_METHOD
                )
            }
            else -> {
                emptyList()
            }
        }
    }

    override fun generateSyntheticMethods(
        thisDescriptor: ClassDescriptor,
        name: Name,
        bindingContext: BindingContext,
        fromSupertypes: List<SimpleFunctionDescriptor>,
        result: MutableCollection<SimpleFunctionDescriptor>
    ) {
        when {
            thisDescriptor.isBarqObjectCompanion -> {
                val classDescriptor = thisDescriptor.containingDeclaration as ClassDescriptor

                when (name) {
                    BARQ_OBJECT_COMPANION_SCHEMA_METHOD -> result.add(
                        createBarqObjectCompanionSchemaGetterFunctionDescriptor(
                            thisDescriptor,
                            classDescriptor
                        )
                    )
                    BARQ_OBJECT_COMPANION_NEW_INSTANCE_METHOD -> result.add(
                        createBarqObjectCompanionNewInstanceFunctionDescriptor(
                            thisDescriptor,
                            classDescriptor
                        )
                    )
                }
            }
        }
    }

    private fun createBarqObjectCompanionSchemaGetterFunctionDescriptor(
        companionClass: ClassDescriptor,
        barqObjectClass: ClassDescriptor
    ): SimpleFunctionDescriptor {

        return SimpleFunctionDescriptorImpl.create(
            companionClass,
            Annotations.EMPTY,
            BARQ_OBJECT_COMPANION_SCHEMA_METHOD,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            companionClass.source
        ).apply {
            initialize(
                /* extensionReceiverParameter = */ null,
                /* dispatchReceiverParameter = */ companionClass.thisAsReceiverParameter,
                /* contextReceiverParameters = */ emptyList(),
                /* typeParameters = */ emptyList(),
                /* unsubstitutedValueParameters = */ emptyList(),
                // FIXME Howto resolve types from "runtime" module. Should be
                //  `io.github.barqdb.kotlin.internal.Table`, but doesn't seem to break as long as the actual
                //  implementation return type can be cast to this return type
                /* unsubstitutedReturnType = */ barqObjectClass.builtIns.anyType,
                /* modality = */ Modality.OPEN,
                /* visibility = */ DescriptorVisibilities.PUBLIC
            )
        }
    }

    private fun createBarqObjectCompanionNewInstanceFunctionDescriptor(
        companionClass: ClassDescriptor,
        barqObjectClass: ClassDescriptor
    ): SimpleFunctionDescriptor {

        return SimpleFunctionDescriptorImpl.create(
            companionClass,
            Annotations.EMPTY,
            BARQ_OBJECT_COMPANION_NEW_INSTANCE_METHOD,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            companionClass.source
        ).apply {
            initialize(
                /* extensionReceiverParameter = */ null,
                /* dispatchReceiverParameter = */ companionClass.thisAsReceiverParameter,
                /* contextReceiverParameters = */ emptyList(),
                /* typeParameters = */ emptyList(),
                /* unsubstitutedValueParameters = */ emptyList(),
                /* unsubstitutedReturnType = */ barqObjectClass.builtIns.anyType,
                /* modality = */ Modality.OPEN,
                /* visibility = */ DescriptorVisibilities.PUBLIC
            )
        }
    }
}
