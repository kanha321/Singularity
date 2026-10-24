fn main() {
    println!("cargo:rustc-cdylib-link-arg=-Wl,-z,nodelete");
}
