package demo.fn;

import java.io.Serializable;
import java.util.function.Function;

public class Times2Function implements Function<Long, Long>, Serializable {
    @Override
    public Long apply(Long x) {
        return x * 2;
    }
}