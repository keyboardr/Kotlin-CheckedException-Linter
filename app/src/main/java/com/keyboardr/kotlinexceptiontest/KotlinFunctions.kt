@file:Suppress("unused")

package com.keyboardr.kotlinexceptiontest

import com.keyboardr.kotlinexceptiontest.JavaFunctions.*
import kotlinx.coroutines.*
import kotlin.jvm.Throws

private val supervisorJob = SupervisorJob()

private val safeScope: CoroutineScope =
    CoroutineScope(supervisorJob + CoroutineExceptionHandler { _, throwable ->
        handleException(throwable)
    })

private fun handleException(throwable: Throwable) {
    when (throwable) {
        is CheckedException -> {
            println("caught in safeScope")
            supervisorJob.cancel("Checked exception thrown", throwable)
        }
        is CancellationException -> println("Canceled")
        else -> throw throwable
    }
}

fun interface CheckedBlock<T> {
    @Throws(CheckedException::class)
    operator fun invoke(): T

    fun irrelevantOtherMethod() {

    }
}

fun callAssignAndDontCatch() {
    val result = doThingThatReturnsThrowing() // should break
}

fun callAndDontCatch() {
    doThingThrowingException() // should break
}

fun callInTryCatch() {
    try {
        doThingThrowingException()
    } catch (e: CheckedException) {
        // We're good
    }
}

@Throws(CheckedException::class)
fun callButDeclareThrows() {
    doThingThrowingException()
}

fun doCallSafely() {
    callSafely {
        doThingThrowingException()
    }
}
fun doCallSafelyWithReturn() {
    val result = callSafely {
        doThingThatReturnsThrowing()
    }
}

fun doCallInSafeScope() {
    callInSafeScope {
        doThingThrowingException()
    }
}

fun callWithLambdaType() {
    callSafely {
        doThingThrowingException()
    }
}

fun doCallNotSafe() {
    callNotSafe {
        doThingThrowingException() // should break
    }
}

fun <T> callSafely(block: CheckedBlock<T>) =
    try {
        Result.success(block())
    } catch (e: CheckedException) {
        Result.failure(e)
    }

@SafeForCheckedException
fun callInSafeScope(block: suspend () -> Unit) {
    safeScope.launch {
        block()
    }
}

@SafeForCheckedException
fun <T> callInSafeScopeForResultAsync(block: suspend () -> T): Deferred<Result<T>> {

    return safeScope.async {
        runCatching { block() }.also {
            it.exceptionOrNull()?.let { e -> handleException(e) }
        }
    }
}

fun callNotSafe(block: () -> Unit) {
    block()
}
