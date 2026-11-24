// relay29-runner: fiatjaf/relay29 (khatru29) on loopback, for amy's NIP-29 interop harness.
// Roles: "admin" (the creator's role; every action) and "moderator" (remove-user, delete-event).
// Plain members can do nothing moderative. Private groups allowed. Events must be within +-60 s.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"time"

	"github.com/fiatjaf/eventstore/slicestore"
	"github.com/fiatjaf/khatru/policies"
	"github.com/fiatjaf/relay29"
	"github.com/fiatjaf/relay29/khatru29"
	"github.com/nbd-wtf/go-nostr"
	"github.com/nbd-wtf/go-nostr/nip29"
)

var (
	adminRole     = &nip29.Role{Name: "admin", Description: "the group's max top admin"}
	moderatorRole = &nip29.Role{Name: "moderator", Description: "the person who cleans up unwanted stuff"}
)

func main() {
	port := flag.Int("port", 2929, "listen port (127.0.0.1)")
	sk := flag.String("sk", "", "relay secret key hex (default: random)")
	flag.Parse()
	if *sk == "" {
		*sk = nostr.GeneratePrivateKey()
	}
	db := &slicestore.SliceStore{}
	db.Init()
	domain := fmt.Sprintf("127.0.0.1:%d", *port)
	relay, state := khatru29.Init(relay29.Options{
		Domain:                  domain,
		DB:                      db,
		SecretKey:               *sk,
		DefaultRoles:            []*nip29.Role{adminRole, moderatorRole},
		GroupCreatorDefaultRole: adminRole,
	})
	relay.ServiceURL = "ws://" + domain
	state.AllowAction = func(ctx context.Context, group nip29.Group, role *nip29.Role, action relay29.Action) bool {
		if role == adminRole {
			return true
		}
		if role == moderatorRole {
			switch action.(type) {
			case relay29.RemoveUser, relay29.DeleteEvent:
				return true
			}
		}
		return false
	}
	relay.Info.Name = "relay29 interop"
	relay.Info.Description = "fiatjaf/relay29 on loopback for amy's NIP-29 harness"
	relay.RejectEvent = append(relay.RejectEvent,
		policies.PreventTimestampsInThePast(60*time.Second),
		policies.PreventTimestampsInTheFuture(30*time.Second),
	)
	pk, _ := nostr.GetPublicKey(*sk)
	log.Printf("relay29 pubkey %s", pk)
	// Bind first, then say so: the harness waits for "listening", and a port another process still
	// holds must fail here rather than leave the harness talking to that one.
	ln, err := net.Listen("tcp", domain)
	if err != nil {
		log.Fatal(err)
	}
	log.Printf("listening on ws://%s (pid %d)", domain, os.Getpid())
	if err := http.Serve(ln, relay); err != nil {
		log.Fatal(err)
	}
}
