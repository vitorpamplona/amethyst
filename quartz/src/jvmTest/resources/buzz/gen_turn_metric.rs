// Generates a golden Agent Turn Metric (kind:44200) vector from Buzz's own code,
// with deterministic keys (owner sk=1, agent sk=2), for cross-implementation tests.
use buzz_core::agent_turn_metric::{
    encrypt_agent_turn_metric, AgentTurnMetricPayload, StopReason, TokenCounts,
};
use nostr::{EventBuilder, JsonUtil, Keys, Kind, SecretKey, Tag};

fn keys_from_byte(last: u8) -> Keys {
    let mut sk = [0u8; 32];
    sk[31] = last;
    Keys::new(SecretKey::from_slice(&sk).unwrap())
}

fn main() {
    let owner = keys_from_byte(1);
    let agent = keys_from_byte(2);

    let payload = AgentTurnMetricPayload {
        harness: "goose".to_string(),
        model: Some("claude-sonnet-4-5".to_string()),
        channel_id: Some("12345678-1234-1234-1234-123456789abc".to_string()),
        session_id: Some("sess-abc".to_string()),
        turn_id: Some("turn-1".to_string()),
        turn_seq: Some(1),
        timestamp: "2026-07-01T20:11:03.213Z".to_string(),
        turn: Some(TokenCounts {
            input_tokens: Some(1234),
            output_tokens: Some(567),
            total_tokens: Some(1801),
            cost_usd: Some(0.0123),
            cache_read_tokens: None,
            cache_write_tokens: None,
        }),
        cumulative: Some(TokenCounts {
            input_tokens: Some(45210),
            output_tokens: Some(9876),
            total_tokens: Some(55086),
            cost_usd: Some(0.41),
            cache_read_tokens: None,
            cache_write_tokens: None,
        }),
        delta_reliable: true,
        stop_reason: Some(StopReason::EndTurn),
    };

    let ciphertext = encrypt_agent_turn_metric(&agent, &owner.public_key(), &payload).unwrap();

    let event = EventBuilder::new(Kind::Custom(44200), ciphertext)
        .tags([
            Tag::parse(["p", &owner.public_key().to_hex()]).unwrap(),
            Tag::parse(["agent", &agent.public_key().to_hex()]).unwrap(),
        ])
        .sign_with_keys(&agent)
        .unwrap();

    let out = serde_json::json!({
        "owner_sk": "0000000000000000000000000000000000000000000000000000000000000001",
        "agent_sk": "0000000000000000000000000000000000000000000000000000000000000002",
        "owner_pub": owner.public_key().to_hex(),
        "agent_pub": agent.public_key().to_hex(),
        "payload": serde_json::to_value(&payload).unwrap(),
        "event": serde_json::from_str::<serde_json::Value>(&event.as_json()).unwrap(),
    });
    println!("{}", serde_json::to_string_pretty(&out).unwrap());
}
