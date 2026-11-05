use std::collections::HashMap;

pub fn url_decode(input: &str) -> String {
    let mut out = Vec::new();
    let bytes = input.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let Ok(val) = u8::from_str_radix(std::str::from_utf8(&bytes[i + 1..i + 3]).unwrap_or(""), 16) {
                out.push(val);
                i += 3;
                continue;
            }
        } else if bytes[i] == b'+' {
            out.push(b' ');
            i += 1;
            continue;
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).to_string()
}

pub fn parse_query(query_str: &str) -> HashMap<String, String> {
    let mut params = HashMap::new();
    for part in query_str.split('&') {
        if let Some((k, v)) = part.split_once('=') {
            params.insert(url_decode(k), url_decode(v));
        }
    }
    params
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_url_decode() {
        assert_eq!(url_decode("hello+world"), "hello world");
        assert_eq!(url_decode("a%20b%2Fc"), "a b/c");
    }

    #[test]
    fn test_parse_query() {
        let q = parse_query("foo=bar&path=%2Fhome%2Fuser");
        assert_eq!(q.get("foo").unwrap(), "bar");
        assert_eq!(q.get("path").unwrap(), "/home/user");
    }
}

