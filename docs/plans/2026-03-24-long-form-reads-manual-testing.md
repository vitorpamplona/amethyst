---
title: "Long-Form Reads — Manual Testing Sheet"
date: 2026-03-24
branch: features/long-form-content
---

# Long-Form Reads — Manual Testing Sheet

**Run:** `cd AmethystMultiplatform-long-form && ./gradlew :desktopApp:run`

## Pre-Test Setup

- [x] App launches without crash
- [x] Login with existing account (needs relay connections)
- [x] Navigate to Reads tab in sidebar

---

## 1. ReadsScreen Feed

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| 1.1 | Feed loads articles | Click Reads in sidebar | Long-form article cards appear | |
| 1.2 | Reading time shown | Check article cards | "X min read" displayed on each card | |
| 1.3 | Global/Following toggle | Click Global/Following chips | Feed switches between modes | |
| 1.4 | Article click navigates | Click any article card | ArticleReaderScreen opens (not ThreadScreen) | |

---

## 2. ArticleReaderScreen

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| 2.1 | Article loads | Click article from Reads feed | Content renders with markdown formatting | |
| 2.2 | Title + metadata | Check header area | Title, author name, reading time, date displayed | |
| 2.3 | Banner image | Open article with banner | Hero image renders at top (if article has `image` tag) | |
| 2.4 | Markdown headings | Scroll through article | H1-H3 render with different sizes | |
| 2.5 | Bold/italic/code | Check formatting | **Bold**, *italic*, `inline code` render correctly | |
| 2.6 | Code blocks | Find code block | Monospace font, distinct background | |
| 2.7 | Links clickable | Click a URL link | Opens in system browser | |
| 2.8 | nostr: links | Click a nostr: link | Does NOT open OS error dialog (scheme filtered) | |
| 2.9 | Images in content | Find article with images | Images render via Coil | |
| 2.10 | Back button | Click ← Back | Returns to ReadsScreen | |
| 2.11 | Content width | Check article body | Max ~680dp centered column | |
| 2.12 | Loading state | Open article (watch transition) | "Loading article..." shown briefly | |
| 2.13 | Error state | Open invalid address tag (if testable) | "Article not found" message | |

---

## 3. Table of Contents

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| 3.1 | ToC visible | Open article on wide window (>1100dp) | ToC sidebar appears on left with heading list | |
| 3.2 | ToC hidden | Resize window to <900dp | ToC sidebar disappears | |
| 3.3 | Heading hierarchy | Check ToC entries | H2 indented less than H3 | |
| 3.4 | Click heading | Click a ToC entry | Active entry highlights (scroll-to is TODO) | |
| 3.5 | No headings | Open article with no markdown headings | ToC sidebar not shown or empty | |

---

## 4. Article Editor

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| 4.1 | Navigate to editor | Drafts tab → New Draft (or via menu) | Editor screen opens with split pane | |
| 4.2 | Split pane | Check layout | Source left, preview right | |
| 4.3 | Live preview | Type markdown in source pane | Preview updates after ~300ms | |
| 4.4 | Toolbar: Bold | Click B button | `**text**` inserted at cursor | |
| 4.5 | Toolbar: Italic | Click I button | `*text*` inserted | |
| 4.6 | Toolbar: Heading | Click H button | `## ` inserted | |
| 4.7 | Toolbar: Link | Click link button | `[text](url)` inserted | |
| 4.8 | Toolbar: Code | Click code button | Backticks inserted | |
| 4.9 | Toolbar: Quote | Click quote button | `> ` inserted | |
| 4.10 | Metadata: Title | Enter title | Title field accepts input, max 256 chars | |
| 4.11 | Metadata: Summary | Enter summary | Summary field accepts input, max 1024 chars | |
| 4.12 | Metadata: Tags | Type tag + Enter | Tag chip added | |
| 4.13 | Metadata: Slug | Enter slug | Auto-sanitized (no special chars) | |
| 4.14 | Ctrl+S save | Press Ctrl+S (or Cmd+S) | Draft saved to disk | |
| 4.15 | Back button | Click ← Back | Returns to previous screen | |
| 4.16 | Preview link safety | Add `[click](javascript:alert(1))` in source | Link NOT clickable in preview (scheme blocked) | |

---

## 5. Draft Storage

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| 5.1 | Draft saved | Create draft, save, check filesystem | `~/.amethyst/drafts/<slug>.md` exists | |
| 5.2 | Index file | Check filesystem | `~/.amethyst/drafts/index.json` exists with metadata | |
| 5.3 | Drafts screen | Navigate to Drafts | Lists saved drafts with title, date | |
| 5.4 | Resume editing | Click a draft in list | Editor opens with content restored | |
| 5.5 | Delete draft | Click delete on a draft | Confirmation dialog → draft removed | |
| 5.6 | Slug sanitization | Try slug with `../` or special chars | Slug sanitized to safe characters | |
| 5.7 | Directory permissions | `ls -la ~/.amethyst/drafts/` | Dir permissions 700 (Unix) | |

---

## 6. Publish

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| 6.1 | Publish button | Fill title + content, click Publish | Event signed and sent to relays | |
| 6.2 | Publish feedback | After publish | Success snackbar / confirmation | |
| 6.3 | Published in feed | After publish, check Reads feed | Your article appears in Global feed | |
| 6.4 | Re-publish (replace) | Edit same draft, publish again | Article updated (same d-tag) | |
| 6.5 | Size limit | Try publishing >100KB content | Error message about content too large | |

---

## 7. Typography (Visual)

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| 7.1 | Body text | Read article body | Georgia-style serif, ~18sp, generous line height | |
| 7.2 | Content centered | Check horizontal layout | Content centered with max ~680dp width | |
| 7.3 | Dark mode | Check dark theme | Text ~#E0E0E0 on dark background, comfortable contrast | |
| 7.4 | Blockquotes | Find blockquote | Left border/indent, slightly larger text | |
| 7.5 | Tables | Find table | Renders with columns and rows | |

---

## 8. Security Checks

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| 8.1 | XSS in markdown | Article with `<script>alert(1)</script>` | Rendered as literal text, no execution | |
| 8.2 | URI scheme: javascript | Link `[x](javascript:alert(1))` in article | Link not clickable / filtered | |
| 8.3 | URI scheme: file | Link `[x](file:///etc/passwd)` in article | Link not clickable / filtered | |
| 8.4 | Image URL validation | Article with `file:///etc/passwd` as banner | Image not loaded | |
| 8.5 | Slug traversal | Set slug to `../../.ssh/keys` | Sanitized to safe string | |

---

## 9. Edge Cases

| # | Test | Steps | Expected | Pass? |
|---|------|-------|----------|-------|
| 9.1 | Empty article | Article with no content | Reader shows empty body, no crash | |
| 9.2 | No relays connected | Disconnect all relays → open article | "Connecting to relays..." loading state | |
| 9.3 | Very long article | Article with 10k+ words | Renders without freeze (may be slow) | |
| 9.4 | No banner image | Article without `image` tag | Header renders without banner, no crash | |
| 9.5 | No author metadata | Article from unknown pubkey | Shows pubkey hex, no profile pic | |
| 9.6 | Window resize | Resize during article reading | Layout adapts, ToC shows/hides | |

---

## Notes

_Record any bugs, unexpected behavior, or UX issues here:_

|  | Issue | Severity | Notes |
|--|-------|----------|-------|
|  |       |          |       |
|  |       |          |       |
|  |       |          |       |
