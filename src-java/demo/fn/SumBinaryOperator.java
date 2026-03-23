package demo.fn;

import java.io.Serializable;
import java.util.function.BinaryOperator;

public class SumBinaryOperator implements BinaryOperator<Long>, Serializable {
    @Override
    public Long apply(Long a, Long b) {
        return a + b;
    }
}