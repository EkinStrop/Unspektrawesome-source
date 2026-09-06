#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--var-name", required=True)
    args = parser.parse_args()

    data = Path(args.input).read_bytes()
    out = Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)

    with out.open("w", encoding="utf-8", newline="\n") as f:
        f.write("#pragma once\n")
        f.write("#include <cstddef>\n")
        f.write("#include <cstdint>\n\n")
        f.write(f"static const uint8_t {args.var_name}[] = {{\n")
        for i in range(0, len(data), 16):
            chunk = data[i:i + 16]
            f.write("    ")
            f.write(", ".join(f"0x{b:02x}" for b in chunk))
            f.write(",\n")
        f.write("};\n")
        f.write(f"static const size_t {args.var_name}_size = {len(data)};\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
