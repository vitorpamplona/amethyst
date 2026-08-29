package androidx.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** JVM stand-in for androidx.annotation.VisibleForTesting. */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface VisibleForTesting {
    int PRIVATE = 2;
    int PACKAGE_PRIVATE = 3;
    int PROTECTED = 4;
    int NONE = 5;

    int otherwise() default PRIVATE;
}
