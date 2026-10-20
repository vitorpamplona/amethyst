package main

import (
	"crypto/sha256"
	"encoding/hex"
	"hash"
	"strconv"
)

// Event is the typed result every `typed` parser produces: owned, fully decoded
// strings, integers as integers, tags as nested string slices.
type Event struct {
	ID        string     `json:"id"`
	PubKey    string     `json:"pubkey"`
	CreatedAt int64      `json:"created_at"`
	Kind      int        `json:"kind"`
	Tags      [][]string `json:"tags"`
	Content   string     `json:"content"`
	Sig       string     `json:"sig"`
}

// fold is what the timed loop sinks for a typed event (HARNESS.md: kind + tags + content length).
func (e *Event) fold() int64 {
	return int64(e.Kind) + int64(len(e.Tags)) + int64(len(e.Content))
}

const (
	us = 0x1F
	rs = 0x1E
	gs = 0x1D
	fs = 0x1C
)

// digester feeds events into the cross-language digest of HARNESS.md
// (reference: digest_event in corpus/prepare.py).
type digester struct {
	h   hash.Hash
	buf []byte
}

func newDigester() *digester { return &digester{h: sha256.New(), buf: make([]byte, 0, 64<<10)} }

func (d *digester) str(s string) {
	d.buf = append(d.buf, s...)
	d.buf = append(d.buf, us)
}

func (d *digester) add(e *Event) {
	d.str(e.ID)
	d.str(e.PubKey)
	d.buf = strconv.AppendInt(d.buf, e.CreatedAt, 10)
	d.buf = append(d.buf, us)
	d.buf = strconv.AppendInt(d.buf, int64(e.Kind), 10)
	d.buf = append(d.buf, us)
	for _, t := range e.Tags {
		for _, v := range t {
			d.str(v)
		}
		d.buf = append(d.buf, rs)
	}
	d.buf = append(d.buf, gs)
	d.str(e.Content)
	d.str(e.Sig)
	d.buf = append(d.buf, fs)
	if len(d.buf) > 32<<10 {
		d.h.Write(d.buf)
		d.buf = d.buf[:0]
	}
}

func (d *digester) hex() string {
	d.h.Write(d.buf)
	d.buf = d.buf[:0]
	return hex.EncodeToString(d.h.Sum(nil))
}
