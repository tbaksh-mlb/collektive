/*
 * Copyright (c) 2025, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

package it.unibo.collektive.backend.transformers

import it.unibo.collektive.utils.common.isAggregate
import it.unibo.collektive.utils.common.isConcrete
import it.unibo.collektive.utils.logging.debug
import it.unibo.collektive.utils.stack.StackFunctionCall
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

/**
 * Looking for the aggregate function definition, which is the one that contains the function calls
 * and the branches that have to be aligned. The body of this function call will be
 * transformed by adding the alignedOn function when necessary.
 */
class AggregateFunctionTransformer(
    private val pluginContext: IrPluginContext,
    private val logger: MessageCollector,
    private val aggregateClass: IrClass,
    private val fieldClass: IrClass,
    private val alignRawFunction: IrFunction,
    private val dealignFunction: IrFunction,
    private val projectFunction: IrFunction,
) : IrElementTransformerVoid() {

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (declaration.isConcrete && declaration.isAggregate(aggregateClass, fieldClass, logger)) {
            logger.debug(declaration.dumpKotlinLike() + " is an aggregate function")
            /*
             This transformation is needed to project field inside the `alignOn` function called directly by the user.
             This is made before the alignment transformation because of optimization reasons:
             if the field projection is made after the alignment step, this means that for each field call
             we made a projection, which is not necessary.
             */
            declaration.transformChildren(
                ProjectionTransformer(pluginContext, logger, projectFunction),
                null,
            )
            /*
             This transformation is needed to add the `alignRaw` and `dealign` function call to the aggregate functions.
             */
            declaration.transformChildren(
                AlignmentTransformer(
                    pluginContext,
                    aggregateClass,
                    fieldClass,
                    declaration,
                    alignRawFunction,
                    dealignFunction,
                    logger,
                ),
                StackFunctionCall(),
            )
        }
        return super.visitFunction(declaration)
    }
}
