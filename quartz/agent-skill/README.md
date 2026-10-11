# Quartz agent skill

`quartz/` is an [Agent Skill](https://agentskills.io) for coding agents working in a
project that **depends on** Quartz. It teaches the agent where things are in the library
and which helpers to reach for (the `fetch*` / publish accessories, typed event builders,
`NostrSigner`, NIP-19 / NIP-05 parsing) instead of re-implementing them, and links to
the full guides in this repo.

It is not loaded when you work on Amethyst itself; the repo's own skills live in
`.claude/skills/`.

## Install

Copy the `quartz/` folder into your project's skills directory:

```bash
# Claude Code
mkdir -p .claude/skills && cp -r path/to/amethyst/quartz/agent-skill/quartz .claude/skills/

# Codex
mkdir -p .agents/skills && cp -r path/to/amethyst/quartz/agent-skill/quartz .agents/skills/
```

Or, without cloning the repo:

```bash
mkdir -p .claude/skills/quartz && curl -fsSL -o .claude/skills/quartz/SKILL.md \
  https://raw.githubusercontent.com/vitorpamplona/amethyst/main/quartz/agent-skill/quartz/SKILL.md
```

Use `~/.claude/skills/` or `~/.agents/skills/` instead to make it available in every
project.
