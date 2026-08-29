package android.util;

/**
 * JVM stand-in for android.util.Rational — a plain immutable fraction. Real
 * arithmetic, since that is all it is.
 */
public final class Rational extends Number implements Comparable<Rational> {
    public static final Rational ZERO = new Rational(0, 1);
    public static final Rational NaN = new Rational(0, 0);
    public static final Rational POSITIVE_INFINITY = new Rational(1, 0);
    public static final Rational NEGATIVE_INFINITY = new Rational(-1, 0);

    private final int numerator;
    private final int denominator;

    public Rational(int numerator, int denominator) {
        // Android normalises sign onto the numerator and reduces by the gcd, so
        // 2/4 and 1/2 compare and print the same.
        if (denominator < 0) {
            numerator = -numerator;
            denominator = -denominator;
        }
        int divisor = gcd(Math.abs(numerator), denominator);
        if (divisor > 1) {
            numerator /= divisor;
            denominator /= divisor;
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public int getNumerator() { return numerator; }

    public int getDenominator() { return denominator; }

    public boolean isNaN() { return denominator == 0 && numerator == 0; }

    public boolean isInfinite() { return denominator == 0 && numerator != 0; }

    public boolean isFinite() { return denominator != 0; }

    public boolean isZero() { return numerator == 0 && denominator != 0; }

    @Override public float floatValue() { return (float) doubleValue(); }

    @Override public double doubleValue() { return (double) numerator / (double) denominator; }

    @Override public int intValue() { return denominator == 0 ? 0 : numerator / denominator; }

    @Override public long longValue() { return intValue(); }

    @Override
    public int compareTo(Rational other) { return Double.compare(doubleValue(), other.doubleValue()); }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Rational)) return false;
        Rational that = (Rational) other;
        return numerator == that.numerator && denominator == that.denominator;
    }

    @Override
    public int hashCode() { return numerator * 31 + denominator; }

    @Override
    public String toString() { return numerator + "/" + denominator; }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int t = b;
            b = a % b;
            a = t;
        }
        return a == 0 ? 1 : a;
    }
}
