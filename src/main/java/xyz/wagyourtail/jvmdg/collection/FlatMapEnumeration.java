package xyz.wagyourtail.jvmdg.collection;

import xyz.wagyourtail.jvmdg.util.Function;

import java.util.Enumeration;

public class FlatMapEnumeration<T, E> implements Enumeration<E> {
    private final Enumeration<T> enumeration;
    private final Function<T, Enumeration<E>> mapper;

    private Enumeration<E> currentEnumeration = null;

    public FlatMapEnumeration(Enumeration<T> enumeration, Function<T, Enumeration<E>> mapper) {
        this.enumeration = enumeration;
        this.mapper = mapper;
    }

    @Override
    public boolean hasMoreElements() {
        while (currentEnumeration == null || !currentEnumeration.hasMoreElements()) {
            if (enumeration.hasMoreElements()) {
                currentEnumeration = mapper.apply(enumeration.nextElement());
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    public E nextElement() {
        if (!hasMoreElements()) {
            return null;
        }
        return currentEnumeration.nextElement();
    }

}
