/*
 * Copyright (c) 2025, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

package it.unibo.collektive.backend.visitors

import it.unibo.collektive.utils.common.isAssignableFrom
import it.unibo.collektive.utils.logging.info
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrSingleStatementBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.receiverAndArgs
import org.jetbrains.kotlin.ir.visitors.IrVisitor

/**
 * Class that visit all the children of the IR, looking for the
 * AggregateContext class.
 */
class AggregateRefChildrenVisitor(
    private val pluginContext: org.jetbrains.kotlin.backend.common.extensions.IrPluginContext,
    private val aggregateClass: IrClass,
    private val fieldClass: IrClass,
    private val elements: MutableList<IrExpression>,
    private val logger: MessageCollector,
) : IrVisitor<Unit, Nothing?>() {

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private val getContextSymbol = checkNotNull(fieldClass.getPropertyGetter("context"))

    // Visit all the children of the root element
    override fun visitElement(element: IrElement, data: Nothing?) {
        element.acceptChildren(this, data)
    }

    // Search in each call if in its receiver or arguments there is the reference to
    // the aggregate context
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitCall(expression: IrCall, data: Nothing?) {
        var fromField = false
        val aggregateContextRef = expression
            .receiverAndArgs()
            .find { it.type.isAssignableFrom(aggregateClass.defaultType) }
            ?: expression.receiverAndArgs()
                .find { it.type.isAssignableFrom(fieldClass.defaultType) }
                ?.let { fieldExpression: IrExpression ->
                    fromField = true
                    IrSingleStatementBuilder(pluginContext, Scope(getContextSymbol), fieldExpression.startOffset, fieldExpression.endOffset)
                        .build {
                            irCall(getContextSymbol).apply {
                                this.type = aggregateClass.defaultType
                                dispatchReceiver = fieldExpression
                            }
                        }
                }
        if (fromField) {
            logger.info("Found aggregate context reference in field: ${expression.dumpKotlinLike()}")
            logger.info("Context is: ${aggregateContextRef?.dumpKotlinLike()}")
        }
        aggregateContextRef?.let { elements.add(it) } ?: super.visitCall(expression, data)
    }
}

