package easyev

// EasyEvent mirrors Event for easyjson. It is a separate type on purpose: easyjson is
// generated with -no_std_marshalers, and keeping its methods off Event guarantees no
// other library silently dispatches into the easyjson code through json.Unmarshaler.
//
// It lives in its own package because easyjson cannot generate into package main.
// Regenerate (from this directory) with:
//
//	go run github.com/mailru/easyjson/easyjson@v0.9.2 -no_std_marshalers event.go
//
//easyjson:json
type EasyEvent struct {
	ID        string     `json:"id"`
	PubKey    string     `json:"pubkey"`
	CreatedAt int64      `json:"created_at"`
	Kind      int        `json:"kind"`
	Tags      [][]string `json:"tags"`
	Content   string     `json:"content"`
	Sig       string     `json:"sig"`
}
