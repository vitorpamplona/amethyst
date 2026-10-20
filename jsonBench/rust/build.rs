//! Embeds the toolchain description and, from `cargo metadata`, the resolved version and
//! license of every crate the harness (and its `sjd` glue crate) depends on directly.

use std::collections::BTreeMap;
use std::env;
use std::fs;
use std::path::Path;
use std::process::Command;

fn main() {
    let manifest_dir = env::var("CARGO_MANIFEST_DIR").unwrap();
    println!("cargo:rerun-if-changed=Cargo.lock");
    println!("cargo:rerun-if-changed=Cargo.toml");
    println!("cargo:rerun-if-changed=sjd/Cargo.toml");
    println!("cargo:rerun-if-env-changed=RUSTFLAGS");

    let rustc = env::var("RUSTC").unwrap_or_else(|_| "rustc".into());
    let rustc_version = Command::new(&rustc)
        .arg("--version")
        .output()
        .ok()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_else(|| "rustc (unknown)".into());
    let flags = env::var("CARGO_ENCODED_RUSTFLAGS")
        .unwrap_or_default()
        .split('\u{1f}')
        .filter(|s| !s.is_empty())
        .collect::<Vec<_>>()
        .join(" ");
    let profile = env::var("PROFILE").unwrap_or_default();
    println!("cargo:rustc-env=JSONBENCH_RUSTC={rustc_version}");
    println!("cargo:rustc-env=JSONBENCH_RUSTFLAGS={flags}");
    println!("cargo:rustc-env=JSONBENCH_PROFILE={profile}");

    let cargo = env::var("CARGO").unwrap_or_else(|_| "cargo".into());
    let manifest = Path::new(&manifest_dir).join("Cargo.toml");
    // --filter-platform keeps --offline from needing other platforms' crates downloaded.
    let target = env::var("TARGET").unwrap_or_default();
    let meta = Command::new(cargo)
        .args([
            "metadata",
            "--format-version",
            "1",
            "--offline",
            "--locked",
            "--filter-platform",
            &target,
        ])
        .arg("--manifest-path")
        .arg(&manifest)
        .output();
    // name -> (version, license); direct deps of the root crate win over sjd's.
    let mut crates: BTreeMap<String, (String, String)> = BTreeMap::new();
    match meta {
        Ok(o) if o.status.success() => {
            let v: serde_json::Value = serde_json::from_slice(&o.stdout).expect("cargo metadata json");
            let packages = v["packages"].as_array().unwrap();
            let by_id: BTreeMap<&str, &serde_json::Value> =
                packages.iter().map(|p| (p["id"].as_str().unwrap(), p)).collect();
            let nodes = v["resolve"]["nodes"].as_array().unwrap();
            for member in ["sjd", "jsonbench-rust"] {
                let Some(node) = nodes
                    .iter()
                    .find(|n| by_id[n["id"].as_str().unwrap()]["name"] == member)
                else {
                    continue;
                };
                for dep in node["deps"].as_array().unwrap() {
                    let p = by_id[dep["pkg"].as_str().unwrap()];
                    let name = p["name"].as_str().unwrap().to_string();
                    let version = p["version"].as_str().unwrap().to_string();
                    // SPDX: old crates write "MIT/Apache-2.0" for "MIT OR Apache-2.0".
                    let license = p["license"]
                        .as_str()
                        .unwrap_or("(see license-file)")
                        .replace('/', " OR ");
                    crates.insert(name, (version, license));
                }
            }
        }
        Ok(o) => println!(
            "cargo:warning=cargo metadata failed: {}",
            String::from_utf8_lossy(&o.stderr)
        ),
        Err(e) => println!("cargo:warning=cargo metadata failed: {e}"),
    }
    let mut src = String::from("pub const CRATES: &[(&str, &str, &str)] = &[\n");
    for (name, (version, license)) in &crates {
        src.push_str(&format!("    ({name:?}, {version:?}, {license:?}),\n"));
    }
    src.push_str("];\n");
    let out = Path::new(&env::var("OUT_DIR").unwrap()).join("crates.rs");
    fs::write(out, src).unwrap();
}
