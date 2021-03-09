@file:Suppress("UnstableApiUsage")

package com.keyboardr.kotlinexceptiontest.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.intellij.lang.Language
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.uast.*

class CheckedExceptionNotCaughtDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {

            override fun visitCallExpression(node: UCallExpression) {
                if (!isKotlin(node.lang)) return
                val method = node.resolve() ?: return
                if (method.throwsList.referenceElements.any { it.qualifiedName == CHECKED_EXCEPTION_FQN }) {
                    if (!handlesCheckedException(node) && !inSafeLambda(node)) {
                        context.reportUsage(
                            problemLocation = context.getCallLocation(
                                node,
                                includeReceiver = false,
                                includeArguments = false
                            ),
                            statementLocation = context.getCallLocation(
                                node,
                                includeReceiver = true,
                                includeArguments = true
                            )
                        )
                    }
                }
            }

            private fun inSafeLambda(node: UCallExpression): Boolean {
                // Check if lambda is SAM that declares `throws`
                val lambda = node.getParentOfType<ULambdaExpression>(
                    ULambdaExpression::class.java,
                    strict = true
                ) ?: return false
                val functionalInterfaceType = lambda.functionalInterfaceType
                if (functionalInterfaceType is PsiClassType) {
                    val functionDeclaration = functionalInterfaceType.resolve()?.methods
                        ?.filter { it.hasModifier(JvmModifier.ABSTRACT) }
                        ?.takeIf { it.size == 1 }
                        ?.first()
                    if (functionDeclaration != null) {
                        val thrownTypes = functionDeclaration.throwsList.referencedTypes
                        if (containsCheckedException(listOf(*thrownTypes))) {
                            return true
                        }
                    }
                }

                // Check for safe annotation
                val higherOrderMethod =
                    (lambda.uastParent as? UCallExpression)?.resolve() ?: return false
                return higherOrderMethod.hasAnnotation(SAFE_FOR_CHECKED_EXCEPTION_FQN)
            }


            private fun handlesCheckedException(node: UElement): Boolean {
                // Ensure that the caller is handling a Checked exception
                // First check to see if we're inside a try/catch which catches a CheckedException
                // (or some wider exception than that). Check for nested try/catches too.
                var parent = node
                while (true) {
                    val tryCatch =
                        parent.getParentOfType<UTryExpression>(UTryExpression::class.java, true)
                            ?: break
                    for (catchClause in tryCatch.catchClauses) {
                        if (containsCheckedException(catchClause.types)) {
                            return true
                        }
                    }
                    parent = tryCatch
                }
                // If not, check to see if the method itself declares that it throws a
                // CheckedException or something wider.
                val declaration = parent.getParentOfType<UMethod>(UMethod::class.java, false)
                if (declaration != null) {
                    val thrownTypes = declaration.throwsList.referencedTypes
                    if (containsCheckedException(listOf(*thrownTypes))) {
                        return true
                    }
                }
                return false
            }

            private fun containsCheckedException(types: List<PsiType>) =
                types.any { (it as? PsiClassType)?.resolve()?.qualifiedName == CHECKED_EXCEPTION_FQN }


        }
    }


    companion object {
        private const val CHECKED_EXCEPTION_FQN =
            "com.keyboardr.kotlinexceptiontest.CheckedException"
        private const val SAFE_FOR_CHECKED_EXCEPTION_FQN =
            "com.keyboardr.kotlinexceptiontest.SafeForCheckedException"

        private val IMPLEMENTATION =
            Implementation(CheckedExceptionNotCaughtDetector::class.java, Scope.JAVA_FILE_SCOPE)

        @JvmField
        val CHECKED_EXCEPTION_NOT_CAUGHT = Issue.create(
            id = "CheckedExceptionNotCaught",
            briefDescription = "Code must catch `CheckedException` or declare that it throws",
            explanation = """
        Kotlin does not support checked Exceptions, so we should not rely on Exceptions to propagate errors in our application.
        More details in: https://kotlinlang.org/docs/reference/exceptions.html
    """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )

        val ISSUES = listOf(CHECKED_EXCEPTION_NOT_CAUGHT)

        private fun JavaContext.reportUsage(
            problemLocation: Location,
            statementLocation: Location = problemLocation
        ) {
            report(
                CHECKED_EXCEPTION_NOT_CAUGHT,
                problemLocation,
                "Must catch `CheckedException` or declare throws",
                lintFix {
                    group {
                        composite(
                            name = "Surround with try/catch"
                        ) {
                            replace(
                                ReplaceTarget.Beginning,
                                with = "try {",
                                range = statementLocation,
                                options = autoFix
                            )
                            replace(
                                ReplaceTarget.End,
                                with = "} catch(e: $CHECKED_EXCEPTION_FQN){kotlin.TODO()}",
                                range = statementLocation,
                            ) {
                                autoFix()
                                reformat = true
                                shortenNames = true
                            }
                        }

                        composite(name = "Surround with callSafely()") {
                            replace(
                                ReplaceTarget.Beginning,
                                with = "callSafely {",
                                range = statementLocation,
                                options = autoFix
                            )
                            replace(
                                ReplaceTarget.End,
                                with = "}",
                                range = statementLocation,
                            ) {
                                autoFix()
                                reformat = true
                                shortenNames = true
                            }
                        }
                    }
                }
            )
        }
    }
}
