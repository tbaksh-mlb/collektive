/*
 * Copyright (c) 2025, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

package it.unibo.collektive.backend.transformers

import it.unibo.collektive.utils.common.AggregateFunctionNames.AGGREGATE_API_PACKAGE
import it.unibo.collektive.utils.common.isAssignableFrom
import it.unibo.collektive.utils.stack.StackFunctionCall
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

/**
 * Looking for the aggregate function call, which is the one that contains the function calls
 * and the branches that have to be aligned. The body of this function call will be
 * transformed by adding the alignedOn function when necessary.
 */
class AggregateCallTransformer(
    private val pluginContext: IrPluginContext,
    private val logger: MessageCollector,
    private val aggregateClass: IrClass,
    private val fieldClass: IrClass,
    private val alignRawFunction: IrFunction,
    private val dealignFunction: IrFunction,
    private val projectFunction: IrFunction,
) : IrElementTransformerVoid() {

    override fun visitFunction(declaration: IrFunction): IrStatement {
        val allowedClass = declaration.fqNameWhenAvailable.let {
            it != fieldClass.fqNameWhenAvailable && it != aggregateClass.fqNameWhenAvailable
        }
        val inApiPackage = declaration.fqNameWhenAvailable?.asString()?.startsWith(AGGREGATE_API_PACKAGE) == true
        val shouldAlign = allowedClass && !inApiPackage && declaration.isAggregate()
        if (shouldAlign) {
            /*
             This transformation is needed to project field inside the `alignOn` function called directly by the user.
             This is made before the alignment transformation because of optimization reasons:
             if the field projection is made after the alignment step, this means that for each field call
             we made a projection, which is not necessary.
             */
            declaration.transformChildren(
                FieldTransformer(pluginContext, logger, aggregateClass, projectFunction),
                null,
            )
            /*
             This transformation is needed to add the `alignRaw` and `dealign` function call to the aggregate functions.
             */
            declaration.transformChildren(
                AlignmentTransformer(
                    pluginContext,
                    aggregateClass,
                    declaration,
                    alignRawFunction,
                    dealignFunction,
                ),
                StackFunctionCall(),
            )
        }
        return super.visitFunction(declaration)
    }

    private fun IrFunction.isAggregate() =
        listOf(aggregateClass, fieldClass).any { irClass ->
            val type = irClass.defaultType
            extensionReceiverParameter?.type?.isAssignableFrom(type)
                ?: dispatchReceiverParameter?.type?.isAssignableFrom(type)
                ?: valueParameters.any { it.type.isAssignableFrom(type) }
        }
}
