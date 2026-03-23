package demo.fn;

import java.io.Serializable;
import java.util.function.Predicate;

public class DivisibleBy3Predicate implements Predicate<Long>, Serializable {
    @Override
    public boolean test(Long x) {
        return x % 3 == 0;
    }
}