/*
 * Copyright (c) 2025, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

package it.unibo.collektive.utils.common

import it.unibo.collektive.utils.common.AggregateFunctionNames.NO_ALIGN_ANNOTATION_FQ_NAME
import it.unibo.collektive.utils.logging.debug
import it.unibo.collektive.utils.logging.info
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
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.parents
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid

/**
 * Builds an [IrFunctionAccessExpression] that invokes the `context`
 * property getter on this [IrGetValue] representing a [it.unibo.collektive.aggregate.Field].
 *
 * @throws IllegalStateException if the current value is not assignable from [fieldClass].
 */
private fun IrGetValue.buildGetFieldContext(
    pluginContext: IrPluginContext,
    aggregateClass: IrClass,
    fieldClass: IrClass,
    getContext: IrFunction,
): IrFunctionAccessExpression {
    check(type.isAssignableFrom(fieldClass.defaultType)) {
        "Expected a Field, but got a: ${type.classFqName}"
    }
    return IrSingleStatementBuilder(pluginContext, Scope(getContext.symbol), startOffset, endOffset)
        .build {
            irCall(getContext.symbol, aggregateClass.defaultType)
                .apply { dispatchReceiver = this@buildGetFieldContext }
        }
}

/**
 * Attempts to retrieve a reference to the [it.unibo.collektive.aggregate.api.Aggregate]
 * execution context from the current [IrExpression].
 *
 * This function searches for a captured variable of either:
 * - The [it.unibo.collektive.aggregate.api.Aggregate] type (used directly), or
 * - The [it.unibo.collektive.aggregate.Field] type (from which the context is derived via the `.context` property).
 *
 * If a [it.unibo.collektive.aggregate.Field] reference is found,
 * a call to the `context` getter is injected into the IR.
 *
 * @param pluginContext the IR plugin context used to build new IR expressions
 * @param aggregateClass the IR class representing the `Aggregate<ID>` interface
 * @param fieldClass the IR class representing the `Field<ID, *>` interface
 * @param getContext the function symbol for the `Field.context` getter
 * @param logger an optional [MessageCollector] for debug logging
 * @return the [IrExpression] representing the aggregate context, or `null` if not found
 */
fun IrExpression.findAggregateReference(
    pluginContext: IrPluginContext,
    aggregateClass: IrClass,
    fieldClass: IrClass,
    getContext: IrFunction,
    logger: MessageCollector?,
): IrExpression? = findFirstCapturedVariableOfType(aggregateClass)
    ?: findFirstCapturedVariableOfType(fieldClass)
        ?.also { logger?.info("Found aggregate context in ${this.dumpKotlinLike()}: ${it.dumpKotlinLike()}") }
        ?.buildGetFieldContext(pluginContext, aggregateClass, fieldClass, getContext)
        ?.also { logger?.info("Field-mediated context in ${this.dumpKotlinLike()}: ${it.dumpKotlinLike()}") }

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
    logger?.debug("Detected annotations: $allAnnotations")
    return allAnnotations.any { it.type.classFqName?.asString() == NO_ALIGN_ANNOTATION_FQ_NAME }
}
