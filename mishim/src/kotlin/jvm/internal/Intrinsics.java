package kotlin.jvm.internal;

/**
 * Minimal stand-in for kotlin-stdlib's Intrinsics.
 *
 * The Kotlin compiler emits calls to Intrinsics.checkNotNullParameter (and a few
 * siblings) for non-null parameters. The real class is not in MiSound's dex --
 * R8 inlined/renamed it to short names like kotlin.jvm.internal.a -- so the shim
 * classes compiled from Kotlin resolve this fully-qualified name instead. This
 * implementation only enforces what a null check would: nothing subtle.
 *
 * Written in Java on purpose: a Kotlin implementation would make the Kotlin
 * compiler emit further Intrinsics references into Intrinsics itself.
 */
public final class Intrinsics {
    private Intrinsics() {
    }

    public static void checkNotNullParameter(Object value, String paramName) {
        if (value == null) {
            throwParameterIsNullException(paramName);
        }
    }

    public static void checkNotNullExpressionValue(Object value, String expression) {
        if (value == null) {
            throwExpressionIsNullException(expression);
        }
    }

    public static void checkNotNull(Object value) {
        if (value == null) {
            throw new NullPointerException();
        }
    }

    public static void checkNotNull(Object value, String message) {
        if (value == null) {
            throw new NullPointerException(message);
        }
    }

    public static void checkParameterIsNotNull(Object value, String paramName) {
        if (value == null) {
            throwParameterIsNullException(paramName);
        }
    }

    public static void checkExpressionValueIsNotNull(Object value, String expression) {
        if (value == null) {
            throwExpressionIsNullException(expression);
        }
    }

    public static void checkFieldIsNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("Field " + fieldName + " is null");
        }
    }

    public static void checkReturnedValueIsNotNull(Object value, String methodName) {
        if (value == null) {
            throw new IllegalStateException("Method " + methodName + " returned null");
        }
    }

    private static void throwParameterIsNullException(String paramName) {
        throw new NullPointerException("Parameter specified as non-null is null: " + paramName);
    }

    private static void throwExpressionIsNullException(String expression) {
        throw new NullPointerException("Expression is null: " + expression);
    }
}