//! hang-publish — reference moq-lite / hang audio publisher for the
//! cross-stack interop harness. **Phase 1 stub.**

use anyhow::Result;
use clap::Parser;

#[derive(Parser, Debug)]
#[command(
    name = "hang-publish",
    about = "Reference moq-lite / hang audio publisher (Phase 1 stub)"
)]
struct Args {
    #[arg(long)]
    relay_url: String,
    #[arg(long)]
    jwt: Option<String>,
    #[arg(long)]
    broadcast: String,
    #[arg(long, default_value_t = 440)]
    freq_hz: u32,
    #[arg(long, default_value_t = 5)]
    duration: u64,
    #[arg(long, default_value_t = 1)]
    channels: u32,
}

#[tokio::main]
async fn main() -> Result<()> {
    let args = Args::parse();
    eprintln!(
        "hang-publish Phase-1 stub — relay_url={} broadcast={} freq_hz={} duration={}s channels={}",
        args.relay_url, args.broadcast, args.freq_hz, args.duration, args.channels
    );
    eprintln!("Phase 2 will implement the actual publish loop. Exiting cleanly.");
    Ok(())
}
