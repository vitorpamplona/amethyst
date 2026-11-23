/**
 * Armada Concord driver for cli/tests/concord/armada-interop.sh.
 *
 * Runs ONE Armada identity headlessly: Armada's own React hooks and `src/concord`
 * modules, mounted with @testing-library's renderHook in vitest's jsdom env (the
 * same way Armada's own hook tests mount them), with exactly four seams replaced:
 *
 *   - useNostr        → a real @nostrify NPool whose every socket goes to $RELAY_SOCKET
 *   - useCurrentUser  → an nsec NUser (fresh key per run, from $ARMADA_SK)
 *   - useAppContext   → Armada's defaultConfig with every relay list = [$RELAY]
 *   - useToast        → recorded, not rendered
 *
 * Nothing in Armada is copied or modified: the harness copies THIS file into a
 * scratch checkout ($ARMADA_DIR/src/__interop__/) so `@/…` resolves, and runs it
 * with Armada's vitest config. Armada is AGPL-3.0 and stays an external
 * counterparty; this file only calls it.
 *
 * Control: a tiny JSON-over-HTTP server on $DRIVER_HOST:$DRIVER_PORT.
 *   POST /<command>  {args}  →  200 {ok:true, ...} | 500 {ok:false, error}
 * Commands are listed in COMMANDS below. POST /quit ends the run.
 */
import { NPool, NRelay1, NSecSigner, type NostrEvent, type NostrFilter } from "@nostrify/nostrify";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { appendFileSync } from "node:fs";
import { hexToBytes } from "@noble/hashes/utils.js";
import { getPublicKey, finalizeEvent } from "nostr-tools/pure";
import { nip19 } from "nostr-tools";
import { createElement, type ReactNode, useState } from "react";
import { expect, it, vi } from "vitest";

const env = vi.hoisted(() => ({
  pool: undefined as unknown,
  user: undefined as unknown,
  logins: [] as unknown[],
  config: undefined as unknown,
  toasts: [] as unknown[],
}));

vi.mock("@nostrify/react", async (orig) => ({
  ...(await orig<typeof import("@nostrify/react")>()),
  useNostr: () => ({ nostr: env.pool }),
}));
vi.mock("@nostrify/react/login", async (orig) => ({
  ...(await orig<typeof import("@nostrify/react/login")>()),
  useNostrLogin: () => ({ logins: env.logins, addLogin: () => undefined, removeLogin: () => undefined, setLogin: () => undefined }),
}));
vi.mock("@/hooks/useCurrentUser", () => ({
  useCurrentUser: () => ({ user: env.user, users: env.user ? [env.user] : [], isLoading: false }),
}));
vi.mock("@/hooks/useAppContext", () => ({
  useAppContext: () => ({ config: env.config, updateConfig: () => undefined, presetRelays: [] }),
}));
vi.mock("@/hooks/useToast", () => ({
  toast: (t: unknown) => {
    env.toasts.push(t);
    return { id: "x", dismiss: () => undefined, update: () => undefined };
  },
  useToast: () => ({ toast: () => undefined, toasts: [], dismiss: () => undefined }),
}));

// RELAY is the relay's URL as every party names it (in relay lists, community metadata);
// RELAY_SOCKET is where it actually listens. The harness maps the name to the address for
// amy's JVM; here every socket is simply opened at RELAY_SOCKET.
const RELAY = process.env.RELAY ?? "ws://127.0.0.2:7781";
const RELAY_SOCKET = process.env.RELAY_SOCKET ?? RELAY;
const LOG = process.env.DRIVER_LOG ?? "/dev/stderr";
const log = (m: string) => {
  try {
    appendFileSync(LOG, `[${new Date().toISOString()}] ${m}\n`);
  } catch {
    /* ignore */
  }
};

it("armada driver", { timeout: 0 }, async () => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = false;
  const sk = hexToBytes(process.env.ARMADA_SK!);
  const pubkey = getPublicKey(sk);

  const { defaultConfig } = await import("@/contexts/AppContext");
  env.config = {
    ...defaultConfig,
    appRelays: [RELAY],
    broadcastRelays: [],
    communityRelays: [RELAY],
    searchRelays: [],
    dmRelays: [RELAY],
    relayMetadata: { relays: [{ url: RELAY, read: true, write: true }], updatedAt: 1, pubkey },
  };

  // Every relay URL Armada names (its stock set, a bundle's list) lands on $RELAY.
  const sockets = new Map<string, NRelay1>();
  const pool = new NPool({
    open(url: string) {
      if (url !== RELAY) log(`redirect ${url} -> ${RELAY_SOCKET}`);
      let r = sockets.get(url);
      if (!r) {
        r = new NRelay1(RELAY_SOCKET);
        sockets.set(url, r);
      }
      return r;
    },
    reqRouter: (filters: NostrFilter[]) => new Map([[RELAY, filters]]),
    eventRouter: (_e: NostrEvent) => [RELAY],
    eoseTimeout: 500,
  });
  env.pool = pool;

  const { NUser } = await import("@nostrify/react/login");
  env.user = new NUser("nsec", pubkey, new NSecSigner(sk));
  env.logins = [{ id: "harness", type: "nsec", pubkey, createdAt: new Date().toISOString(), data: { nsec: nip19.nsecEncode(sk) } }];

  const mod = await import("./armadaCommands");
  const COMMANDS = await mod.makeCommands({ pool, sk, pubkey, relay: RELAY, log, env });

  // ── control server ────────────────────────────────────────────────────────
  const host = process.env.DRIVER_HOST ?? "127.0.0.2";
  const port = Number(process.env.DRIVER_PORT ?? 7790);
  let quit!: () => void;
  const done = new Promise<void>((r) => (quit = r));
  const server = createServer(async (req: IncomingMessage, res: ServerResponse) => {
    const name = (req.url ?? "/").replace(/^\//, "").split("?")[0];
    let body = "";
    for await (const chunk of req) body += chunk;
    const args = body ? JSON.parse(body) : {};
    const reply = (code: number, obj: unknown) => {
      res.writeHead(code, { "content-type": "application/json" });
      res.end(JSON.stringify(obj, (_k, v) => (typeof v === "bigint" ? v.toString() : v instanceof Uint8Array ? Buffer.from(v).toString("hex") : v)));
    };
    if (name === "quit") {
      reply(200, { ok: true });
      quit();
      return;
    }
    const fn = COMMANDS[name];
    if (!fn) return reply(404, { ok: false, error: `unknown command ${name}` });
    const t0 = Date.now();
    log(`> ${name} ${body}`);
    try {
      const out = await fn(args);
      log(`< ${name} ok ${Date.now() - t0}ms`);
      reply(200, { ok: true, ...(out as object) });
    } catch (e) {
      const msg = e instanceof Error ? `${e.message}\n${e.stack}` : String(e);
      log(`< ${name} FAILED ${msg}`);
      reply(500, { ok: false, error: e instanceof Error ? e.message : String(e) });
    }
  });
  await new Promise<void>((r) => server.listen(port, host, () => r()));
  log(`driver ${pubkey} listening on ${host}:${port}, relay ${RELAY}`);
  appendFileSync(process.env.DRIVER_READY ?? "/dev/null", `${pubkey}\n`);
  await done;
  server.close();
  expect(true).toBe(true);
});

// Re-exported so armadaCommands can mount hooks under the same QueryClient.
export { QueryClient, QueryClientProvider, renderHook, createElement, useState, finalizeEvent };
export type { ReactNode };
