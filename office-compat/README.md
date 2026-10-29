# Android HSSF compatibility

This module packages Apache POI 5.5.1 core, with upstream LICENSE/NOTICE files,
from the pinned Maven artifact. It replaces only `CleanerUtil` and its nested
interface. The original desktop implementation contains signature-polymorphic
MethodHandle calls that D8 rejects below API 26. The replacement advertises
that explicit memory-map unmapping is unavailable; buffers use Android's normal
GC lifecycle. The application opens POIFS from bounded in-memory streams and
does not use file mapping, desktop graphics, column autosizing, or image APIs.

All spreadsheet parsing, formats and encryption checks remain upstream HSSF.
No OOXML or scratchpad dependency is included. Core library desugaring supports
Java library calls on API 24. Test both debug and R8 builds when updating POI.
