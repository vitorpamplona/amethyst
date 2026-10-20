//! `Dom` access for every library's generic tree.

use crate::model::Dom;
use std::borrow::Cow;

impl Dom for serde_json::Value {
    #[inline]
    fn field(&self, key: &str) -> Option<&Self> {
        self.get(key)
    }
    #[inline]
    fn string(&self) -> Option<&str> {
        self.as_str()
    }
    #[inline]
    fn uint(&self) -> Option<u64> {
        self.as_u64()
    }
    #[inline]
    fn array_len(&self) -> Option<usize> {
        self.as_array().map(Vec::len)
    }
    fn items(&self) -> Option<Vec<&Self>> {
        self.as_array().map(|a| a.iter().collect())
    }
}

impl Dom for serde_json_borrow::Value<'_> {
    #[inline]
    fn field(&self, key: &str) -> Option<&Self> {
        self.as_object().and_then(|o| o.get(key))
    }
    #[inline]
    fn string(&self) -> Option<&str> {
        self.as_str()
    }
    #[inline]
    fn uint(&self) -> Option<u64> {
        self.as_u64()
    }
    #[inline]
    fn array_len(&self) -> Option<usize> {
        self.as_array().map(|a| a.len())
    }
    fn items(&self) -> Option<Vec<&Self>> {
        self.as_array().map(|a| a.iter().collect())
    }
}

mod simd {
    use super::Dom;
    use simd_json::prelude::*;

    impl Dom for simd_json::BorrowedValue<'_> {
        #[inline]
        fn field(&self, key: &str) -> Option<&Self> {
            ValueObjectAccess::get(self, key)
        }
        #[inline]
        fn string(&self) -> Option<&str> {
            ValueAsScalar::as_str(self)
        }
        #[inline]
        fn uint(&self) -> Option<u64> {
            ValueAsScalar::as_u64(self)
        }
        #[inline]
        fn array_len(&self) -> Option<usize> {
            ValueAsArray::as_array(self).map(|a| a.len())
        }
        fn items(&self) -> Option<Vec<&Self>> {
            ValueAsArray::as_array(self).map(|a| a.iter().collect())
        }
    }

    impl Dom for simd_json::OwnedValue {
        #[inline]
        fn field(&self, key: &str) -> Option<&Self> {
            ValueObjectAccess::get(self, key)
        }
        #[inline]
        fn string(&self) -> Option<&str> {
            ValueAsScalar::as_str(self)
        }
        #[inline]
        fn uint(&self) -> Option<u64> {
            ValueAsScalar::as_u64(self)
        }
        #[inline]
        fn array_len(&self) -> Option<usize> {
            ValueAsArray::as_array(self).map(|a| a.len())
        }
        fn items(&self) -> Option<Vec<&Self>> {
            ValueAsArray::as_array(self).map(|a| a.iter().collect())
        }
    }
}

mod sonic {
    use super::Dom;
    use sonic_rs::{JsonContainerTrait, JsonValueTrait};

    impl Dom for sonic_rs::Value {
        #[inline]
        fn field(&self, key: &str) -> Option<&Self> {
            self.as_object().and_then(|o| o.get(&key))
        }
        #[inline]
        fn string(&self) -> Option<&str> {
            self.as_str()
        }
        #[inline]
        fn uint(&self) -> Option<u64> {
            self.as_u64()
        }
        #[inline]
        fn array_len(&self) -> Option<usize> {
            self.as_array().map(|a| a.len())
        }
        fn items(&self) -> Option<Vec<&Self>> {
            self.as_array().map(|a| a.iter().collect())
        }
    }
}

impl Dom for jiter::JsonValue<'_> {
    #[inline]
    fn field(&self, key: &str) -> Option<&Self> {
        match self {
            jiter::JsonValue::Object(o) => o.iter().find(|(k, _)| k == key).map(|(_, v)| v),
            _ => None,
        }
    }
    #[inline]
    fn string(&self) -> Option<&str> {
        match self {
            jiter::JsonValue::Str(s) => Some(s),
            _ => None,
        }
    }
    #[inline]
    fn uint(&self) -> Option<u64> {
        match self {
            jiter::JsonValue::Int(i) => u64::try_from(*i).ok(),
            _ => None,
        }
    }
    #[inline]
    fn array_len(&self) -> Option<usize> {
        match self {
            jiter::JsonValue::Array(a) => Some(a.len()),
            _ => None,
        }
    }
    fn items(&self) -> Option<Vec<&Self>> {
        match self {
            jiter::JsonValue::Array(a) => Some(a.iter().collect()),
            _ => None,
        }
    }
}

impl Dom for json::JsonValue {
    #[inline]
    fn field(&self, key: &str) -> Option<&Self> {
        match self {
            json::JsonValue::Object(o) => o.get(key),
            _ => None,
        }
    }
    #[inline]
    fn string(&self) -> Option<&str> {
        self.as_str()
    }
    #[inline]
    fn uint(&self) -> Option<u64> {
        self.as_u64()
    }
    #[inline]
    fn array_len(&self) -> Option<usize> {
        match self {
            json::JsonValue::Array(a) => Some(a.len()),
            _ => None,
        }
    }
    fn items(&self) -> Option<Vec<&Self>> {
        match self {
            json::JsonValue::Array(a) => Some(a.iter().collect()),
            _ => None,
        }
    }
}

impl Dom for tinyjson::JsonValue {
    #[inline]
    fn field(&self, key: &str) -> Option<&Self> {
        match self {
            tinyjson::JsonValue::Object(o) => o.get(key),
            _ => None,
        }
    }
    #[inline]
    fn string(&self) -> Option<&str> {
        match self {
            tinyjson::JsonValue::String(s) => Some(s),
            _ => None,
        }
    }
    /// tinyjson keeps every number as `f64`; NIP-01 integers are < 2^53, so this is exact.
    #[inline]
    fn uint(&self) -> Option<u64> {
        match self {
            tinyjson::JsonValue::Number(f) if *f >= 0.0 && f.fract() == 0.0 => Some(*f as u64),
            _ => None,
        }
    }
    #[inline]
    fn array_len(&self) -> Option<usize> {
        match self {
            tinyjson::JsonValue::Array(a) => Some(a.len()),
            _ => None,
        }
    }
    fn items(&self) -> Option<Vec<&Self>> {
        match self {
            tinyjson::JsonValue::Array(a) => Some(a.iter().collect()),
            _ => None,
        }
    }
}

/// hifijson's slice lexer yields numbers as their source text and strings as `Cow`.
pub type HifiValue<'a> = hifijson::value::Value<&'a str, Cow<'a, str>>;

impl Dom for HifiValue<'_> {
    #[inline]
    fn field(&self, key: &str) -> Option<&Self> {
        match self {
            hifijson::value::Value::Object(o) => o.iter().find(|(k, _)| k == key).map(|(_, v)| v),
            _ => None,
        }
    }
    #[inline]
    fn string(&self) -> Option<&str> {
        match self {
            hifijson::value::Value::String(s) => Some(s),
            _ => None,
        }
    }
    #[inline]
    fn uint(&self) -> Option<u64> {
        match self {
            hifijson::value::Value::Number((n, _)) => n.parse().ok(),
            _ => None,
        }
    }
    #[inline]
    fn array_len(&self) -> Option<usize> {
        match self {
            hifijson::value::Value::Array(a) => Some(a.len()),
            _ => None,
        }
    }
    fn items(&self) -> Option<Vec<&Self>> {
        match self {
            hifijson::value::Value::Array(a) => Some(a.iter().collect()),
            _ => None,
        }
    }
}

impl Dom for simdjson_rust::tape::Value<'_> {
    #[inline]
    fn field(&self, key: &str) -> Option<&Self> {
        self.get(key)
    }
    #[inline]
    fn string(&self) -> Option<&str> {
        self.as_str()
    }
    #[inline]
    fn uint(&self) -> Option<u64> {
        self.as_u64()
    }
    #[inline]
    fn array_len(&self) -> Option<usize> {
        self.as_array().map(<[_]>::len)
    }
    fn items(&self) -> Option<Vec<&Self>> {
        self.as_array().map(|a| a.iter().collect())
    }
}
