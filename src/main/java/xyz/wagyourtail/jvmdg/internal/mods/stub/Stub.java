package xyz.wagyourtail.jvmdg.internal.mods.stub;

import org.gradle.api.JavaVersion;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Stub {

    JavaVersion value();

    String desc() default "";

    Class<?>[] include() default {};

    boolean subtypes() default false;

    boolean subtypesOnly() default false;

    boolean returnDecendant() default false;
}