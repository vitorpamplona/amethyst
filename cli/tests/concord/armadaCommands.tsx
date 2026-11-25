/**
 * The command table the Armada driver (armada-driver.test.tsx) serves.
 *
 * One composite hook (`useArmada`) mounts the Armada hooks a member's screens
 * hold, for the community/channel the harness points it at (`focus`), and the
 * commands call the mutations those hooks return — the same calls Armada's
 * buttons make. Reads come from the same hook state Armada's screens render.
 * No protocol logic lives here; where a screen composes two hook calls (the
 * disappearing-timer setting), the command composes the same two.
 */
import { NostrEvent, NPool } from "@nostrify/nostrify";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import { finalizeEvent } from "nostr-tools/pure";
import { Component, createElement, useMemo, type ReactNode } from "react";

import { useChannelTimeline, useMessageActions, useSendMessage } from "@/concord/hooks/useChannel";
import { useCommunityActions, useCommunityManagement } from "@/concord/hooks/useCommunityActions";
import { useCommunity, useCommunityList } from "@/concord/hooks/useCommunityList";
import { useChannels, useControlFold, useDissolved } from "@/concord/hooks/useControlPlane";
import { useAcceptDirectInvite, useDirectInvites } from "@/concord/hooks/useDirectInvites";
import { useMembers } from "@/concord/hooks/useGuestbook";
import { useInviteActions } from "@/concord/hooks/useInvites";
import { useModeration } from "@/concord/hooks/useModeration";
import { usePins } from "@/concord/hooks/usePins";
import { useRankedRoles } from "@/concord/hooks/useRankedRoles";
import { useChannelRekeyWatch, useRekeyWatch } from "@/concord/hooks/useRekey";
import { useMetadataActions, useRoles, useStaffKeyWatch } from "@/concord/hooks/useRoles";
import { publishTimerNotices, messageExpirationOf } from "@/concord/lib/disappearing";
import { parseInviteLink } from "@/concord/lib/invite";
import { controlGroups, openControlWraps } from "@/concord/lib/control";
import { KIND_EDIT, KIND_REACTION } from "@/concord/lib/kinds";
import { effectivePermissions, highestPosition, isAuthorized, isStaff, Permissions } from "@/concord/lib/roles";
import { EventStoreContext } from "@/contexts/EventStoreContext";
import { appEventStore } from "@/lib/db/mainEventStore";
import { markBootPainted } from "@/lib/bootGate";
import { ControlPlaneSync } from "@/components/ControlPlaneSync";
import { CommunityDuties } from "@/concord/components/CommunityDuties";
import { useChannelHistoryWalk } from "@/concord/hooks/useChannelHistoryWalk";
import { useResumePendingJoins } from "@/concord/hooks/useCommunityActions";
import { useResumeOutgoing } from "@/concord/hooks/useResumeOutgoing";
import { useResumeGuestbookJoins } from "@/concord/hooks/usePendingGuestbookJoin";
import { WireSync } from "@/wire/WireSync";
import { ReadStateProvider } from "@/components/ReadStateProvider";
import { MutedPubkeysProvider } from "@/components/MutedPubkeysProvider";

type Props = { cid?: string; chid?: string };

/**
 * The account-wide headless services Armada's App mounts for a signed-in user
 * (SignedInServices / SignedInPushServices / SignedInRouterServices), the
 * Concord ones: the live wire, Control sync, the background community duties,
 * history walks and the resume-after-restart hooks. Each sits in its own error
 * boundary so one that needs a context we don't provide is logged, not fatal.
 */
function Resume() {
  useChannelHistoryWalk();
  useResumePendingJoins();
  useResumeOutgoing();
  useResumeGuestbookJoins();
  return null;
}
let serviceLog: (m: string) => void = () => undefined;
class Boundary extends Component<{ name: string; children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  componentDidCatch(e: unknown) {
    serviceLog(`service ${this.props.name} crashed: ${e instanceof Error ? e.message : String(e)}`);
  }
  render() {
    return this.state.failed ? null : this.props.children;
  }
}
const SERVICES: Array<[string, () => ReactNode]> = [
  ["WireSync", () => createElement(WireSync)],
  ["ControlPlaneSync", () => createElement(ControlPlaneSync)],
  ["CommunityDuties", () => createElement(CommunityDuties)],
  ["Resume", () => createElement(Resume)],
];
function Services() {
  return createElement(
    "div",
    null,
    SERVICES.map(([name, el]) => createElement(Boundary, { key: name, name }, el())),
  );
}
const NO_OBSERVED = new Map<string, number>();

function useArmada({ cid, chid }: Props) {
  const list = useCommunityList();
  const actions = useCommunityActions();
  const inbox = useDirectInvites();
  const accept = useAcceptDirectInvite();
  const community = useCommunity(cid);
  const fold = useControlFold(community);
  const channels = useChannels(community);
  const channel = useMemo(() => channels.find((c) => c.idHex === chid), [channels, chid]);
  const invites = useInviteActions(community);
  const roles = useRoles(community);
  const ranked = useRankedRoles(community);
  const meta = useMetadataActions(community);
  const { members } = useMembers(community, NO_OBSERVED);
  const memberList = useMemo(() => [...members], [members]);
  const moderation = useModeration(community, memberList);
  // The background watchers a member's community page keeps running: follow a
  // Refounding we were re-keyed for, and a private channel's rotations.
  const rekey = useRekeyWatch(community);
  useChannelRekeyWatch(community);
  // Adopts the staff write key a Grant's control_wrap carries (CORD-04 §3). ConcordPage mounts it
  // (ConcordPage.tsx useStaffKeyWatch(baseCommunity)); without it a member made staff never writes.
  useStaffKeyWatch(community);
  const timeline = useChannelTimeline(community, channel, chid ?? null);
  const send = useSendMessage(community, channel);
  // The message menu's Delete: a self-delete, or a moderator's hide citing their Grant (CORD-03 §3).
  const messageActions = useMessageActions(community, channel);
  const openedById = useMemo(() => new Map(timeline.raw.map((r) => [r.rumorId, r])), [timeline.raw]);
  const pins = usePins(community, channel, openedById);
  const manage = useCommunityManagement(community);
  const dissolved = useDissolved(community);
  return { manage, dissolved, list, actions, inbox, accept, community, fold, channels, channel, invites, roles, ranked, meta, members, moderation, rekey, timeline, send, messageActions, pins, openedById };
}

export interface Ctx {
  pool: NPool;
  sk: Uint8Array;
  pubkey: string;
  relay: string;
  log: (m: string) => void;
  env: { toasts: unknown[]; user: { signer: Parameters<typeof publishTimerNotices>[3] } };
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const PERM_NAMES = Object.keys(Permissions) as Array<keyof typeof Permissions>;
const permsOf = (names: string[]): bigint => names.reduce((acc, n) => {
  const bit = Permissions[n as keyof typeof Permissions];
  if (bit === undefined) throw new Error(`unknown permission ${n}`);
  return acc | bit;
}, 0n);
const namesOf = (bits: bigint): string[] => PERM_NAMES.filter((n) => (bits & Permissions[n]) !== 0n);

export async function makeCommands(ctx: Ctx) {
  serviceLog = ctx.log;
  // The app opens this on its first paint; there is no paint here.
  markBootPainted();
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const store = appEventStore();
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(
      QueryClientProvider,
      { client: queryClient },
      createElement(
        EventStoreContext.Provider,
        { value: store },
        createElement(ReadStateProvider, null, createElement(MutedPubkeysProvider, null, createElement(Services), children)),
      ),
    );
  let props: Props = {};
  const view = renderHook((p: Props) => useArmada(p), { wrapper, initialProps: props });
  const H = () => view.result.current;
  const focus = (p: Props) => {
    if (p.cid !== props.cid || p.chid !== props.chid) {
      props = { ...props, ...p };
      view.rerender(props);
    }
  };
  /** The screens re-read on focus/poll; a harness step re-reads by invalidating, as a reopen does. */
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["concord"] });

  /** Poll the hook state until `pred` returns a truthy value, re-reading every few seconds. */
  async function until<T>(what: string, pred: () => T | undefined | false | null, ms = 45_000): Promise<T> {
    const end = Date.now() + ms;
    let lastRefresh = Date.now();
    for (;;) {
      let v: T | undefined | false | null;
      try {
        v = pred();
      } catch {
        v = undefined;
      }
      if (v) return v;
      if (Date.now() > end) throw new Error(`timed out waiting for ${what}`);
      if (Date.now() - lastRefresh > 4000) {
        lastRefresh = Date.now();
        await refresh();
      }
      await sleep(250);
    }
  }

  async function open(cid: string, chName?: string) {
    focus({ cid, chid: props.cid === cid ? props.chid : undefined });
    await until(`community ${cid.slice(0, 8)}`, () => H().community);
    await until("control fold", () => H().fold.data);
    if (chName) {
      const ch = await until(`channel ${chName}`, () => H().channels.find((c) => c.name === chName || c.idHex === chName));
      focus({ cid, chid: ch.idHex });
      await until("channel mounted", () => H().channel?.idHex === ch.idHex);
      return ch;
    }
    return undefined;
  }

  const publish = async (e: NostrEvent) => ctx.pool.event(e, { signal: AbortSignal.timeout(8000) });

  const timelineRows = () => H().timeline.folded.messages.map((m) => {
    const reactions = H().timeline.folded.reactions.get(m.rumorId);
    return {
      id: m.rumorId,
      author: m.author,
      kind: m.kind,
      content: m.content,
      edited: m.editedMs !== undefined,
      reactions: reactions ? Object.fromEntries([...reactions.entries()].map(([emoji, e]) => [emoji, (e as { count?: number; pubkeys?: string[] }).count ?? (e as { pubkeys?: string[] }).pubkeys?.length ?? 1])) : {},
    };
  });

  const rosterView = () => {
    const f = H().fold.data!;
    const r = f.roster;
    const holders = new Set(r.grants.map((g) => g.member));
    return {
      ranked: f.rankedRoster ?? false,
      owner: f.ownerHex,
      everyone: r.everyone !== undefined ? namesOf(r.everyone) : undefined,
      roles: r.roles.map((role) => ({ id: role.roleId, name: role.name, position: role.position, rank: role.rank, permissions: namesOf(role.permissions), bits: role.permissions.toString() })),
      grants: [...holders].sort().map((member) => ({
        member,
        roles: r.grants.find((g) => g.member === member)!.roleIds.map((id) => r.roles.find((x) => x.roleId === id)?.name).filter(Boolean),
        position: highestPosition(r, member),
        permissions: namesOf(effectivePermissions(r, member)),
        staff: isStaff(r, member, f.ownerHex),
      })),
      banned: [...f.banned].sort(),
    };
  };

  return {
    whoami: async () => ({ pubkey: ctx.pubkey }),

    /** Account setup, not Concord: a kind 10002 + 10050 naming only $RELAY, so Direct Invites find us. */
    "publish-relay-lists": async () => {
      const now = Math.floor(Date.now() / 1000);
      await publish(finalizeEvent({ kind: 10002, content: "", tags: [["r", ctx.relay]], created_at: now }, ctx.sk));
      await publish(finalizeEvent({ kind: 10050, content: "", tags: [["relay", ctx.relay]], created_at: now }, ctx.sk));
      return {};
    },

    create: async ({ name }: { name: string }) => H().actions.create({ name, relays: [ctx.relay], messageExpirationSecs: 0 }),

    list: async () => {
      await refresh();
      const l = H().list;
      return { entries: (l.data?.list.entries ?? []).map((e) => ({ community_id: e.community_id, name: e.current.name, epoch: e.current.root_epoch, channels: (e.current.channels ?? []).map((c) => c.name) })) };
    },

    channels: async ({ cid, wait, gone }: { cid: string; wait?: string[]; gone?: string[] }) => {
      await open(cid);
      if (wait) await until(`channels ${wait}`, () => wait.every((n) => H().channels.some((c) => c.name === n)));
      if (gone) await until(`channels gone ${gone}`, () => gone.every((n) => !H().channels.some((c) => c.name === n)));
      const f = H().fold.data!;
      return {
        epoch: H().community!.rootEpoch,
        metadata: { name: f.metadata?.name, description: f.metadata?.description ?? null, relays: f.metadata?.relays, message_expiration: messageExpirationOf(f.metadata) },
        ranked: f.rankedRoster ?? false,
        channels: H().channels.map((c) => ({ id: c.idHex, name: c.name, private: c.isPrivate, epoch: c.current.epoch })),
      };
    },

    /** Mint a shareable invite link (CORD-05 §2) — the Invite dialog's "Create link". */
    link: async ({ cid }: { cid: string }) => {
      await open(cid);
      const url = await H().invites.createLink({});
      return { url };
    },

    /** Direct Invite (CORD-05 §6) — the Invite dialog's "Invite by npub". */
    "dm-invite": async ({ cid, to }: { cid: string; to: string }) => {
      await open(cid);
      await H().invites.sendDirectInvite({ recipientPubkey: to });
      return {};
    },

    invites: async ({ wait }: { wait?: string }) => {
      const rows = () => (H().inbox.data ?? []).map((i) => ({ wrap_id: i.wrapId, community_id: i.communityId, name: i.name, sender: i.sender, catch_up: Boolean(i.catchUp), channels: (i.bundle.channels ?? []).map((c) => c.name) }));
      if (wait) await until(`invite for ${wait}`, () => rows().some((r) => r.community_id === wait));
      return { invites: rows() };
    },

    accept: async ({ cid }: { cid: string }) => {
      const parked = await until(`invite ${cid.slice(0, 8)}`, () => (H().inbox.data ?? []).find((i) => i.communityId === cid));
      const out = await H().accept.mutateAsync({ invite: parked });
      return out;
    },

    /** Follow an invite link (any base; CORD-05 §2) — the join page's "Join" button. */
    join: async ({ url }: { url: string }) => {
      const invite = parseInviteLink(url);
      if (!invite) throw new Error(`Armada does not parse this invite link: ${url}`);
      const preview = await H().actions.preview({ invite });
      const out = await H().actions.join({ invite });
      return { ...out, preview_name: preview.name };
    },

    send: async ({ cid, ch, text }: { cid: string; ch: string; text: string }) => {
      await open(cid, ch);
      const rumor = await H().send.mutateAsync({ content: text });
      return { id: (rumor as { id?: string } | undefined)?.id };
    },

    react: async ({ cid, ch, target, emoji }: { cid: string; ch: string; target: string; emoji: string }) => {
      await open(cid, ch);
      const t = await until(`message ${target.slice(0, 8)}`, () => H().openedById.get(target));
      await H().send.mutateAsync({ content: emoji, kind: KIND_REACTION, target, targetKind: t.kind, targetPubkey: t.author });
      return {};
    },

    edit: async ({ cid, ch, target, text }: { cid: string; ch: string; target: string; text: string }) => {
      await open(cid, ch);
      const t = await until(`message ${target.slice(0, 8)}`, () => H().openedById.get(target));
      await H().send.mutateAsync({ content: text, kind: KIND_EDIT, target, targetKind: t.kind, expiration: null });
      return {};
    },

    /** Deletes a message as its menu does: our own, or another member's as a moderator's hide. */
    delete: async ({ cid, ch, target }: { cid: string; ch: string; target: string }) => {
      await open(cid, ch);
      await until(`message ${target.slice(0, 8)}`, () => H().openedById.get(target));
      H().messageActions.deleteMessage(target);
      // deleteMessage publishes in the background; give it the time a click would.
      await sleep(3000);
      return {};
    },

    /** The channel as its screen renders it. `wait`: contents that must appear; `absent`: must not. */
    read: async ({ cid, ch, wait, absent, settleMs }: { cid: string; ch: string; wait?: string[]; absent?: string[]; settleMs?: number }) => {
      await open(cid, ch);
      if (wait?.length) await until(`messages ${JSON.stringify(wait)}`, () => wait.every((w) => timelineRows().some((m) => m.content.includes(w))));
      else {
        // Nothing to wait for: give the sync rounds the same time a reader would.
        const end = Date.now() + (settleMs ?? 8000);
        while (Date.now() < end) {
          await refresh();
          await sleep(1000);
        }
      }
      const rows = timelineRows();
      const leaked = (absent ?? []).filter((a) => rows.some((m) => m.content.includes(a)));
      return { messages: rows, leaked, epoch: H().channel?.current.epoch, streams: H().channel?.streams.map((s) => s.epoch) };
    },

    roles: async ({ cid, waitRole, waitGrant, waitBanned, waitUnbanned }: { cid: string; waitRole?: string; waitGrant?: string; waitBanned?: string; waitUnbanned?: string }) => {
      await open(cid);
      if (waitRole) await until(`role ${waitRole}`, () => H().fold.data!.roster.roles.some((r) => r.name === waitRole));
      if (waitGrant) await until(`grant to ${waitGrant.slice(0, 8)}`, () => H().fold.data!.roster.grants.some((g) => g.member === waitGrant && g.roleIds.length > 0));
      if (waitBanned) await until(`ban of ${waitBanned.slice(0, 8)}`, () => H().fold.data!.banned.has(waitBanned));
      if (waitUnbanned) await until(`unban of ${waitUnbanned.slice(0, 8)}`, () => !H().fold.data!.banned.has(waitUnbanned));
      return rosterView();
    },

    /** Can `who` act with `perm`, as Armada judges it (CORD-04 §3, §5)? */
    authorized: async ({ cid, who, perm }: { cid: string; who: string; perm: string }) => {
      await open(cid);
      const f = H().fold.data!;
      return { authorized: isAuthorized(f.roster, who, f.ownerHex, permsOf([perm])) };
    },

    /** Roles v2 "Create role" (vsk 13), then optionally the member's Grant. */
    "create-role": async ({ cid, name, perms, grant }: { cid: string; name: string; perms: string[]; grant?: string }) => {
      await open(cid);
      let roleId: string;
      if (H().fold.data!.rankedRoster) {
        roleId = await H().ranked.createRole({ name, color: 0, permissions: permsOf(perms), display: false, mentionable: false });
      } else {
        roleId = H().roles.newRoleId();
        const position = Math.max(0, ...H().fold.data!.roster.roles.map((r) => r.position)) + 1;
        await H().roles.saveRole({ role: { roleId, name, position, permissions: permsOf(perms), scope: { kind: "server" }, color: 0 } });
      }
      if (grant) {
        await until(`role ${name} in fold`, () => H().fold.data!.roster.roles.some((r) => r.roleId === roleId));
        await H().roles.setMemberRoles({ member: grant, roleIds: [roleId] });
      }
      return { role_id: roleId };
    },

    grant: async ({ cid, member, roleIds }: { cid: string; member: string; roleIds: string[] }) => {
      await open(cid);
      await H().roles.setMemberRoles({ member, roleIds });
      return {};
    },

    members: async ({ cid, wait, gone }: { cid: string; wait?: string; gone?: string }) => {
      await open(cid);
      if (wait) await until(`member ${wait.slice(0, 8)}`, () => H().members.has(wait));
      if (gone) await until(`member ${gone.slice(0, 8)} gone`, () => !H().members.has(gone));
      return { members: [...H().members].sort() };
    },

    /** The member panel's "Ban": Banlist + strip + Refounding (CORD-04 §6). */
    ban: async ({ cid, target }: { cid: string; target: string }) => {
      await open(cid);
      const phases: string[] = [];
      await H().moderation.ban({ target, onPhase: (p) => phases.push(String(p)) });
      return { phases, epoch: H().community?.rootEpoch };
    },

    kick: async ({ cid, target }: { cid: string; target: string }) => {
      await open(cid);
      await H().moderation.kick({ target });
      return {};
    },

    unban: async ({ cid, target }: { cid: string; target: string }) => {
      await open(cid);
      await (H().moderation.unban as (a: unknown) => Promise<unknown>)({ target });
      return {};
    },

    /** Wait until this member follows the community to at least `epoch` (useRekeyWatch). */
    epoch: async ({ cid, atLeast, ms }: { cid: string; atLeast?: number; ms?: number }) => {
      await open(cid);
      if (atLeast !== undefined) await until(`epoch >= ${atLeast}`, () => H().community!.rootEpoch >= BigInt(atLeast), ms ?? 60_000);
      return { epoch: H().community!.rootEpoch, stranded: H().rekey.stranded };
    },

    pins: async ({ cid, ch, wait }: { cid: string; ch: string; wait?: string }) => {
      await open(cid, ch);
      if (wait) await until(`pin ${wait.slice(0, 8)}`, () => H().pins.pins.some((p) => p.rumorId === wait));
      return { pins: H().pins.pins.map((p) => ({ id: p.rumorId, author: p.author, content: p.content })), dark: H().pins.dark };
    },

    pin: async ({ cid, ch, target }: { cid: string; ch: string; target: string }) => {
      await open(cid, ch);
      const opened = await until(`message ${target.slice(0, 8)}`, () => H().openedById.get(target));
      await H().pins.pin({ opened });
      return {};
    },

    /** Settings › Disappearing messages: the metadata edition, then the channel notices. */
    timer: async ({ cid, seconds }: { cid: string; seconds: number }) => {
      await open(cid);
      await H().meta.updateMetadata({ message_expiration: seconds });
      // Best-effort, as the settings screen does (CORD-08 §4): the metadata fold is the authority.
      await publishTimerNotices(ctx.pool, H().community!, H().channels, ctx.env.user.signer, ctx.pubkey, seconds).catch(() => undefined);
      return {};
    },

    /** "New channel" (CORD-03 §2); private on the vsk-13 Roster opens to the owner alone. */
    "create-channel": async ({ cid, name, private: isPrivate }: { cid: string; name: string; private?: boolean }) => {
      await open(cid);
      const out = await H().manage.createChannel({ name, isPrivate: Boolean(isPrivate) });
      return { channel_id: out.channelIdHex };
    },

    /** A channel's permission overwrite (CORD-04 §8), e.g. a Role allowed VIEW_CHANNEL. */
    overwrite: async ({ cid, ch, role, allow, deny }: { cid: string; ch: string; role: string; allow: string[]; deny?: string[] }) => {
      await open(cid);
      const chan = await until(`channel ${ch}`, () => H().channels.find((c) => c.name === ch || c.idHex === ch));
      await H().ranked.setOverwrite({ scope: chan.idHex, target: { kind: "role", id: role }, allow: permsOf(allow), deny: permsOf(deny ?? []) });
      return {};
    },

    dissolve: async ({ cid }: { cid: string }) => {
      await open(cid);
      await H().manage.dissolve();
      return {};
    },

    dissolved: async ({ cid, wait }: { cid: string; wait?: boolean }) => {
      await open(cid);
      if (wait) await until("dissolution", () => H().dissolved.data !== undefined && H().dissolved.data !== null);
      return { dissolved_at: H().dissolved.data ?? null };
    },

    /** Evidence: every Control edition this member can open, as Armada parses it. */
    editions: async ({ cid, vsk }: { cid: string; vsk?: string }) => {
      await open(cid);
      const groups = controlGroups(H().community!);
      const wraps = await ctx.pool.query([{ kinds: [1059], authors: groups.map((g) => g.pk) }], { signal: AbortSignal.timeout(8000) });
      const eds = openControlWraps(wraps, groups) as unknown as Array<{ vsk: string; version: bigint; author: string; content: string; binding?: unknown }>;
      return { editions: eds.filter((e) => !vsk || e.vsk === vsk).map((e) => ({ vsk: e.vsk, version: e.version, author: e.author, marked: Boolean(e.binding), content: e.content })) };
    },

    toasts: async () => ({ toasts: ctx.env.toasts.map((t) => JSON.stringify(t)) }),
  } as Record<string, (a: never) => Promise<unknown>>;
}
