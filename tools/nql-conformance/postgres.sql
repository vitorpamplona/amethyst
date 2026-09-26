-- The functions NIP-FF's implementation notes give a PostgreSQL implementation for
-- the rules PostgreSQL doesn't already follow. Install them once; the translator
-- then emits calls to them in place of the NQL construct named in each comment.

-- CAST(x AS INTEGER) for TEXT x: the whole text is an optionally signed run of digits that fits, or NULL.
CREATE FUNCTION nql_text_to_integer(x text) RETURNS int8 LANGUAGE sql IMMUTABLE STRICT AS $$
  SELECT CASE WHEN x ~ '^[+-]?[0-9]+$' AND pg_input_is_valid(x, 'int8') THEN x::int8 END
$$;

-- CAST(x AS REAL) for TEXT x: the whole text is a decimal number whose value is finite, and non-zero unless it is zero, or NULL.
CREATE FUNCTION nql_text_to_real(x text) RETURNS float8 LANGUAGE sql IMMUTABLE STRICT AS $$
  SELECT CASE WHEN x ~ '^[+-]?[0-9]+(\.[0-9]+)?([eE][+-]?[0-9]+)?$' AND pg_input_is_valid(x, 'float8') THEN x::float8 END
$$;

-- CAST(x AS INTEGER) for REAL x: truncated toward zero when that fits, or NULL.
CREATE FUNCTION nql_real_to_integer(x float8) RETURNS int8 LANGUAGE sql IMMUTABLE STRICT AS $$
  SELECT CASE WHEN x >= -9223372036854775808::float8 AND x < 9223372036854775808::float8 THEN trunc(x)::int8 END
$$;

-- substr(x, start, len): PostgreSQL's own window semantics, but NULL for a negative len, and 64-bit arguments.
CREATE FUNCTION nql_substr(x text, s int8, n int8) RETURNS text LANGUAGE sql IMMUTABLE STRICT AS $$
  SELECT CASE WHEN n >= 0 THEN substr(x, greatest(least(s, 2147483647), -2147483647)::int4, least(n, 2147483647)::int4) END
$$;
CREATE FUNCTION nql_substr(x text, s int8) RETURNS text LANGUAGE sql IMMUTABLE STRICT AS $$
  SELECT substr(x, greatest(least(s, 2147483647), -2147483647)::int4)
$$;
