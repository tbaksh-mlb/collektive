/*
 * Copyright (c) 2025, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

package it.unibo.collektive.utils.common

import it.unibo.collektive.utils.common.AggregateFunctionNames.NO_ALIGN_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.IrSingleStatementBuilder
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.parents
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

// @OptIn(UnsafeDuringIrConstructionAPI::class)
// private fun IrBlock.findAggregateReference(pluginContext: IrPluginContext, aggregateClass: IrClass, fieldClass: IrClass, logger: MessageCollector): IrExpression? =
//    statements.firstNotNullOfOrNull {
//        when (it) {
//            is IrCall ->
//                findAggregateReference(pluginContext, aggregateClass, fieldClass, it, logger)
//                    ?: findAggregateReference(pluginContext, aggregateClass, fieldClass, it.symbol.owner, logger)
//            is IrVariable -> findAggregateReference(pluginContext, aggregateClass, fieldClass, it, logger)
//            is IrTypeOperatorCall -> findAggregateReference(pluginContext, aggregateClass, fieldClass, it, logger)
//            is IrWhen -> findAggregateReference(pluginContext, aggregateClass, fieldClass, it, logger)
//            else -> null // collectAggregateReference(aggregateContextClass, it)
//        }
//    }
//
// @OptIn(UnsafeDuringIrConstructionAPI::class)
// private fun IrExpression.findAggregateReference(
//    pluginContext: IrPluginContext,
//    aggregateContextClass: IrClass,
//    fieldClass: IrClass,
//    logger: MessageCollector
// ): IrExpression? = when (this) {
//    is IrBlock -> findAggregateReference(pluginContext, aggregateContextClass, fieldClass, logger)
//    is IrGetValue ->
//        findAggregateReference(pluginContext, aggregateContextClass, fieldClass, this, logger)
//            ?: findAggregateReference(pluginContext, aggregateContextClass, fieldClass, symbol.owner, logger)
//    else -> findAggregateReference(pluginContext, aggregateContextClass, fieldClass, this, logger)
// }

/**
 * Retrieve the aggregate context reference by looking in all the function call in the element found.
 */
fun IrExpression.findAggregateReference(
    pluginContext: IrPluginContext,
    aggregateClass: IrClass,
    fieldClass: IrClass,
    getContext: IrFunction,
    logger: MessageCollector,
): IrExpression? = findFirstCapturedVariableOfType(aggregateClass)
    ?: findFirstCapturedVariableOfType(fieldClass)
        ?.buildGetFieldContext(pluginContext, aggregateClass, fieldClass, getContext)

private fun IrExpression.findFirstCapturedVariableOfType(targetType: IrClass): IrGetValue? {
    var result: IrGetValue? = null
    accept(
        object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                if (result == null) {
                    element.acceptChildren(this, null)
                }
            }

            override fun visitGetValue(expression: IrGetValue) {
                if (result == null) {
                    if (expression.type.isAssignableFrom(targetType.defaultType)) {
                        result = expression
                    }
                }
            }
        },
        null,
    )
    return result
}

/**
 * Builds an [IrFunctionAccessExpression] that invokes the `context` property getter on this [IrGetValue] representing a [Field].
 *
 * @throws IllegalStateException if the current value is not assignable from [fieldClass].
 */
private fun IrGetValue.buildGetFieldContext(
    pluginContext: IrPluginContext,
    aggregateClass: IrClass,
    fieldClass: IrClass,
    getContext: IrFunction,
): IrFunctionAccessExpression {
    check(type.isAssignableFrom(fieldClass.defaultType)){
        "Expected a Field, but got a: ${type.classFqName}"
    }
    return IrSingleStatementBuilder(pluginContext, Scope(getContext.symbol), startOffset, endOffset)
        .build {
            irCall(getContext.symbol, aggregateClass.defaultType)
                .apply { dispatchReceiver = this@buildGetFieldContext }
        }
}

/**
 * Determines whether this function operates on an
 * [it.unibo.collektive.aggregate.api.Aggregate] or [it.unibo.collektive.aggregate.Field],
 * based on receiver or parameter types,
 * and is not marked with the [it.unibo.collektive.aggregate.api.NoAlign] annotation.
 *
 * @return true if the function should be considered an aggregate-aware DSL construct.
 */
fun IrFunction.isAggregate(aggregateClass: IrClass, fieldClass: IrClass, logger: MessageCollector? = null): Boolean =
    !isAnnotatedWithNoAlign(logger) &&
        listOf(aggregateClass, fieldClass).any { irClass: IrClass ->
            val type = irClass.defaultType
            extensionReceiverParameter?.type?.isAssignableFrom(type)
                ?: dispatchReceiverParameter?.type?.isAssignableFrom(type)
                ?: valueParameters.any { it.type.isAssignableFrom(type) }
        }

private fun IrFunction.isAnnotatedWithNoAlign(logger: MessageCollector? = null): Boolean {
    val allAnnotations = annotations + parents.flatMap { (it as? IrAnnotationContainer)?.annotations.orEmpty() }
    return allAnnotations.any { it.type.classFqName?.asString() == NO_ALIGN_ANNOTATION_FQ_NAME }
}
