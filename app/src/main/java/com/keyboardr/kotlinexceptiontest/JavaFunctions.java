package com.keyboardr.kotlinexceptiontest;

public class JavaFunctions {


    public static void doThingThrowingException() throws CheckedException {
        throw new CheckedException();
    }

    public static int doThingThatReturnsThrowing() throws CheckedException {
        return 10;
    }

    public static void callButDeclareThrows() throws CheckedException {
        doThingThrowingException();
    }

    public static void callInTryCatch() {
        try {
            doThingThrowingException();
            System.out.println("I won't get run");
        } catch (CheckedException e) {
            // Don't worry we're good.
        } finally {
            System.out.println("This will get called no matter what");
        }
    }

    public static void doBadThings() {
        //doThingThrowingException();
    }
}
