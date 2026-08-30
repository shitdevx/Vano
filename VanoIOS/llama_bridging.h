#ifndef LLAMA_BRIDGING_H
#define LLAMA_BRIDGING_H

#include "llama.h"

// Swift 5.0 / Xcode 15.4 cannot import forward-declared opaque C structs
// from bridging headers. Provide minimal definitions so Swift can see them.
// Only types that are forward-declared but NEVER fully defined in llama.h need this:
//   llama_model, llama_context, llama_vocab, llama_kv_cache, llama_adapter_lora
// Types with full definitions in llama.h (like llama_sampler, llama_batch) must NOT be redefined.
struct llama_model { char _opaque; };
struct llama_context { char _opaque; };
struct llama_vocab { char _opaque; };
struct llama_kv_cache { char _opaque; };
struct llama_adapter_lora { char _opaque; };

#endif
