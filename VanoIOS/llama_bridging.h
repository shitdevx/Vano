#ifndef LLAMA_BRIDGING_H
#define LLAMA_BRIDGING_H

#include "llama.h"

// Swift 5.0 / Xcode 15.4 cannot import forward-declared opaque C structs
// from bridging headers. Provide minimal definitions so Swift can see the types.
// These are only used as opaque pointers - no member access.
struct llama_model { char _opaque; };
struct llama_context { char _opaque; };
struct llama_sampler { char _opaque; };
struct llama_vocab { char _opaque; };
struct llama_kv_cache { char _opaque; };

#endif
