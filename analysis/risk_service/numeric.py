"""Decimal helpers for wire validation, exposure multiplication and final half-up rounding.

Statistics use NumPy float64. These helpers preserve exact decimal application rules,
not a second statistical engine or a Java-compatible random generator.
"""

from decimal import Context, Decimal, ROUND_HALF_EVEN, ROUND_HALF_UP, localcontext

PRECISION = Context(prec=34, rounding=ROUND_HALF_EVEN, Emin=-999999, Emax=999999)
ONE = Decimal(1)
ZERO = Decimal(0)
HUNDRED = Decimal(100)


def exact_add(a, b):
    digits = max(a.adjusted(), b.adjusted()) - min(a.as_tuple().exponent, b.as_tuple().exponent) + 3
    with localcontext(PRECISION) as ctx:
        ctx.prec = max(34, digits)
        return a + b


def exact_mul(a, b):
    with localcontext(PRECISION) as ctx:
        ctx.prec = max(34, len(a.as_tuple().digits) + len(b.as_tuple().digits))
        return a * b


def rounded(value, places):
    with localcontext(PRECISION) as ctx:
        ctx.prec = max(34, value.adjusted() + places + 3)
        return value.quantize(Decimal(1).scaleb(-places), rounding=ROUND_HALF_UP)
