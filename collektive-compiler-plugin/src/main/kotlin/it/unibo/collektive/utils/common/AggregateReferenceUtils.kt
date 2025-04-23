/*
 * Copyright (c) 2025, Danilo Pianini, Nicolas Farabegoli, Elisa Tronetti,
 * and all authors listed in the `build.gradle.kts` and the generated `pom.xml` file.
 *
 * This file is part of Collektive, and is distributed under the terms of the Apache License 2.0,
 * as described in the LICENSE file in this project's repository's top directory.
 */

package it.unibo.collektive.utils.common

import it.unibo.collektive.backend.visitors.AggregateRefChildrenVisitor
import it.unibo.collektive.utils.common.AggregateFunctionNames.NO_ALIGN_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.parents

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrBlock.findAggregateReference(pluginContext: IrPluginContext, aggregateClass: IrClass, fieldClass: IrClass, logger: MessageCollector): IrExpression? =
    statements.firstNotNullOfOrNull {
        when (it) {
            is IrCall ->
                findAggregateReference(pluginContext, aggregateClass, fieldClass, it, logger)
                    ?: findAggregateReference(pluginContext, aggregateClass, fieldClass, it.symbol.owner, logger)
            is IrVariable -> findAggregateReference(pluginContext, aggregateClass, fieldClass, it, logger)
            is IrTypeOperatorCall -> findAggregateReference(pluginContext, aggregateClass, fieldClass, it, logger)
            is IrWhen -> findAggregateReference(pluginContext, aggregateClass, fieldClass, it, logger)
            else -> null // collectAggregateReference(aggregateContextClass, it)
        }
    }

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrExpression.findAggregateReference(
    pluginContext: IrPluginContext,
    aggregateContextClass: IrClass,
    fieldClass: IrClass,
    logger: MessageCollector
): IrExpression? = when (this) {
    is IrBlock -> findAggregateReference(pluginContext, aggregateContextClass, fieldClass, logger)
    is IrGetValue ->
        findAggregateReference(pluginContext, aggregateContextClass, fieldClass, this, logger)
            ?: findAggregateReference(pluginContext, aggregateContextClass, fieldClass, symbol.owner, logger)
    else -> findAggregateReference(pluginContext, aggregateContextClass, fieldClass, this, logger)
}


/**
 * Retrieve the aggregate context reference by looking in all the function call in the element found.
 */
fun IrElement.findAggregateReference(
    pluginContext: IrPluginContext,
    aggregateClass: IrClass,
    fieldClass: IrClass,
    logger: MessageCollector
): IrExpression? =
    buildList { accept(AggregateRefChildrenVisitor(pluginContext, aggregateClass, fieldClass, this, logger), null) }
        .firstOrNull()

fun IrFunction.isAggregate(aggregateClass: IrClass, fieldClass: IrClass, logger: MessageCollector? = null): Boolean {
    // Function is annotated
    return isConcrete && !isAnnotatedWithNoAlign(logger) &&
        listOf(aggregateClass, fieldClass).any { irClass: IrClass ->
            val type = irClass.defaultType
            extensionReceiverParameter?.type?.isAssignableFrom(type)
                ?: dispatchReceiverParameter?.type?.isAssignableFrom(type)
                ?: valueParameters.any { it.type.isAssignableFrom(type) }
        }

}

val IrFunction.isAbstract get() = this is IrSimpleFunction && modality == Modality.ABSTRACT
val IrFunction.isConcrete get() = !isAbstract

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrFunction.isAnnotatedWithNoAlign(logger: MessageCollector? = null): Boolean {
    val allAnnotations = annotations + parents.flatMap { (it as? IrAnnotationContainer)?.annotations.orEmpty() }
    return allAnnotations.any { it.type.classFqName?.asString() == NO_ALIGN_ANNOTATION_FQ_NAME }
}
