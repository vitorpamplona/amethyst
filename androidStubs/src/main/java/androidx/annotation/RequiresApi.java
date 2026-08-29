package androidx.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JVM stand-in for androidx.annotation.RequiresApi. Inert: there is no API
 * level on desktop, and Build.VERSION.SDK_INT reports the compile target, so
 * guarded code takes its modern branch.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface RequiresApi {
    int value() default 1;

    int api() default 1;
}
