//! udp-loss-shim — UDP loopback that drops a configurable fraction of
//! datagrams, used by the I9 packet-loss scenario. **Phase 1 stub.**

use anyhow::Result;
use clap::Parser;

#[derive(Parser, Debug)]
#[command(name = "udp-loss-shim", about = "UDP packet-loss shim (Phase 1 stub)")]
struct Args {
    #[arg(long)]
    listen: String,
    #[arg(long)]
    upstream: String,
    #[arg(long, default_value_t = 0.0)]
    loss_rate: f32,
}

#[tokio::main]
async fn main() -> Result<()> {
    let args = Args::parse();
    eprintln!(
        "udp-loss-shim Phase-1 stub — listen={} upstream={} loss_rate={}",
        args.listen, args.upstream, args.loss_rate
    );
    eprintln!("Phase 3 will implement actual UDP forwarding. Exiting cleanly.");
    Ok(())
}
