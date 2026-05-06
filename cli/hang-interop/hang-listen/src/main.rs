//! hang-listen — reference moq-lite / hang audio listener for the
//! cross-stack interop harness. **Phase 1 stub** — Phase 2 fills in
//! the real subscribe loop. See
//! `nestsClient/plans/2026-05-06-cross-stack-interop-test.md`.

use anyhow::Result;
use clap::Parser;

#[derive(Parser, Debug)]
#[command(
    name = "hang-listen",
    about = "Reference moq-lite / hang audio listener (Phase 1 stub)"
)]
struct Args {
    #[arg(long)]
    relay_url: String,
    #[arg(long)]
    jwt: Option<String>,
    #[arg(long)]
    broadcast: String,
    #[arg(long, default_value_t = 5)]
    duration: u64,
    #[arg(long)]
    output_pcm: Option<String>,
}

#[tokio::main]
async fn main() -> Result<()> {
    let args = Args::parse();
    eprintln!(
        "hang-listen Phase-1 stub — relay_url={} broadcast={} duration={}s output_pcm={:?}",
        args.relay_url, args.broadcast, args.duration, args.output_pcm
    );
    eprintln!("Phase 2 will implement the actual subscribe loop. Exiting cleanly.");
    Ok(())
}
