package demo.fn;

public final class Lambdas {
    private Lambdas() {}

    public static SFunction<Long, Long> times2() {
        return x -> x * 2;
    }

    public static SPredicate<Long> divisibleBy3() {
        return x -> x % 3 == 0;
    }

    public static SBinaryOperator<Long> sum() {
        return (a, b) -> a + b;
    }
}