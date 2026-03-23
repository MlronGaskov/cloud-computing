package demo.fn;

import java.io.Serializable;
import java.util.function.BinaryOperator;

@FunctionalInterface
public interface SBinaryOperator<T> extends BinaryOperator<T>, Serializable {}