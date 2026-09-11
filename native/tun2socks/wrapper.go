package main

/*
#include <stdint.h>
#include <stdbool.h>
*/
import "C"

import (
	"fmt"
	"sync"

	"github.com/xjasonlyu/tun2socks/v2/engine"
)

var engineMu sync.Mutex
var engineRunning bool

//export Java_com_dstwr_flow_vpn_NativeTun2Socks_nativeStart
func Java_com_dstwr_flow_vpn_NativeTun2Socks_nativeStart(_ C.uintptr_t, _ C.uintptr_t, fd C.int) C.bool {
	engineMu.Lock()
	defer engineMu.Unlock()

	if engineRunning {
		return C.bool(true)
	}
	if fd < 0 {
		return C.bool(false)
	}

	engine.Insert(&engine.Key{
		MTU:                      1500,
		Device:                   fmt.Sprintf("fd://%d", int(fd)),
		Proxy:                    "direct://",
		LogLevel:                 "warning",
		UDPTimeout:               60 * 1000000000,
		TCPModerateReceiveBuffer: true,
		TCPSendBufferSize:        "4MB",
		TCPReceiveBufferSize:     "4MB",
	})

	engine.Start()
	engineRunning = true
	return C.bool(true)
}

//export Java_com_dstwr_flow_vpn_NativeTun2Socks_nativeStop
func Java_com_dstwr_flow_vpn_NativeTun2Socks_nativeStop(_ C.uintptr_t, _ C.uintptr_t) {
	engineMu.Lock()
	defer engineMu.Unlock()

	if !engineRunning {
		return
	}
	engine.Stop()
	engineRunning = false
}

func main() {}
