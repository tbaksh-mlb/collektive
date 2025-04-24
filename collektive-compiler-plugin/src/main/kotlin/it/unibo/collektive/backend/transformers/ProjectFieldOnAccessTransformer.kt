/*
 * Copyright (c) 2025, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

package it.unibo.collektive.backend.transformers

import it.unibo.collektive.utils.common.AggregateFunctionNames.FIELD_CLASS
import it.unibo.collektive.utils.logging.debug
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.builders.IrSingleStatementBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.putArgument
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName

internal class ProjectFieldOnAccessTransformer(
    private val logger: MessageCollector,
    private val pluginContext: IrPluginContext,
    private val projectFunction: IrFunction,
//    private val aggregateReference: IrExpression,
) : IrElementTransformerVoid() {
    override fun visitGetValue(expression: IrGetValue): IrExpression {
        if (expression.type.classFqName == FqName(FIELD_CLASS)) {
            logger.debug("This expression returns a field: ${expression.dumpKotlinLike()}")
            return wrapInProjectFunction(expression) // , aggregateReference)
        }
        return super.visitGetValue(expression)
    }

    private fun wrapInProjectFunction(fieldExpression: IrGetValue): IrExpression = IrSingleStatementBuilder(
        pluginContext,
        Scope(fieldExpression.symbol),
        fieldExpression.startOffset,
        fieldExpression.endOffset,
    )
        .irCall(projectFunction).apply {
            // Set the return type
            logger.debug("Projecting: ${fieldExpression.dumpKotlinLike()}")
            this.type = fieldExpression.type
            // Set generics type of the `alignOn` function
//            putTypeArgument(0, fieldExpression.type.)
//            putTypeArgument(0, type)
            // Set extension receiver
//            extensionReceiver = dispatchReceiver
            // Set function argument
            putArgument(projectFunction.valueParameters.single(), fieldExpression)
        }
}
