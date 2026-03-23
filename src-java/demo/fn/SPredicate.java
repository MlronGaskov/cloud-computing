package demo.fn;

import java.io.Serializable;
import java.util.function.Predicate;

@FunctionalInterface
public interface SPredicate<T> extends Predicate<T>, Serializable {}