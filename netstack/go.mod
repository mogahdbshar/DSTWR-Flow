module dev.netvalve/bridge

go 1.26.0

require github.com/sagernet/gvisor v0.0.0-20250811.0-sing-box-mod.1

tool (
	golang.org/x/mobile/cmd/gomobile
	golang.org/x/mobile/cmd/gobind
)

require (
	github.com/google/btree v1.1.2 // indirect
	golang.org/x/mobile v0.0.0-20260908204917-8b95e45f8d3e
	golang.org/x/sys v0.47.0 // indirect
	golang.org/x/time v0.7.0 // indirect
)
